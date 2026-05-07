import { useEffect, useState } from 'react';
import { Search, AlertTriangle, CheckCircle2, Send, ThumbsDown, ThumbsUp } from 'lucide-react';
import { FlowExitNav } from '@/components/FlowExitNav';
import { Button } from '@/components/ui/button';
import {
  fetchEvaluationReports,
  submitEvaluationReport,
  submitEvaluationCalibration,
  type EvaluationReport,
} from '@/lib/backend';

interface EvaluationSectionProps {
  defaultProjectId?: number | null;
}

const EvaluationSection = ({ defaultProjectId }: EvaluationSectionProps) => {
  const [projectId, setProjectId] = useState(defaultProjectId != null ? String(defaultProjectId) : '');
  const [reports, setReports] = useState<EvaluationReport[] | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');

  const [outlineLogicScore, setOutlineLogicScore] = useState(75);
  const [factualAccuracyScore, setFactualAccuracyScore] = useState(75);
  const [infoDensityScore, setInfoDensityScore] = useState(75);
  const [languageExpressionScore, setLanguageExpressionScore] = useState(75);
  const [recommendations, setRecommendations] = useState('');
  const [userFeedback, setUserFeedback] = useState('');
  const [submitMessage, setSubmitMessage] = useState('');
  const [calibrationMessage, setCalibrationMessage] = useState('');

  useEffect(() => {
    if (defaultProjectId != null) {
      setProjectId(String(defaultProjectId));
    }
  }, [defaultProjectId]);

  const handleQuery = async () => {
    setError('');
    setReports(null);

    const parsedId = Number(projectId);
    if (!parsedId || parsedId <= 0) {
      setError('请输入有效的项目 ID');
      return;
    }

    setIsLoading(true);
    try {
      const data = await fetchEvaluationReports(parsedId);
      setReports(data);
      if (data.length === 0) {
        setError('当前项目暂无评估报告');
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : '查询失败，请稍后重试');
    } finally {
      setIsLoading(false);
    }
  };

  const handleSubmitFeedback = async () => {
    setSubmitMessage('');
    const parsedId = Number(projectId);
    if (!parsedId || parsedId <= 0) {
      setSubmitMessage('请先填写有效的项目 ID');
      return;
    }
    try {
      await submitEvaluationReport(parsedId, {
        outlineLogicScore,
        factualAccuracyScore,
        infoDensityScore,
        languageExpressionScore,
        recommendations: recommendations || undefined,
        userFeedback: userFeedback || undefined,
      });
      setSubmitMessage('已提交评估并写入 ILF-3，可点击「查询报告」查看最新记录。');
      await handleQuery();
    } catch (e) {
      setSubmitMessage(e instanceof Error ? e.message : '提交失败');
    }
  };

  const handleCalibration = async (agreeWithAuto: boolean) => {
    setCalibrationMessage('');
    const parsedId = Number(projectId);
    if (!parsedId || parsedId <= 0) {
      setCalibrationMessage('请先填写有效的项目 ID');
      return;
    }
    try {
      await submitEvaluationCalibration(parsedId, {
        agreeWithAuto,
        note: agreeWithAuto ? undefined : userFeedback || undefined,
      });
      setCalibrationMessage(
        agreeWithAuto ? '已记录：人工分项与最新自动启发式对齐。' : '已记录：对自动分存在疑虑（人工分项已写入中性偏低）。',
      );
      await handleQuery();
    } catch (e) {
      setCalibrationMessage(e instanceof Error ? e.message : '校准提交失败');
    }
  };

  return (
    <section className="min-h-screen pt-24 pb-16 bg-[#f3f3f3]">
      <div className="section-container">
        <div className="section-inner max-w-4xl space-y-8">
          <FlowExitNav />
          <div className="bg-white rounded-3xl shadow-lg p-8">
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-6">
              <div>
                <h1 className="text-3xl font-bold text-[#1f1f1f]">评估反馈（EI-5）</h1>
                <p className="text-[#1f1f1f]/60 mt-2">
                  提交人工评分后，后端自动计算多维度指标（含向量检索事实抽检一致率），两类分数一并写入 ILF-3。
                </p>
              </div>
              <div className="flex items-center gap-3">
                <Search className="w-5 h-5 text-[#3898ec]" />
                <span className="text-sm text-[#1f1f1f]/60">ILF-3 存储</span>
              </div>
            </div>

            <label className="block text-sm font-medium text-[#1f1f1f] mb-2">项目 ID</label>
            <input
              type="number"
              value={projectId}
              onChange={(event) => setProjectId(event.target.value)}
              placeholder="请输入项目 ID"
              className="w-full rounded-2xl border border-gray-200 px-4 py-3 text-sm outline-none transition focus:border-[#3898ec] focus:ring-2 focus:ring-[#3898ec]/20 mb-6"
            />

            <div className="grid gap-4 sm:grid-cols-2 mb-6">
              <ScoreField label="大纲逻辑 (0–100)" value={outlineLogicScore} onChange={setOutlineLogicScore} />
              <ScoreField label="事实准确率 (0–100)" value={factualAccuracyScore} onChange={setFactualAccuracyScore} />
              <ScoreField label="信息密度 (0–100)" value={infoDensityScore} onChange={setInfoDensityScore} />
              <ScoreField
                label="语言表达 (0–100)"
                value={languageExpressionScore}
                onChange={setLanguageExpressionScore}
              />
            </div>

            <div className="space-y-4 mb-6">
              <div>
                <label className="block text-sm font-medium text-[#1f1f1f] mb-2">改进建议</label>
                <textarea
                  value={recommendations}
                  onChange={(e) => setRecommendations(e.target.value)}
                  className="w-full min-h-[80px] rounded-2xl border border-gray-200 px-4 py-3 text-sm"
                  placeholder="可选：对大纲或事实层面的改进建议"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-[#1f1f1f] mb-2">用户反馈</label>
                <textarea
                  value={userFeedback}
                  onChange={(e) => setUserFeedback(e.target.value)}
                  className="w-full min-h-[80px] rounded-2xl border border-gray-200 px-4 py-3 text-sm"
                  placeholder="可选：主观评价或使用感受"
                />
              </div>
            </div>

            <div className="flex flex-wrap gap-3 mb-4">
              <Button type="button" onClick={handleSubmitFeedback} className="inline-flex items-center gap-2">
                <Send className="w-4 h-4" />
                提交评估
              </Button>
              <Button type="button" variant="outline" onClick={handleQuery} disabled={isLoading}>
                查询报告
              </Button>
            </div>

            {submitMessage && (
              <div className="rounded-2xl bg-emerald-50 border border-emerald-200 p-4 text-sm text-emerald-900 mb-4">
                {submitMessage}
              </div>
            )}

            {error && (
              <div className="rounded-2xl bg-red-50 border border-red-200 p-4 text-sm text-red-700 mb-6 flex items-center gap-2">
                <AlertTriangle className="w-4 h-4" />
                {error}
              </div>
            )}

            {isLoading && (
              <div className="rounded-2xl bg-[#eef6ff] border border-[#cfe2ff] p-4 text-sm text-[#1f4b9f]">
                查询中，请稍候...
              </div>
            )}

            {calibrationMessage && (
              <div className="rounded-2xl bg-sky-50 border border-sky-200 p-4 text-sm text-sky-950 mb-4">
                {calibrationMessage}
              </div>
            )}

            {reports && reports.length > 0 && (
              <div className="space-y-6 mt-8">
                {reports.map((report, index) => (
                  <div key={report.id} className="rounded-3xl border border-gray-200 bg-[#fafbff] p-6">
                    <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mb-4">
                      <div>
                        <h2 className="text-xl font-semibold text-[#1f1f1f]">报告 #{report.id}</h2>
                        <p className="text-sm text-[#1f1f1f]/60 mt-1">
                          项目 ID：{report.projectId} {report.pageId ? `| 页面 ID：${report.pageId}` : ''}
                        </p>
                      </div>
                      <div className="flex flex-wrap gap-2">
                        <div className="rounded-2xl bg-[#e7f0ff] px-4 py-2 text-sm font-medium text-[#0f5abb] inline-flex items-center gap-2">
                          <CheckCircle2 className="w-4 h-4" />
                          人工加权总分：{report.totalScore.toFixed(1)}
                        </div>
                        {report.autoTotalScore != null && (
                          <div className="rounded-2xl bg-[#e7f8f4] px-4 py-2 text-sm font-medium text-[#0f766e] inline-flex items-center gap-2">
                            <CheckCircle2 className="w-4 h-4" />
                            自动加权总分：{report.autoTotalScore.toFixed(1)}
                          </div>
                        )}
                      </div>
                    </div>

                    {report.autoTotalScore != null && index === 0 && (
                      <div className="flex flex-wrap items-center gap-2 mb-4 pb-4 border-b border-gray-200">
                        <span className="text-sm text-[#1f1f1f]/70 mr-2">拇指校准（针对最新一条）：</span>
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          className="gap-1"
                          onClick={() => handleCalibration(true)}
                        >
                          <ThumbsUp className="w-4 h-4" />
                          认同自动分
                        </Button>
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          className="gap-1"
                          onClick={() => handleCalibration(false)}
                        >
                          <ThumbsDown className="w-4 h-4" />
                          偏差较大
                        </Button>
                        <span className="text-xs text-[#1f1f1f]/50">
                          「偏差较大」可选用下方用户反馈说明原因
                        </span>
                      </div>
                    )}

                    <div className="grid gap-6 lg:grid-cols-2 mb-4">
                      <div>
                        <p className="text-sm font-semibold text-[#0f5abb] mb-3">人工评分</p>
                        <div className="grid gap-4 sm:grid-cols-2">
                          <div className="rounded-2xl bg-white p-4 border border-gray-200">
                            <p className="text-sm text-[#1f1f1f]/70">大纲逻辑</p>
                            <p className="mt-2 text-2xl font-semibold text-[#1f1f1f]">{report.outlineLogicScore}</p>
                          </div>
                          <div className="rounded-2xl bg-white p-4 border border-gray-200">
                            <p className="text-sm text-[#1f1f1f]/70">事实准确率（人工）</p>
                            <p className="mt-2 text-2xl font-semibold text-[#1f1f1f]">{report.factualAccuracyScore}</p>
                          </div>
                          <div className="rounded-2xl bg-white p-4 border border-gray-200">
                            <p className="text-sm text-[#1f1f1f]/70">信息密度</p>
                            <p className="mt-2 text-2xl font-semibold text-[#1f1f1f]">{report.infoDensityScore}</p>
                          </div>
                          <div className="rounded-2xl bg-white p-4 border border-gray-200">
                            <p className="text-sm text-[#1f1f1f]/70">语言表达</p>
                            <p className="mt-2 text-2xl font-semibold text-[#1f1f1f]">{report.languageExpressionScore}</p>
                          </div>
                        </div>
                      </div>
                      <div>
                        <p className="text-sm font-semibold text-[#0f766e] mb-3">自动评估（后端）</p>
                        {report.autoOutlineLogicScore != null ? (
                          <div className="grid gap-4 sm:grid-cols-2">
                            <div className="rounded-2xl bg-white p-4 border border-gray-200">
                              <p className="text-sm text-[#1f1f1f]/70">结构 / 逻辑启发式</p>
                              <p className="mt-2 text-2xl font-semibold text-[#1f1f1f]">{report.autoOutlineLogicScore}</p>
                            </div>
                            <div className="rounded-2xl bg-white p-4 border border-gray-200">
                              <p className="text-sm text-[#1f1f1f]/70">信息密度</p>
                              <p className="mt-2 text-2xl font-semibold text-[#1f1f1f]">{report.autoInfoDensityScore ?? '—'}</p>
                            </div>
                            <div className="rounded-2xl bg-white p-4 border border-gray-200">
                              <p className="text-sm text-[#1f1f1f]/70">事实准确率（自动 · 语义映射）</p>
                              <p className="mt-2 text-2xl font-semibold text-[#1f1f1f]">{report.autoFactualAccuracyScore ?? '—'}</p>
                            </div>
                            <div className="rounded-2xl bg-white p-4 border border-gray-200">
                              <p className="text-sm text-[#1f1f1f]/70">语言表达 / 连贯</p>
                              <p className="mt-2 text-2xl font-semibold text-[#1f1f1f]">{report.autoLanguageExpressionScore ?? '—'}</p>
                            </div>
                            <div className="rounded-2xl bg-white p-4 border border-gray-200 sm:col-span-2">
                              <p className="text-sm text-[#1f1f1f]/70">引用来源覆盖 · 语义事实抽检（factVerificationRate）</p>
                              <p className="mt-2 text-lg font-semibold text-[#1f1f1f]">
                                {report.autoSourceCoverageScore ?? '—'} 分（有来源页占比）·{' '}
                                {report.factVerificationRate != null
                                  ? formatFactVerification(report.factVerificationRate)
                                  : '—'}
                              </p>
                            </div>
                          </div>
                        ) : (
                          <p className="text-sm text-[#1f1f1f]/50">暂无自动评估数据（旧报告或未写入）。</p>
                        )}
                      </div>
                    </div>

                    <div className="space-y-4">
                      <div className="rounded-2xl bg-white p-4 border border-gray-200">
                        <p className="text-sm font-medium text-[#1f1f1f] mb-2">改进建议</p>
                        <p className="text-sm text-[#1f1f1f]/80">{report.recommendations || '暂无建议'}</p>
                      </div>
                      <div className="rounded-2xl bg-white p-4 border border-gray-200">
                        <p className="text-sm font-medium text-[#1f1f1f] mb-2">用户反馈</p>
                        <p className="text-sm text-[#1f1f1f]/80">{report.userFeedback || '暂无反馈'}</p>
                      </div>
                      <p className="text-sm text-[#1f1f1f]/60">
                        评估时间：{new Date(report.evaluationTime).toLocaleString()}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </section>
  );
};

function ScoreField(props: {
  label: string;
  value: number;
  onChange: (v: number) => void;
}) {
  return (
    <div>
      <label className="block text-sm text-[#1f1f1f]/70 mb-2">{props.label}</label>
      <input
        type="number"
        min={0}
        max={100}
        value={props.value}
        onChange={(e) => props.onChange(Number(e.target.value))}
        className="w-full rounded-2xl border border-gray-200 px-4 py-3 text-sm"
      />
    </div>
  );
}

/** 后端存 0~1（新规）或旧版 0~100 词重叠率 */
function formatFactVerification(rate: number): string {
  if (rate > 1) {
    return `词重叠抽检约 ${rate.toFixed(1)}%（旧口径）`;
  }
  return `语义证据支持度均值 ${(rate * 100).toFixed(1)}%（目标 ≥92%）`;
}

export default EvaluationSection;
