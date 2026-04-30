# PPT Backend

Spring Boot backend for the PPT AI generator.

## 运行

1. 安装 Java 21
2. 进入 `backend` 目录
3. 运行：

```powershell
mvn spring-boot:run
```

4. 访问健康检查：

- `http://localhost:8080/api/health`
- `http://localhost:8080/actuator/health`

## 配置

默认配置文件在 `src/main/resources/application.yml`。

`application-local.yml` 用于本地开发，`application-prod.yml` 用于生产环境。

使用 Spring profile 切换：

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```
