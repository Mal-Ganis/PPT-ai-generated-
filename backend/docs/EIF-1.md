# EIF-1 外部权威知识源说明

## 已实现的数据源（无需 API Key）

| 名称 | 用途 | 入口代码 |
|------|------|-----------|
| **Spaceflight News API** | 航天相关英文资讯（列表 JSON） | `ExternalKnowledgeSourceService` 常量 `SPACE_FLIGHT_NEWS` |
| **维基百科 MediaWiki API** | 中文条目搜索（开放 API） | 同上，`WIKIPEDIA_API` |

HTTP 客户端：`java.net.http.HttpClient`（见 `ExternalKnowledgeSourceService`）。

## 环境变量

当前 EIF-1 **不要求** SerpAPI/Bing 等密钥。以下可选扩展时可自行增加：

| 变量 | 用途 |
|------|------|
| （预留）`SERPER_API_KEY` / `SERPAPI_API_KEY` | 若将来接入实时网页搜索，可在 `ExternalKnowledgeSourceService` 中增加分支 |

## 未接入的内容

- **付费实时搜索引擎**（SerpAPI、Bing Web Search、Google Custom Search）：未配置密钥前不会调用。
- 检索结果统一映射为 DTO `ExternalSourceDocument`，再写入 ILF-2 向量索引。
