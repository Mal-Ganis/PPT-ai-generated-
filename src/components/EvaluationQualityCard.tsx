import { AutoDimensionBarsList } from '@/components/AutoDimensionBarsList';
import { QualityGateBadge } from '@/components/QualityGateBadge';
import { BarChart3, ChevronRight, Loader2, RefreshCw } from 'lucide-react';
import { Button } from '@/components/ui/button';
import type { EvaluationReport } from '@/lib/backend';
import { formatAutoTotalScore, getAutoDimensionBars } from '@/lib/evaluationQuality';

interface EvaluationQualityCardProps {
  report: EvaluationReport | null;
  onViewFullReport?: () => void;
  onReEvaluate?: () => void;
  isReEvaluating?: boolean;
  compact?: boolean;
}

export function EvaluationQualityCard({
  report,
  onViewFullReport,
  onReEvaluate,
  isReEvaluating = false,
  compact = false,
}: EvaluationQualityCardProps) {
  const bars = report ? getAutoDimensionBars(report) : [];
  const scoreLabel = formatAutoTotalScore(report?.autoTotalScore);

  return (
    <div
      className={`rounded-2xl border border-[#3898ec]/20 bg-gradient-to-br from-[#eef6ff] to-white ${
        compact ? 'p-4' : 'p-5'
      }`}
    >
      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3 mb-4">
        <div className="flex items-start gap-3">
          <div className="w-10 h-10 rounded-xl bg-[#3898ec]/15 flex items-center justify-center shrink-0">
            <BarChart3 className="w-5 h-5 text-[#3898ec]" />
          </div>
          <div>
            <p className="text-sm font-medium text-[#1f1f1f]/70">自动质量评估</p>
            {report ? (
              <>
                <div className="flex items-baseline gap-2 mt-0.5">
                  <p className="text-2xl font-bold text-[#3898ec] tabular-nums">{scoreLabel}</p>
                  {scoreLabel !== '—' && (
                    <span className="text-sm text-[#3898ec]/60 font-medium">/ 100</span>
                  )}
                </div>
                {report.qualityGateStatus && (
                  <div className="mt-2">
                    <QualityGateBadge status={report.qualityGateStatus} />
                  </div>
                )}
              </>
            ) : (
              <p className="text-sm text-[#1f1f1f]/50 mt-1">暂无评估报告，可点击下方重新评估</p>
            )}
          </div>
        </div>
        <div className="flex flex-wrap gap-2 shrink-0">
          {onReEvaluate && (
            <Button
              type="button"
              variant="secondary"
              size="sm"
              className="gap-1"
              disabled={isReEvaluating}
              onClick={onReEvaluate}
            >
              {isReEvaluating ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <RefreshCw className="w-4 h-4" />
              )}
              重新评估
            </Button>
          )}
          {onViewFullReport && (
            <Button type="button" variant="outline" size="sm" className="gap-1" onClick={onViewFullReport}>
              查看完整报告
              <ChevronRight className="w-4 h-4" />
            </Button>
          )}
        </div>
      </div>

      {bars.length > 0 && <AutoDimensionBarsList bars={bars} barClassName="bg-[#3898ec]" />}

      {report?.recommendations?.trim() && !compact && (
        <p className="mt-4 text-xs text-[#1f1f1f]/55 line-clamp-2">{report.recommendations}</p>
      )}
    </div>
  );
}