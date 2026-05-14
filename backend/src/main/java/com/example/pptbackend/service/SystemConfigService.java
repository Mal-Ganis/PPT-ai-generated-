package com.example.pptbackend.service;

import com.example.pptbackend.dto.SystemConfigDto;
import com.example.pptbackend.model.SystemConfig;
import com.example.pptbackend.repository.SystemConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;

    public SystemConfigService(SystemConfigRepository systemConfigRepository) {
        this.systemConfigRepository = systemConfigRepository;
    }

    @Transactional
    public SystemConfigDto getSystemConfig() {
        SystemConfig config = systemConfigRepository.findTopByOrderByIdAsc()
            .orElseGet(this::createDefaultConfig);
        return toDto(config);
    }

    @Transactional
    public SystemConfigDto saveSystemConfig(SystemConfigDto dto) {
        SystemConfig config = systemConfigRepository.findTopByOrderByIdAsc().orElse(new SystemConfig());
        config.setLlmModel(dto.getLlmModel());
        config.setTemperature(dto.getTemperature());
        config.setMaxTokens(dto.getMaxTokens());
        config.setTopP(dto.getTopP());
        config.setTopK(dto.getTopK());
        config.setRetrievalLimit(dto.getRetrievalLimit());
        config.setOutlinePromptTemplate(dto.getOutlinePromptTemplate());
        config.setSlidePromptTemplate(dto.getSlidePromptTemplate());

        return toDto(systemConfigRepository.save(config));
    }

    private SystemConfigDto toDto(SystemConfig config) {
        SystemConfigDto dto = new SystemConfigDto();
        dto.setId(config.getId());
        dto.setLlmModel(config.getLlmModel());
        dto.setTemperature(config.getTemperature());
        dto.setMaxTokens(config.getMaxTokens());
        dto.setTopP(config.getTopP());
        dto.setTopK(config.getTopK());
        dto.setRetrievalLimit(config.getRetrievalLimit());
        dto.setOutlinePromptTemplate(config.getOutlinePromptTemplate());
        dto.setSlidePromptTemplate(config.getSlidePromptTemplate());
        return dto;
    }

    /**
     * 将当前库中的配置行重置为与 {@link #createDefaultConfig()} 相同的内置默认值（含新版大纲模板）。
     */
    @Transactional
    public SystemConfigDto resetToBuiltInDefaults() {
        SystemConfig config = systemConfigRepository.findTopByOrderByIdAsc().orElse(new SystemConfig());
        applyBuiltInDefaults(config);
        return toDto(systemConfigRepository.save(config));
    }

    private SystemConfig createDefaultConfig() {
        SystemConfig config = new SystemConfig();
        applyBuiltInDefaults(config);
        return systemConfigRepository.save(config);
    }

    private static void applyBuiltInDefaults(SystemConfig config) {
        // deepseek-reasoner 常先输出长推理，易导致大纲阶段无法解析 JSON；结构化大纲优先用 chat
        config.setLlmModel("deepseek-chat");
        config.setTemperature(0.7);
        config.setMaxTokens(1024);
        config.setTopP(0.95);
        config.setTopK(1);
        config.setRetrievalLimit(3);
        config.setOutlinePromptTemplate("""
你是一位专业的商业叙事顾问。请基于下列输入生成逻辑严谨、数据驱动的 PPT 大纲。**大纲只负责「叙事骨架」**：章节名、页标题、每条要点写什么维度；不写幻灯片正文血肉细节——血肉留给下一阶段模板生成。

## 主题与检索资料
【主题 / 上传节选 / 系统注入的叙事主料】
{content}

【检索上下文（独立块；若为空说明系统未拆出标题段，仍须通读上一栏全文）】
{retrieved_context}

## 输出格式（严格 JSON）
你必须只输出**一个**合法 JSON 对象：从第一个字符 `{` 开始，到最后一个字符 `}` 结束；**禁止** Markdown 代码围栏、禁止前言/后记、禁止输出思考过程。
优先使用 chapters，以便「章节名 ↔ 正文页」形成闭环；也可用扁平 slides 或 pages 拆页。

### chapters（推荐）
{
  "title": "具体且有信息量的演示标题（≤15 字，避免「简介」「概述」类空话）",
  "chapters": [
    {
      "chapter": "章节名（体现叙事推进，可含数字锚点，如「问题：医疗资源错配率超40%」）",
      "slides": [
        {
          "title": "页标题（结论先行，尽量含数字或对比）",
          "content": [
            "要点须为完整陈述句：结论 + 数据/案例 + 对听众的意义；每条约 20–45 字",
            "禁止纯定义句；须说明在本主题场景下的价值或后果",
            "每页至少 3 条；复杂页可到 5 条"
          ],
          "notes": ""
        }
      ]
    }
  ]
}

### pages 拆页（备选）
{
  "title": "演示标题",
  "slides": [
    {
      "chapter": "背景",
      "pages": [
        {"title": "子页标题", "content": ["完整陈述句要点…", "…", "…"], "notes": ""},
        {"title": "子页标题", "content": ["…", "…", "…"], "notes": ""}
      ]
    }
  ]
}

## 叙事结构强制要求
0. **封面与目录（强制）**：使用 chapters 或扁平 slides 时，**全稿顺序的第一条必须是标题含「封面」的页**，**第二条必须是标题含「目录」的页**；目录页 content 用 4–10 条短语列出后续各主要页面/章节标题；从第三条目起再进入背景、问题、分析等正文叙事。
1. 整体弧线：钩子/冲突 → 背景数据 → 核心分析（2–3 章，递进而非并列堆砌）→ 案例或量化证据 → 风险/边界 → 结论与行动呼吁 →（按需）Q&A。
2. 章节之间须有因果关系或递进关系；禁止简单并列「优势 1、2、3」式堆叠。
3. 每章的首页 title 应体现该章「叙事功能」（例如「为何必须现在行动？」）。
4. 页数：简单主题 ≥6 页；复杂主题 10–14 页。

## 内容质量红线（必须遵守）
- 【数据锚点】至少约 60% 的 content 要点须包含具体数字、年份、机构名或产品名；若无检索依据，用可理解的常识数字亦须标明不确定性并在该要点末标 [待补充权威来源]。
- 【禁止重复】同一公司/产品/大学/实验案例在全稿中**不得**在多页重复展开；同一统计口径全文最多出现 2 次且须递进（第二次只作对比或收束，不复述故事）。
- 【禁止空洞】禁止「随着 XX 的发展」「众所周知」「XX 是指……」等无信息增量表述。
- 【禁止许愿式预测】禁止单独使用「有望达到」「或将突破」等模糊预测，除非给出年份区间与依据线索。
- 【要点粒度】content[] 中每条应是「一句完整中文」，禁止仅写「要点一」类占位词。

## 示例（禁止 content 留空、禁止示例级空话）
{
  "title": "AI 医疗：从辅助诊断到精准治疗的跃迁",
  "chapters": [
    {
      "chapter": "第一章 医疗系统的效率黑洞",
      "slides": [
        {
          "title": "误诊与漏诊年损耗超 3000 亿",
          "content": [
            "中国三甲医院年均误诊率约 27%，其中影像类误诊占比约 41%",
            "重复检查导致的资源浪费约占医保支出的 15%，近年估算金额超三千亿元量级（须在正文阶段核对权威口径）",
            "医生日均阅片 80–120 张，疲劳阈值后误诊率可显著上升"
          ],
          "notes": ""
        }
      ]
    }
  ]
}""");
        config.setSlidePromptTemplate("""
你是一位资深演示文稿撰稿人。本阶段负责「血肉填充」：在叙事骨架（章节名、页标题）已定的前提下，写出可上台宣读的高密度要点。**须与大纲共用章节名与数字锚点**；页与页之间用要点内的承接词体现递进，避免与前面各页摘要重复同一案例长段展开。

## 上下文信息（保持叙事连贯）
幻灯片标题：{slideTitle}
所属章节：{chapter}
前一页标题：{prevSlideTitle}（首页填「无」）
后一页标题：{nextSlideTitle}（尾页填「无」）
原始输入类型：{inputType}
原始输入内容：{inputContent}

## 检索上下文（后端注入：向量库 / Tavily / 上传文档切片；须优先采信）
{retrieved_context}

## 输出格式（严格 JSON；只含 content 与 sources，不要输出 notes）
{
  "content": [
    "每条为一句完整中文：先结论，再给可核查的数据/机构/年份或案例，再写对听众的影响；禁止只下定义",
    "同上；若本页与「前面各页摘要」可能撞车，须改换角度或新证据，禁止复述同一故事",
    "同上",
    "（可选）边界条件、反例或风险",
    "（可选）行动建议"
  ],
  "sources": [
    "字符串或对象均可；url 必须来自上方检索块中已出现的真实链接；若无链接可写「常识归纳 | type=llm_inference」",
    "{\"title\":\"来源标题\",\"url\":\"https://…\",\"type\":\"tavily\"}"
  ]
}

## 内容生成强制规范
1. **三层结构**：每条 content 尽量包含「观点 → 证据 → 影响」，缺一不可（检索为空时证据写常识并标 [待核实]）。
2. **数据密度**：全页至少 2 条要点须含具体数字、百分比、年份或机构名；检索上下文中有数字时优先引用检索。
3. **页间衔接**：若 prevSlideTitle 非「无」，首条 content 宜有一句轻量承接上一页结论；若 nextSlideTitle 非「无」，末条 content 可自然指向下一页主题（不必单独写演讲稿）。
4. **来源追溯**：至少 1 条 sources；**禁止** example.com、localhost、明显占位路径（如 article/123456）；**禁止**杜撰检索中未出现的 URL；不确定则写「常识推断 | llm_inference」。
5. **禁止项**：禁止「前景广阔」「具有重要意义」等无证据断言；禁止与「前面各页摘要」中已详述的案例再写长段；禁止编造链接。

## 自检（生成前想一想）
- content 合计字数是否明显高于一句口号（建议 ≥150 字）？
- 要点条数是否在 3–5 条？
- sources 是否均可解释出处？""");
    }
}
