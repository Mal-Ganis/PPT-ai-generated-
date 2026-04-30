import { useState } from 'react';
import { Search, AlertTriangle, CheckCircle2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { fetchEvaluationReports, type EvaluationReport } from '@/lib/backend';

const EvaluationSection = () => {
  const [projectId, setProjectId] = useState('');
  const [reports, setReports] = useState<EvaluationReport[] | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');

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

  return (
    <section className="min-h-screen pt-24 pb-16 bg-[#f3f3f3]">
      <div className="section-container">
        <div className="section-inner max-w-4xl">
          <div className="bg-white rounded-3xl shadow-lg p-8">
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-8">
              <div>
                <h1 className="text-3xl font-bold text-[#1f1f1f]">评估报告查询</h1>
                <p className="text-[#1f1f1f]/60 mt-2">
                  输入项目 ID 查询该项目历史评估报告详情。
                </p>
              </div>
              <div className="flex items-center gap-3">
                <Search className="w-5 h-5 text-[#3898ec]" />
                <span className="text-sm text-[#1f1f1f]/60">后端实时读取评估数据</span>
              </div>
            </div>

            <div className="grid gap-4 md:grid-cols-[1fr_auto] mb-6">
              <input
                type="number"
                value={projectId}
                onChange={event => setProjectId(event.target.value)}
                placeholder="请输入项目 ID"
                className="w-full rounded-2xl border border-gray-200 px-4 py-3 text-sm outline-none transition focus:border-[#3898ec] focus:ring-2 focus:ring-[#3898ec]/20"
              />
              <Button onClick={handleQuery} disabled={isLoading} className="min-w-[140px]">
                查询报告
              </Button>
            </div>

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

            {reports && reports.length > 0 && (
              <div className="space-y-6">
                {reports.map(report => (
                  <div key={report.id} className="rounded-3xl border border-gray-200 bg-[#fafbff] p-6">
                    <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mb-4">
                      <div>
                        <h2 className="text-xl font-semibold text-[#1f1f1f]">报告 #{report.id}</h2>
                        <p className="text-sm text-[#1f1f1f]/60 mt-1">
                          项目 ID：{report.projectId} {report.pageId ? `| 页面 ID：${report.pageId}` : ''}
                        </p>
                      </div>
                      <div className="rounded-2xl bg-[#e7f8f4] px-4 py-2 text-sm font-medium text-[#0f766e] inline-flex items-center gap-2">
                        <CheckCircle2 className="w-4 h-4" />
                        加权总分：{report.totalScore.toFixed(1)}
                      </div>
                    </div>

                    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4 mb-4">
                      <div className="rounded-2xl bg-white p-4 border border-gray-200">
                        <p className="text-sm text-[#1f1f1f]/70">大纲逻辑</p>
                        <p className="mt-2 text-2xl font-semibold text-[#1f1f1f]">{report.outlineLogicScore}</p>
                      </div>
                      <div className="rounded-2xl bg-white p-4 border border-gray-200">
                        <p className="text-sm text-[#1f1f1f]/70">事实准确率</p>
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

                    <div className="space-y-4">
                      <div className="rounded-2xl bg-white p-4 border border-gray-200">
                        <p className="text-sm font-medium text-[#1f1f1f] mb-2">改进建议</p>
                        <p className="text-sm text-[#1f1f1f]/80">{report.recommendations || '暂无建议'}</p>
                      </div>
                      <div className="rounded-2xl bg-white p-4 border border-gray-200">
                        <p className="text-sm font-medium text-[#1f1f1f] mb-2">用户反馈</p>
                        <p className="text-sm text-[#1f1f1f]/80">{report.userFeedback || '暂无反馈'}</p>
                      </div>
                      <p className="text-sm text-[#1f1f1f]/60">评估时间：{new Date(report.evaluationTime).toLocaleString()}</p>
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

export default EvaluationSection;
