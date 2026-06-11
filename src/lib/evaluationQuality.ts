import type { EvaluationReport } from '@/lib/backend';
import type { SlideData } from '../App';
import { isStructuralSlideData } from '@/lib/structuralSlide';

export interface AutoDimensionBar {
  label: string;
  score: number;
  max?: number;
  /** 自动总分权重，如 0.35 */
  weight?: number;
}

export interface WeakSlideIssue {
  index: number;
  slide: SlideData;
  reasons: string[];
}

/** 与 AutoEvaluationScoringService 计权一致 */
const AUTO_DIMENSION_WEIGHTS: Record<string, number> = {
  '结构 / 逻辑': 0.35,
  '信息密度': 0.25,
  '语言连贯': 0.2,
  '引用覆盖': 0.2,
};

export function formatAutoWeight(weight?: number): string {
  if (weight == null) return '';
  return `${Math.round(weight * 100)}%`;
}

/** 质量门禁阈值（与 backend evaluation.quality-gate-* 一致） */
export const QUALITY_GATE_PASS_MIN = 70;
export const QUALITY_GATE_WARN_MIN = 50;

export const QUALITY_GATE_RULES_SUMMARY =
  `${QUALITY_GATE_PASS_MIN} 分以上通过 · ${QUALITY_GATE_WARN_MIN}–${QUALITY_GATE_PASS_MIN} 分预警 · ${QUALITY_GATE_WARN_MIN} 分以下未通过`;

export function formatAutoTotalScore(autoTotalScore?: number | null): string {
  if (autoTotalScore == null || !Number.isFinite(autoTotalScore)) return '—';
  return `${autoTotalScore.toFixed(1)} 分`;
}

export function getAutoDimensionBars(report: EvaluationReport): AutoDimensionBar[] {
  const bars: AutoDimensionBar[] = [];
  if (report.autoOutlineLogicScore != null) {
    bars.push({
      label: '结构 / 逻辑',
      score: report.autoOutlineLogicScore,
      weight: AUTO_DIMENSION_WEIGHTS['结构 / 逻辑'],
    });
  }
  if (report.autoInfoDensityScore != null) {
    bars.push({
      label: '信息密度',
      score: report.autoInfoDensityScore,
      weight: AUTO_DIMENSION_WEIGHTS['信息密度'],
    });
  }
  if (report.autoLanguageExpressionScore != null) {
    bars.push({
      label: '语言连贯',
      score: report.autoLanguageExpressionScore,
      weight: AUTO_DIMENSION_WEIGHTS['语言连贯'],
    });
  }
  if (report.autoSourceCoverageScore != null) {
    bars.push({
      label: '引用覆盖',
      score: report.autoSourceCoverageScore,
      weight: AUTO_DIMENSION_WEIGHTS['引用覆盖'],
    });
  }
  return bars;
}

export function hasAutoEvaluation(report: EvaluationReport): boolean {
  return report.autoTotalScore != null || report.autoOutlineLogicScore != null;
}

/** 与后端 needsWeakSlideRegeneration 对齐 */
export function getWeakSlideIssues(slides: SlideData[]): WeakSlideIssue[] {
  const issues: WeakSlideIssue[] = [];
  slides.forEach((slide, index) => {
    if (isStructuralSlideData(slide)) return;
    const reasons: string[] = [];
    const bulletCount = slide.content?.filter((b) => b?.trim()).length ?? 0;
    if (bulletCount < 3) {
      reasons.push(`要点仅 ${bulletCount} 条（建议 ≥3）`);
    }
    if (!slide.sources?.some((s) => s?.trim())) {
      reasons.push('缺少引用来源');
    }
    if (reasons.length > 0) {
      issues.push({ index, slide, reasons });
    }
  });
  return issues;
}

export function buildEvaluationCompleteToast(report: EvaluationReport): string {
  const score = formatAutoTotalScore(report.autoTotalScore);
  return `正文生成完成 · 自动评分 ${score}。建议查看评估报告与薄弱页。`;
}

export function mapAutoScoresToManualForm(report: EvaluationReport): {
  outlineLogicScore: number;
  infoDensityScore: number;
  languageExpressionScore: number;
} {
  return {
    outlineLogicScore: Math.round(report.autoOutlineLogicScore ?? report.outlineLogicScore ?? 75),
    infoDensityScore: Math.round(report.autoInfoDensityScore ?? report.infoDensityScore ?? 75),
    languageExpressionScore: Math.round(
      report.autoLanguageExpressionScore ?? report.languageExpressionScore ?? 75,
    ),
  };
}
