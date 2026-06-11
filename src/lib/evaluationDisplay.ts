import type { EvaluationReport } from '@/lib/backend';

export function qualityGateLabel(status?: string | null): string {
  switch (status) {
    case 'PASS':
      return '通过';
    case 'WARN':
      return '预警';
    case 'FAIL':
      return '未通过';
    default:
      return '未知';
  }
}

export function qualityGateClassName(status?: string | null): string {
  switch (status) {
    case 'PASS':
      return 'bg-emerald-50 text-emerald-800 border-emerald-200';
    case 'WARN':
      return 'bg-amber-50 text-amber-900 border-amber-200';
    case 'FAIL':
      return 'bg-red-50 text-red-800 border-red-200';
    default:
      return 'bg-gray-50 text-gray-600 border-gray-200';
  }
}

export function formatSupportScore(detail: { supportScore?: number; method?: string }): string {
  const score = detail.supportScore;
  if (score == null || !Number.isFinite(score)) return '—';
  if (detail.method === 'overlap') {
    return `${Math.round(score * 100)}% 词重叠`;
  }
  return `${Math.round(score * 100)}% 语义`;
}

export function chartReports(reports: EvaluationReport[]): EvaluationReport[] {
  return [...reports].reverse();
}

export function formatRecommendations(text?: string | null): string[] {
  if (!text?.trim()) return [];
  return text
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean)
    .map((l) => l.replace(/^[-•*]\s*/, ''));
}
