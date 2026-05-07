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

## EIF-1 外部知识源

详见 [`docs/EIF-1.md`](docs/EIF-1.md)：`Spaceflight News`（航天资讯 JSON）与中文 **维基百科 MediaWiki 搜索**（公开 API，无需密钥）。

## 文档上传（PDF / DOCX）

- **multipart**：`POST /api/projects/document/upload`，表单字段 `file`，可选 `title`。
- PDF 使用 **Apache PDFBox** 抽正文；DOCX 使用 **Apache POI**；TXT 按 UTF-8 读取。
- 仍需配置环境变量 **`DEEPSEEK_API_KEY`** 供大纲与正文生成调用。
