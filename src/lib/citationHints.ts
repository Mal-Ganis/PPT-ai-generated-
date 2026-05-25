import type { SlideData } from '../App';
import { isStructuralSlideData } from './structuralSlide';

/** 与后端 SlideSourceCitationService 兜底文案、Prompt 占位保持一致 */
const PLACEHOLDER_PATTERNS = [
  /type=llm_inference/i,
  /已过滤不可验证链接/,
  /常识归纳需人工核对/,
  /未命中可核验的外部链接/,
  /内部降级输出/,
  /请在大纲阶段加载外部检索/,
  /手动补充出处/,
];

const PENDING_VERIFICATION = /\[待核实\]|【待核实】|\[待补充权威来源\]/;

export function isPlaceholderSourceLine(line: string): boolean {
  const t = line.trim();
  if (!t) return true;
  return PLACEHOLDER_PATTERNS.some((p) => p.test(t));
}

export function countPendingVerificationInContent(content: string[]): number {
  return content.filter((line) => PENDING_VERIFICATION.test(line)).length;
}

/** 正文页：是否需提示/补充引用（封面、目录、Q&A 等骨架页始终为 false） */
export function slideDataNeedsCitationAttention(slide: SlideData): boolean {
  if (isStructuralSlideData(slide)) {
    return false;
  }
  return slideNeedsCitationAttention(slide.content, slide.sources);
}

export function indicesNeedingCitationAttention(slides: SlideData[]): number[] {
  const out: number[] = [];
  slides.forEach((slide, index) => {
    if (slideDataNeedsCitationAttention(slide)) {
      out.push(index);
    }
  });
  return out;
}

export function slideNeedsCitationAttention(content: string[], sources?: string[]): boolean {
  const pending = countPendingVerificationInContent(content);
  if (pending > 0) return true;
  const list = sources ?? [];
  if (list.length === 0) return true;
  if (list.every(isPlaceholderSourceLine)) return true;
  const hasReal = list.some(
    (s) => !isPlaceholderSourceLine(s) && (/https?:\/\//i.test(s) || /type=(tavily|mediawiki|index)/i.test(s)),
  );
  return !hasReal && list.length > 0;
}

export function citationAttentionSummary(content: string[], sources?: string[]): string | null {
  const pending = countPendingVerificationInContent(content);
  const list = sources ?? [];
  const placeholdersOnly = list.length > 0 && list.every(isPlaceholderSourceLine);

  if (pending > 0 && (list.length === 0 || placeholdersOnly)) {
    return `本页有 ${pending} 条要点标注了「待核实」，且尚无有效引用链接，请在下方补充出处（每行一条）。`;
  }
  if (pending > 0) {
    return `本页有 ${pending} 条要点含「待核实」，请核对下方引用是否与这些要点对应。`;
  }
  if (list.length === 0) {
    return '本页尚未填写引用来源，建议每行一条链接或文献说明（可来自知识检索或手动粘贴）。';
  }
  if (placeholdersOnly) {
    return '当前引用为系统占位说明，请改为真实链接或文献条目（每行一条）。';
  }
  return null;
}

export function sourcesToEditableText(sources: string[]): string {
  return (sources ?? []).filter((s) => s?.trim()).join('\n');
}

export function editableTextToSources(text: string): string[] {
  return text
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean);
}
