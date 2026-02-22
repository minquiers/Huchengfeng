package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.File;
import java.io.IOException;

/**
 * json清洗
 */
public class HCFDataValidator {
    private static final ObjectMapper mapper = new ObjectMapper();
    static File inputFile = new File("原始jsonl文件");
    static File outputFile = new File("hcf_v2_sharegpt_clean.json");

    /**
     * 清洗后删除源文件，把清洗后文件重命名为源文件
     * @param args
     */
    public static void main(String[] args) {


        try {
            // 1. 加载原始数据
            JsonNode rootNode = mapper.readTree(inputFile);
            if (!rootNode.isArray()) {
                System.err.println("错误：根节点不是 JSON 数组！");
                return;
            }

            ArrayNode rawArray = (ArrayNode) rootNode;
            ArrayNode cleanArray = mapper.createArrayNode();
            int errorCount = 0;

            System.out.println("开始校验 " + rawArray.size() + " 条数据...");

            for (JsonNode entry : rawArray) {
                if (isValidShareGPT(entry)) {
                    cleanArray.add(entry);
                } else {
                    errorCount++;
                    // 打印前几个错误样板，方便你定位是哪个 MD 文件出的问题
                    if (errorCount <= 5) {
                        System.out.println("剔除无效数据样板: " + entry.toString());
                    }
                }
            }

            // 2. 持久化清洗后的数据
            mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, cleanArray);

            System.out.println("\n--- 清洗报告 ---");
            System.out.println("原始条数: " + rawArray.size());
            System.out.println("成功保留: " + cleanArray.size());
            System.out.println("剔除脏数据: " + errorCount);
            System.out.println("清洗后的文件已就绪: " + outputFile.getAbsolutePath());

        } catch (IOException e) {
            System.err.println("文件读取/写入失败: " + e.getMessage());
        }
    }

    /**
     * 严格校验 ShareGPT 结构
     */
    private static boolean isValidShareGPT(JsonNode node) {
        // 1. 必须是一个对象
        if (!node.isObject()) return false;

        // 2. 必须包含 conversations 键
        if (!node.has("conversations")) return false;

        JsonNode convs = node.get("conversations");

        // 3. conversations 必须是数组且不能为空
        if (!convs.isArray() || convs.isEmpty()) return false;

        // 4. 遍历对话内容，必须全是对象且包含 from 和 value
        for (JsonNode msg : convs) {
            if (!msg.isObject()) return false;
            if (!msg.has("from") || !msg.has("value")) return false;

            // 额外逻辑：value 不能为空字符串（模型学不到东西）
            if (msg.get("value").asText().trim().isEmpty()) return false;
        }

        return true;
    }
}
