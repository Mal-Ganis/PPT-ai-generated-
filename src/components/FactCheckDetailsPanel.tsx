import { CheckCircle2, XCircle } from 'lucide-react';
import type { FactCheckDetail } from '@/lib/backend';
import { formatSupportScore } from '@/lib/evaluationDisplay';

interface FactCheckDetailsPanelProps {
  details?: FactCheckDetail[];
  compact?: boolean;
}

export function FactCheckDetailsPanel({ details, compact }: FactCheckDetailsPanelProps) {
  if (!details?.length) {
    return (
      <p className="text-sm text-[#1f1f1f]/50">
        暂无事实抽检明细（旧报告或未写入）。
      </p>
    );
  }

  return (
    <div className={`space-y-3 ${compact ? '' : 'mt-2'}`}>
      {details.map((item, idx) => {
        const skipped = item.method === 'skipped';
        return (
        <div
          key={idx}
          className={`rounded-xl border p-3 text-sm ${
            skipped
              ? 'border-slate-200 bg-slate-50/60'
              : item.passed
                ? 'border-emerald-200 bg-emerald-50/40'
                : 'border-amber-200 bg-amber-50/40'
          }`}
        >
          <div className="flex items-start gap-2 mb-2">
            {skipped ? (
              <span className="text-xs text-[#1f1f1f]/45 shrink-0 mt-0.5">—</span>
            ) : item.passed ? (
              <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" />
            ) : (
              <XCircle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
            )}
            <div className="min-w-0 flex-1">
              <p className={`font-medium line-clamp-3 ${skipped ? 'text-[#1f1f1f]/60' : 'text-[#1f1f1f]'}`}>
                {item.statement}
              </p>
              {item.slideTitle && (
                <p className="text-xs text-[#1f1f1f]/50 mt-1">页：{item.slideTitle}</p>
              )}
            </div>
            {!skipped && (
              <span className="text-xs font-medium shrink-0 text-[#1f1f1f]/70">
                {formatSupportScore(item)}
              </span>
            )}
          </div>
          {!compact && (
            <>
              <p
                className={`text-xs pl-6 border-t border-black/5 pt-2 mt-1 ${
                  skipped || item.evidence?.includes('未作为有效证据') || item.evidence?.includes('未检索到') || item.evidence?.includes('不参与事实抽检')
                    ? 'text-[#1f1f1f]/55 italic'
                    : 'text-[#1f1f1f]/65'
                }`}
              >
                <span className="font-medium not-italic">{skipped ? '说明：' : '证据：'}</span>
                {item.evidence}
              </p>
              {item.evidenceNote && (
                <p className="text-xs text-amber-900/85 pl-6 mt-2 bg-amber-50/60 rounded-lg py-2 px-2 border border-amber-100">
                  {item.evidenceNote}
                </p>
              )}
            </>
          )}
        </div>
        );
      })}
    </div>
  );
}
