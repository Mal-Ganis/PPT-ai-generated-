import { useEffect, useState } from 'react';
import { Search, AlertTriangle, CheckCircle2, Send, ThumbsDown, ThumbsUp, FileSearch } from 'lucide-react';
import { FlowExitNav } from '@/components/FlowExitNav';
import { EvaluationDashboardPanel } from '@/components/EvaluationDashboardPanel';
import { AutoEvaluationPanel } from '@/components/AutoEvaluationPanel';
import { EvaluationHistoryChart } from '@/components/EvaluationHistoryChart';
import { QualityGateBadge } from '@/components/QualityGateBadge';
import { Button } from '@/components/ui/button';
import {
  fetchEvaluationReports,
  submitEvaluationCalibration,
  submitEvaluationReport,
  submitPageEvaluation,
  triggerAutoEvaluation,
  type EvaluationReport,
} from '@/lib/backend';
import { buildEvaluationCompleteToast, mapAutoScoresToManualForm, QUALITY_GATE_RULES_SUMMARY } from '@/lib/evaluationQuality';
import { toast } from 'sonner';
import { formatRecommendations } from '@/lib/evaluationDisplay';

interface EvaluationSectionProps {
  defaultProjectId?: number | null;
  flowBack?: { label: string; onClick: () => void };
}

const EvaluationSection = ({ defaultProjectId, flowBack }: EvaluationSectionProps) => {
  const [projectId, setProjectId] = useState(defaultProjectId != null ? String(defaultProjectId) : '');
  const [reports, setReports] = useState<EvaluationReport[] | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');

  const [outlineLogicScore, setOutlineLogicScore] = useState(75);
  const [infoDensityScore, setInfoDensityScore] = useState(75);
  const [languageExpressionScore, setLanguageExpressionScore] = useState(75);
  const [recommendations, setRecommendations] = useState('');
  const [userFeedback, setUserFeedback] = useState('');
  const [submitMessage, setSubmitMessage] = useState('');
  const [calibrationMessage, setCalibrationMessage] = useState('');
  const [pageSlideId, setPageSlideId] = useState('');
  const [pageEvalMessage, setPageEvalMessage] = useState('');
  const [autoEvalMessage, setAutoEvalMessage] = useState('');
  const [dashboardRefresh, setDashboardRefresh] = useState(0);

  const bumpDashboard = () => setDashboardRefresh((n) => n + 1);

  useEffect(() => {
    if (defaultProjectId != null) {
      setProjectId(String(defaultProjectId));
    }
  }, [defaultProjectId]);

  useEffect(() => {
    if (defaultProjectId == null || defaultProjectId <= 0) return;
    void (async () => {
      setIsLoading(true);
      setError('');
      try {
        const data = await fetchEvaluationReports(defaultProjectId);
        setReports(data);
        if (data.length === 0) {
          setError('当前项目暂无评估报告（可点击「重新自动评估」，或在正文/单页重生后自动写入）');
        }
      } catch (e) {
        setError(e instanceof Error ? e.message : '查询失败，请稍后重试');
      } finally {
        setIsLoading(false);
      }
    })();
  }, [defaultProjectId]);

  const applyLatestAutoScores = () => {
    const latest = reports?.[0];
    if (!latest?.autoTotalScore) {
      setSubmitMessage('暂无自动评估数据，请先完成正文生成或点击「查询报告」。');
      return;
    }
    const mapped = mapAutoScoresToManualForm(latest);
    setOutlineLogicScore(mapped.outlineLogicScore);
    setInfoDensityScore(mapped.infoDensityScore);
    setLanguageExpressionScore(mapped.languageExpressionScore);
    if (latest.recommendations?.trim()) {
      setRecommendations(latest.recommendations);
    }
    setSubmitMessage('已填入最新自动评估分项，可按需微调后提交人工评分。');
  };

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
      bumpDashboard();
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

  const handleTriggerAutoEvaluation = async () => {
    setAutoEvalMessage('');
    const parsedId = Number(projectId);
    if (!parsedId || parsedId <= 0) {
      setAutoEvalMessage('请先填写有效的项目 ID');
      return;
    }
    setIsLoading(true);
    try {
      const report = await triggerAutoEvaluation(parsedId);
      toast.success(buildEvaluationCompleteToast(report), { duration: 6000 });
      setAutoEvalMessage('已写入最新自动评估报告。');
      await handleQuery();
    } catch (e) {
      setAutoEvalMessage(e instanceof Error ? e.message : '自动评估失败，请查看后端日志');
    } finally {
      setIsLoading(false);
    }
  };

  const handlePageEvaluation = async () => {
    setPageEvalMessage('');
    const parsedProjectId = Number(projectId);
    const parsedSlideId = Number(pageSlideId);
    if (!parsedProjectId || parsedProjectId <= 0) {
      setPageEvalMessage('请先填写有效的项目 ID');
      return;
    }
    if (!parsedSlideId || parsedSlideId <= 0) {
      setPageEvalMessage('请填写要评估的幻灯片 ID（pageId）');
      return;
    }
    try {
      await submitPageEvaluation(parsedProjectId, parsedSlideId);
      setPageEvalMessage(`已写入单页评估（slideId=${parsedSlideId}），含 LLM 改进建议。`);
      await handleQuery();
    } catch (e) {
      setPageEvalMessage(e instanceof Error ? e.message : '单页评估失败');
    }
  };

  return (
    <section className="min-h-screen pt-24 pb-16 bg-[#f3f3f3]">
      <div className="section-container">
        <div className="section-inner max-w-4xl space-y-8">
          <FlowExitNav flowBack={flowBack} projectsLinkState={{ returnTo: 'evaluation' }} />
          <EvaluationDashboardPanel refreshToken={dashboardRefresh} />
          <div className="bg-white rounded-3xl shadow-lg p-8">
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-6">
              <div>
                <h1 className="text-3xl font-bold text-[#1f1f1f]">评估反馈（EI-5）</h1>
                <p className="text-[#1f1f1f]/60 mt-2">
                  自动评估含质量门禁（{QUALITY_GATE_RULES_SUMMARY}）与 LLM 改进建议；支持整项目与单页（pageId）评估及校准回流。
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
              <div>
                <label className="block text-sm font-medium text-[#1f1f1f] mb-2">单页评估 · 幻灯片 ID</label>
                <input
                  type="number"
                  value={pageSlideId}
                  onChange={(e) => setPageSlideId(e.target.value)}
                  placeholder="slideId / pageId"
                  className="w-full rounded-2xl border border-gray-200 px-4 py-3 text-sm outline-none transition focus:border-[#3898ec] focus:ring-2 focus:ring-[#3898ec]/20"
                />
              </div>
              <div className="flex items-end">
                <Button type="button" variant="outline" onClick={handlePageEvaluation} className="gap-2">
                  <FileSearch className="w-4 h-4" />
                  评估该页
                </Button>
              </div>
            </div>
            {pageEvalMessage && (
              <div className="rounded-2xl bg-sky-50 border border-sky-200 p-4 text-sm text-sky-950 mb-6">
                {pageEvalMessage}
              </div>
            )}

            <div className="grid gap-4 sm:grid-cols-2 mb-4">
              <ScoreField label="大纲逻辑 (0–100)" value={outlineLogicScore} onChange={setOutlineLogicScore} />
              <ScoreField label="信息密度 (0–100)" value={infoDensityScore} onChange={setInfoDensityScore} />
              <ScoreField
                label="语言表达 (0–100)"
                value={languageExpressionScore}
                onChange={setLanguageExpressionScore}
              />
            </div>
            {reports?.[0]?.autoTotalScore != null && (
              <div className="mb-6">
                <Button type="button" variant="outline" size="sm" onClick={applyLatestAutoScores}>
                  采用最新自动分（可再微调）
                </Button>
                <p className="text-xs text-[#1f1f1f]/50 mt-2">
                  自动评估已在正文生成后写入；可先一键对齐分项，再补充主观反馈。
                </p>
              </div>
            )}

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
              <Button
                type="button"
                variant="secondary"
                onClick={() => void handleTriggerAutoEvaluation()}
                disabled={isLoading}
              >
                重新自动评估
              </Button>
            </div>

            {autoEvalMessage && (
              <div className="rounded-2xl bg-sky-50 border border-sky-200 p-4 text-sm text-sky-950 mb-4">
                {autoEvalMessage}
              </div>
            )}

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
              <div className="rounded-2xl border border-gray-200 bg-white p-4 mb-8">
                <p className="text-sm font-medium text-[#1f1f1f] mb-3">评估历史趋势</p>
                <EvaluationHistoryChart reports={reports} />
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
                          项目 ID：{report.projectId}{' '}
                          {report.pageId ? `| 单页 ID：${report.pageId}` : '| 整项目评估'}
                        </p>
                        {report.qualityGateStatus && (
                          <div className="mt-2">
                            <QualityGateBadge status={report.qualityGateStatus} />
                          </div>
                        )}
                      </div>
                      <div className="flex flex-wrap gap-2">
                        <div className="rounded-2xl bg-[#e7f0ff] px-4 py-2 text-sm font-medium text-[#0f5abb] inline-flex items-center gap-2">
                          <CheckCircle2 className="w-4 h-4" />
                          人工加权总分：{report.totalScore.toFixed(1)} 分
                        </div>
                        {report.autoTotalScore != null && (
                          <div className="rounded-2xl bg-[#e7f8f4] px-4 py-2 text-sm font-medium text-[#0f766e] inline-flex items-center gap-2">
                            <CheckCircle2 className="w-4 h-4" />
                            自动加权总分：{report.autoTotalScore.toFixed(1)} 分
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
                            <p className="text-sm text-[#1f1f1f]/70">信息密度</p>
                            <p className="mt-2 text-2xl font-semibold text-[#1f1f1f]">{report.infoDensityScore}</p>
                          </div>
                          <div className="rounded-2xl bg-white p-4 border border-gray-200 sm:col-span-2">
                            <p className="text-sm text-[#1f1f1f]/70">语言表达</p>
                            <p className="mt-2 text-2xl font-semibold text-[#1f1f1f]">{report.languageExpressionScore}</p>
                          </div>
                        </div>
                      </div>
                      <AutoEvaluationPanel report={report} showQualityGate={false} />
                    </div>

                    {report.qualityGateReasons && report.qualityGateReasons.length > 0 && (
                      <div className="rounded-2xl bg-white p-4 border border-gray-200 mb-4">
                        <p className="text-sm font-medium text-[#1f1f1f] mb-2">质量门禁说明</p>
                        <ul className="text-sm text-[#1f1f1f]/75 list-disc pl-5 space-y-1">
                          {report.qualityGateReasons.map((reason, i) => (
                            <li key={i}>{reason}</li>
                          ))}
                        </ul>
                      </div>
                    )}

                    <div className="space-y-4">
                      <div className="rounded-2xl bg-white p-4 border border-gray-200">
                        <p className="text-sm font-medium text-[#1f1f1f] mb-2">改进建议（LLM）</p>
                        {formatRecommendations(report.recommendations).length > 0 ? (
                          <ul className="text-sm text-[#1f1f1f]/80 list-disc pl-5 space-y-1">
                            {formatRecommendations(report.recommendations).map((line, i) => (
                              <li key={i}>{line}</li>
                            ))}
                          </ul>
                        ) : (
                          <p className="text-sm text-[#1f1f1f]/80">{report.recommendations || '暂无建议'}</p>
                        )}
                      </div>
                      {report.calibrationAgreeWithAuto != null && (
                        <div className="rounded-2xl bg-violet-50 p-4 border border-violet-200">
                          <p className="text-sm font-medium text-[#1f1f1f] mb-1">校准回流</p>
                          <p className="text-sm text-[#1f1f1f]/75">
                            {report.calibrationAgreeWithAuto ? '用户认同自动分' : '用户认为自动分偏差较大'}
                            {report.calibrationDeltaFromAuto && !report.calibrationAgreeWithAuto && (
                              <span className="block text-xs mt-1 text-[#1f1f1f]/55">
                                与自动分差值：大纲 {report.calibrationDeltaFromAuto.outline ?? 0}，密度{' '}
                                {report.calibrationDeltaFromAuto.density ?? 0}，语言{' '}
                                {report.calibrationDeltaFromAuto.language ?? 0}
                              </span>
                            )}
                          </p>
                        </div>
                      )}
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

export default EvaluationSection;
