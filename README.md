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
1、数据库使用mysql，启动mysql服务之后，在项目目录下执行命令即可拷贝数据：
```
mysql -u root -p < class10_dump.sql
```

2、外部库至少需要满足：java17：graalvm-ce-17能够提供的功能

3、依赖构建工具Maven

# 开发者事项

对项目进行修改之后，务必做好审查并在update.md中按照原有格式添加修改