import { Sparkles } from 'lucide-react';
import { QualityGateBadge } from '@/components/QualityGateBadge';
import { AutoDimensionBarsList } from '@/components/AutoDimensionBarsList';
import type { EvaluationReport } from '@/lib/backend';
import { formatAutoTotalScore, getAutoDimensionBars, hasAutoEvaluation, QUALITY_GATE_RULES_SUMMARY } from '@/lib/evaluationQuality';

interface AutoEvaluationPanelProps {
  report: EvaluationReport;
  /** 报告标题区已展示门禁时可关闭，避免重复 */
  showQualityGate?: boolean;
}

export function AutoEvaluationPanel({ report, showQualityGate = true }: AutoEvaluationPanelProps) {
  if (!hasAutoEvaluation(report)) {
    return (
      <div className="rounded-2xl border border-dashed border-gray-200 bg-[#fafafa] p-5">
        <p className="text-sm text-[#1f1f1f]/50">暂无自动评估数据（旧报告或未写入）。</p>
      </div>
    );
  }

  const bars = getAutoDimensionBars(report);
  const scoreLabel = formatAutoTotalScore(report.autoTotalScore);

  return (
    <div className="rounded-2xl border border-emerald-200/80 bg-gradient-to-br from-[#ecfdf5] via-white to-[#f0fdf9] p-5">
      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4 mb-5">
        <div className="flex items-start gap-3 min-w-0">
          <div className="w-10 h-10 rounded-xl bg-emerald-500/15 flex items-center justify-center shrink-0">
            <Sparkles className="w-5 h-5 text-emerald-600" />
          </div>
          <div>
            <p className="text-sm font-semibold text-emerald-900">自动评估</p>
            <p className="text-xs text-emerald-800/55 mt-0.5">启发式分项 + 加权总分（百分制）</p>
            <div className="flex flex-wrap items-baseline gap-x-2 gap-y-1 mt-2">
              <span className="text-3xl font-bold text-emerald-700 tabular-nums">{scoreLabel}</span>
              {scoreLabel !== '—' && (
                <span className="text-sm text-emerald-800/60 font-medium">/ 100</span>
              )}
            </div>
          </div>
        </div>
        {showQualityGate && report.qualityGateStatus && (
          <div className="shrink-0">
            <QualityGateBadge status={report.qualityGateStatus} />
          </div>
        )}
      </div>

      <AutoDimensionBarsList bars={bars} showWeight barClassName="bg-emerald-500" />

      <p className="mt-4 text-[11px] leading-relaxed text-[#1f1f1f]/45">
        加权：结构 35% · 密度 25% · 语言 20% · 引用 20% · 门禁：{QUALITY_GATE_RULES_SUMMARY}
      </p>
    </div>
  );
}
