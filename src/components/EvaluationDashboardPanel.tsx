import { useCallback, useEffect, useState } from 'react';
import { BarChart3, Loader2, RefreshCw } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { fetchEvaluationDashboard, type EvaluationDashboard } from '@/lib/backend';
import { qualityGateClassName, qualityGateLabel } from '@/lib/evaluationDisplay';

interface EvaluationDashboardPanelProps {
  /** 变更时重新拉取看板（如本项目写入新评估、校准后） */
  refreshToken?: number;
}

export function EvaluationDashboardPanel({ refreshToken = 0 }: EvaluationDashboardPanelProps) {
  const [data, setData] = useState<EvaluationDashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadDashboard = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setData(await fetchEvaluationDashboard());
    } catch (e) {
      setError(e instanceof Error ? e.message : '看板加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadDashboard();
  }, [refreshToken, loadDashboard]);

  if (loading) {
    return (
      <div className="rounded-2xl border border-gray-200 bg-white p-6 flex items-center gap-2 text-sm text-[#1f1f1f]/60">
        <Loader2 className="w-4 h-4 animate-spin" />
        加载跨项目质量看板…
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">{error}</div>
    );
  }

  if (!data) return null;

  return (
    <div className="rounded-2xl border border-gray-200 bg-white p-6 space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <BarChart3 className="w-5 h-5 text-[#3898ec]" />
          <h2 className="text-lg font-semibold text-[#1f1f1f]">跨项目质量看板</h2>
        </div>
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={loading}
          className="gap-1.5"
          onClick={() => void loadDashboard()}
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
          刷新看板
        </Button>
      </div>
      <p className="text-xs text-[#1f1f1f]/50 -mt-2">
        汇总各项目最新「整项目」自动评估；单页评估不计入看板。门禁：70 分以上通过 · 50–70 分预警。写入新报告后会自动刷新。
      </p>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="有评估的项目" value={String(data.projectCount)} />
        <StatCard
          label="平均自动总分"
          value={data.avgAutoTotalScore != null ? `${data.avgAutoTotalScore.toFixed(1)} 分` : '—'}
        />
        <StatCard
          label="平均引用覆盖"
          value={
            data.avgFactVerificationRate != null
              ? `${Math.round(data.avgFactVerificationRate)} 分`
              : '—'
          }
        />
        <StatCard
          label="校准（认同 / 总数）"
          value={`${data.calibrationAgreeCount} / ${data.calibrationTotal}`}
        />
      </div>

      <div className="flex flex-wrap gap-2 text-sm">
        <span className={`px-3 py-1 rounded-full border ${qualityGateClassName('PASS')}`}>
          门禁通过 {data.qualityGatePassCount}
        </span>
        <span className={`px-3 py-1 rounded-full border ${qualityGateClassName('WARN')}`}>
          预警 {data.qualityGateWarnCount}
        </span>
        <span className={`px-3 py-1 rounded-full border ${qualityGateClassName('FAIL')}`}>
          未通过 {data.qualityGateFailCount}
        </span>
      </div>

      {data.recentProjects.length > 0 && (
        <div>
          <p className="text-sm font-medium text-[#1f1f1f] mb-2">最近项目</p>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-[#1f1f1f]/55 border-b">
                  <th className="py-2 pr-3">项目</th>
                  <th className="py-2 pr-3">自动分</th>
                  <th className="py-2 pr-3">引用覆盖</th>
                  <th className="py-2">门禁</th>
                </tr>
              </thead>
              <tbody>
                {data.recentProjects.map((p) => (
                  <tr key={p.projectId} className="border-b border-gray-100">
                    <td className="py-2 pr-3 max-w-[180px] truncate" title={p.projectTitle}>
                      {p.projectTitle}
                    </td>
                    <td className="py-2 pr-3">
                      {p.autoTotalScore != null ? `${p.autoTotalScore.toFixed(1)} 分` : '—'}
                    </td>
                    <td className="py-2 pr-3">
                      {p.autoSourceCoverageScore != null ? `${p.autoSourceCoverageScore} 分` : '—'}
                    </td>
                    <td className="py-2">
                      <span
                        className={`text-xs px-2 py-0.5 rounded-full border ${qualityGateClassName(p.qualityGateStatus)}`}
                      >
                        {qualityGateLabel(p.qualityGateStatus)}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {data.calibrationRecords.length > 0 && (
        <div>
          <p className="text-sm font-medium text-[#1f1f1f] mb-2">校准回流（最近 {data.calibrationRecords.length} 条）</p>
          <ul className="space-y-2 text-sm text-[#1f1f1f]/75">
            {data.calibrationRecords.slice(0, 5).map((c) => (
              <li key={c.reportId} className="rounded-lg bg-[#f8fafc] px-3 py-2 border border-gray-100">
                项目 {c.projectId} · {c.agreeWithAuto ? '认同自动分' : '偏差较大'}
                {c.deltaFromAuto && !c.agreeWithAuto && (
                  <span className="text-xs text-[#1f1f1f]/50 ml-2">
                    Δ 大纲 {c.deltaFromAuto.outline ?? 0} / 密度 {c.deltaFromAuto.density ?? 0}
                  </span>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl bg-[#f8fafc] border border-gray-100 p-4">
      <p className="text-xs text-[#1f1f1f]/55">{label}</p>
      <p className="text-xl font-semibold text-[#1f1f1f] mt-1">{value}</p>
    </div>
  );
}
