package org.example;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class HCFMultiThreadDistiller {
    private static final String API_KEY = "API_KEY";
    private static final String API_URL = "https://tb.api.mkeai.com/v1/chat/completions";
    private static final String inputDirPath = "md文件路径";
    // 线程数量控制
    private static final int THREAD_COUNT = 3;
    // 文件写入锁，保证多线程追加时不串行
    private static final Object FILE_LOCK = new Object();

    private static final AtomicInteger successCount = new AtomicInteger(0);

    public static void checkJson() {
        JSONUtil.parseArray(FileUtil.readString(inputDirPath, "utf-8"));
    }

    /**
     * 运行完main后人工检查一次(删除不合理的对话，修正识别错误的文字),并且在每行末尾添加【,】(最后一行不用加),然后开始和结尾加一个[],在调用checkJson()方法检查json完整(如果有报错说明json有问题需要人工修改),最后在调用HCFDataValidator.java进行清洗
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        File outputJsonl = new File("hcf_v2_progress.jsonl");
        File folder = new File(inputDirPath);
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".md"));
        if (files == null || files.length == 0) {
            System.out.println("没有找到 Markdown 文件。");
            return;
        }

        System.out.println("🚀 启动 3 线程并发提炼...");
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        for (File file : files) {
            executor.submit(() -> processFile(file, outputJsonl));
        }

        // 关闭线程池并等待所有任务完成
        executor.shutdown();
        executor.awaitTermination(24, TimeUnit.HOURS);

        System.out.println("✅ 所有文件提炼完毕！共成功生成 " + successCount.get() + " 组 QA。");
    }




    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
            .configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // 增加连接池配置，适应多线程并发请求
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();


    private static void processFile(File file, File outputJsonl) {
        String threadName = Thread.currentThread().getName();
        System.out.println("[" + threadName + "] 开始处理: " + file.getName());

        try {
            String content = Files.readString(file.toPath());
            // 核心防御：切片大小降到 800，防止输出超长
            List<String> chunks = splitContent(content, 800);

            for (int i = 0; i < chunks.size(); i++) {
                try {
                    String rawResponse = callDeepSeek(chunks.get(i));
                    String cleanJson = sanitizeJson(rawResponse);

                    JsonNode node = mapper.readTree(cleanJson);
                    if (node.has("data")) {
                        // 加锁写入文件，确保线程安全
                        synchronized (FILE_LOCK) {
                            for (JsonNode qa : node.get("data")) {
                                Files.writeString(outputJsonl.toPath(), mapper.writeValueAsString(qa) + "\n",
                                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                            }
                        }
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.err.println("[" + threadName + "] 片段解析跳过: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[" + threadName + "] 读取文件失败: " + file.getName());
        }
    }

    private static String sanitizeJson(String raw) {
        if (raw == null) return "{}";
        raw = raw.replaceAll("```json", "").replaceAll("```", "").trim();
        int start = raw.indexOf("{");
        int end = raw.lastIndexOf("}");
        if (start != -1 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }

    private static String callDeepSeek(String text) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", "deepseek-chat");
        // 放开最大 Token 限制，允许模型输出完整的长篇大论
        root.put("max_tokens", 8192);
        root.put("temperature", 0.2);

        ArrayNode messages = root.putArray("messages");
        String systemPrompt = "你是一个顶级数据工程专家。请将直播稿提炼为户晨风的人格微调数据为 ShareGPT 格式的 JSON。规则：" +
                "1. 必须保留户子犀利、直接、反问的语气。" +
                "2. 核心观点如'合法纳税'、'苹果人生'、'逻辑自洽'、'中国大多家庭年夜饭味道不行'、'中医不科学'、'大学教育问题'、'不支持文言文'、'支持断亲'、'中餐不健康'、'苹果手机好'、'英语很重要'、'油车选丰田'、'私有化好'、'要依法纳税'、'喝粥不健康'、'支持乌克兰以色列'、'喜欢喝冰水'、'电车选特斯拉' 必须绝对保留。" +
                "3. 删掉所有直播干扰词。" +
                "4. 严禁出现任何直播间干扰词（喂、连麦、下播、感谢我CX、点点赞、左上角关注、不急不急、读一下SC、突然警觉）。" +
                "5. 红线意识：尽量谈消费不谈政治，谈个人不谈体制。" +
                "6. 输出格式：输出格式必须严格如下：{\"data\": [{\"conversations\": [{\"from\": \"human\", \"value\": \"...\"}, {\"from\": \"gpt\", \"value\": \"...\"}]}]}";

        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", "提炼以下内容: " + text);

        ObjectNode responseFormat = mapper.createObjectNode();
        responseFormat.put("type", "json_object");
        root.set("response_format", responseFormat);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(root)))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(response.body()).get("choices").get(0).get("message").get("content").asText();
    }

    private static List<String> splitContent(String content, int size) {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < content.length(); i += size) {
            chunks.add(content.substring(i, Math.min(content.length(), i + size)));
        }
        return chunks;
    }
}
