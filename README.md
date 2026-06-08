# 如何运行
终端执行

.\mvnw.cmd spring-boot:run
    
启动服务成功后，打开浏览器输入网址访问：

http://localhost:8080


## 数据
会通过[UserDataInitializer.java](src/main/java/cn/edu/cuc/class10/service/UserDataInitializer.java)
和[VocabularyInitializer.java](src/main/java/cn/edu/cuc/class10/service/VocabularyInitializer.java)
生成模拟用户和真实考纲词汇

## 端口被占用如何解决
netstat -ano | findstr :8080
用于找到占用端口

taskkill /F /PID xxx
将找到的端口写到xxx的位置，把它关掉

# 他人机器使用注意事项
## 方法一：配置完整环境（复杂但稳定）
1、数据库使用mysql，启动mysql服务之后，在项目目录下执行命令即可拷贝数据：
```
mysql -u root -p < class10_dump.sql
```

2、外部库至少需要满足：java17：graalvm-ce-17能够提供的功能

3、依赖构建工具Maven

## 方法二：接收WAR包
1、依赖mysql和JDK17

```
java -jar class10.war
```
即可启动服务，在浏览器访问

# 开发者事项

对项目进行修改之后，务必做好审查并在update.md中按照原有格式添加修改

# 照片识词模块（OCR）

项目新增了照片识词功能，依赖于 ImageNet-21k 预训练模型文件（~392 MB）。

## 模型文件获取方式（二选一）

### 方式 A：运行导出脚本（推荐）
```bash
pip install torch timm
python export_in21k_model.py
```
脚本会自动下载权重并导出到 `ocr-models/vit_base_in21k.pt`。

### 方式 B：从共享链接下载
联系项目维护者获取模型文件，放到：
```
ocr-models/vit_base_in21k.pt
```

## .gitignore 说明

以下目录**不会**提交到 GitHub，新增 clone 项目的成员需自行准备：

| 目录 | 内容 | 原因 |
|------|------|------|
| `ocr-models/` | OCR 模型文件 (~392 MB) | 超 GitHub 100 MB 限制 |
| `word-images/` | 单词配图缓存 (~900 MB) | 用户数据，无需版本控制 |

