户子训练模型

说在前面，跑出来的效果和本人说话风格差距还是很有点大，另外不要转Q4\_K\_M量化转了后感觉在乱说！！！

文件结构

etl: 清洗https://github.com/Olcmyk/HuChenFeng数据，目前已经把所有.md放到resources下

1) 对接Deepseek清洗对话，参考【HCFMultiThreadDistiller.java>main】

2) 完善json补充,和\[\]，并校验参考【HCFMultiThreadDistiller.java>checkJson()】

3) 清洗脚本中错误数据，参考【HCFDataValidator.java>isValidShareGPT】

x） 已经处理好的文件在llamafactory-0.9.4/data/hcf\_v2\_sharegpt.json，由于金额问题抽取了2023、2024、2025部分数据进行清洗

llamafactory-0.9.4 对模型进行微调

1) 安装环境

conda config --set channel\_priority flexible

conda create -n llama\_factory python=3.11 -c conda-forge

conda activate llama\_factory

2) 进入llamafactory-0.9.4目录下安装依赖

pip install -e ".\[torch,metrics,bitsandbytes,modelscope\]"

pip uninstall torch torchvision torchaudio -y

pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu121

3) 设置环境变量

$env:HF\_ENDPOINT="https://hf-mirror.com"

$env:USE\_MODELSCOPE\_HUB="1"

$env:PYTORCH\_CUDA\_ALLOC\_CONF="expandable\_segments:True"

4) 检查是否GPU版本，返回"True"为是

python -c "import torch; print(torch.cuda.is\_available())"

5）开始训练

llamafactory-cli train train\_a100.yaml

6) 导出模型

llamafactory-cli export export.yaml

7) 训练结束后访问web-ui页面进入Chat窗口，模型选择Custom,路径输入【根路径到/llamafactory-0.9.4/saves/HCF\_v2\_LongMemory】 输入提示词进行测试

llamafactory-cli chat chat\_hcf.yaml

提示词我用的: 你现在是户晨风，一个说话犀利、逻辑严密、直击痛点的人。你回答问题从不客套，不使用'首先、其次'这种刻板句式。请用简练、接地气的口语化风格直接回答问题。

x) 已经训练好的模型在llamafactory-0.9.4/saves/HCF\_v2\_LongMemory下