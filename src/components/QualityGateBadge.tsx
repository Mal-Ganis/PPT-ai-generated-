import { qualityGateClassName, qualityGateLabel } from '@/lib/evaluationDisplay';

export function QualityGateBadge({ status }: { status?: string | null }) {
  return (
    <span
      className={`inline-flex items-center text-xs font-medium px-2.5 py-1 rounded-full border ${qualityGateClassName(status)}`}
    >
      质量门禁：{qualityGateLabel(status)}
    </span>
  );
}
