import type { AutoDimensionBar } from '@/lib/evaluationQuality';
import { formatAutoWeight } from '@/lib/evaluationQuality';

interface AutoDimensionBarsListProps {
  bars: AutoDimensionBar[];
  showWeight?: boolean;
  barClassName?: string;
}

export function AutoDimensionBarsList({
  bars,
  showWeight = false,
  barClassName = 'bg-[#3898ec]',
}: AutoDimensionBarsListProps) {
  if (bars.length === 0) return null;

  return (
    <div className="space-y-3">
      {bars.map((bar) => (
        <div key={bar.label} className="space-y-1.5">
          <div className="flex items-center justify-between gap-2 text-xs text-[#1f1f1f]/70">
            <span className="font-medium text-[#1f1f1f]/80">{bar.label}</span>
            <span className="flex items-center gap-2 shrink-0 tabular-nums">
              {showWeight && bar.weight != null && (
                <span className="text-[#1f1f1f]/45">{formatAutoWeight(bar.weight)}</span>
              )}
              <span className="font-semibold text-[#1f1f1f]">{Math.round(bar.score)}</span>
            </span>
          </div>
          <div className="h-2 rounded-full bg-white/90 border border-gray-100 overflow-hidden">
            <div
              className={`h-full rounded-full transition-all ${barClassName}`}
              style={{ width: `${Math.min(100, Math.max(0, bar.score))}%` }}
            />
          </div>
        </div>
      ))}
    </div>
  );
}
