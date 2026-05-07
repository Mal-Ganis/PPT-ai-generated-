import { useState } from 'react';
import { Loader2, Search } from 'lucide-react';
import { FlowExitNav } from '@/components/FlowExitNav';
import { Button } from '@/components/ui/button';
import { searchIndexByText, type IndexSearchResult } from '@/lib/backend';
import { toast } from 'sonner';

function parseMeta(raw?: string): { url?: string; title?: string; trustScore?: number } {
  if (!raw) return {};
  try {
    const o = JSON.parse(raw) as Record<string, unknown>;
    return {
      url: typeof o.url === 'string' ? o.url : undefined,
      title: typeof o.title === 'string' ? o.title : undefined,
      trustScore: typeof o.trustScore === 'number' ? o.trustScore : undefined,
    };
  } catch {
    return {};
  }
}

export default function KnowledgeSearchPage() {
  const [projectId, setProjectId] = useState('');
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [results, setResults] = useState<IndexSearchResult[]>([]);

  const handleSearch = async () => {
    const pid = projectId.trim() ? Number(projectId) : undefined;
    if (!query.trim()) {
      toast.error('请输入检索关键词');
      return;
    }
    setLoading(true);
    try {
      const res = await searchIndexByText(query, pid, 10);
      setResults(res.results ?? []);
      if (!res.results?.length) {
        toast.message('未命中片段，可尝试扩大主题或先上传/索引文档');
      }
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '检索失败');
    } finally {
      setLoading(false);
    }
  };

  const selectForRegenerate = (r: IndexSearchResult) => {
    const meta = parseMeta(r.metadata);
    const payload = {
      segmentId: r.segmentId,
      snippet: r.content,
      url: meta.url,
      title: meta.title,
      distance: r.distance,
      trustScore: meta.trustScore,
    };
    sessionStorage.setItem('ppt-selected-citation', JSON.stringify(payload));
    toast.success('已保存引用到浏览器会话，可在「内容编辑」页结合备注使用。');
  };

  return (
    <section className="min-h-screen pt-24 pb-16 bg-[#f3f3f3]">
      <div className="section-container">
        <div className="section-inner max-w-4xl">
          <div className="flex flex-wrap items-center gap-4 mb-8">
            <FlowExitNav />
            <h1 className="text-2xl font-bold text-[#1f1f1f]">知识检索（ILF-2）</h1>
          </div>

          <div className="bg-white rounded-3xl shadow-lg p-6 mb-8 space-y-4">
            <p className="text-sm text-[#1f1f1f]/60">
              输入查询文本后由服务端嵌入向量并在索引中检索；可选填项目 ID 限定当前项目的向量片段。
            </p>
            <div className="grid gap-4 md:grid-cols-[1fr_2fr_auto]">
              <input
                type="number"
                placeholder="项目 ID（可选）"
                value={projectId}
                onChange={(e) => setProjectId(e.target.value)}
                className="rounded-2xl border border-gray-200 px-4 py-3 text-sm"
              />
              <input
                type="text"
                placeholder="检索关键词或句子"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                className="rounded-2xl border border-gray-200 px-4 py-3 text-sm md:col-span-1"
              />
              <Button type="button" onClick={handleSearch} disabled={loading} className="md:w-auto">
                {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Search className="w-4 h-4 mr-2" />}
                检索
              </Button>
            </div>
          </div>

          <div className="space-y-4">
            {results.map((r) => {
              const meta = parseMeta(r.metadata);
              const relevance = Math.max(0, 1 - r.distance / 2);
              return (
                <div
                  key={r.id}
                  className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm"
                >
                  <div className="flex flex-col sm:flex-row sm:justify-between gap-2 mb-3">
                    <span className="text-xs font-mono text-[#1f1f1f]/50">#{r.segmentId}</span>
                    <span className="text-sm text-[#1f1f1f]/60">
                      相关度（近似） {(relevance * 100).toFixed(1)}% · distance {r.distance.toFixed(4)}
                    </span>
                  </div>
                  <p className="text-[#1f1f1f] mb-4 whitespace-pre-wrap">{r.content}</p>
                  <div className="flex flex-wrap gap-2 text-sm">
                    {meta.url && (
                      <a
                        href={meta.url}
                        target="_blank"
                        rel="noreferrer"
                        className="text-[#3898ec] underline"
                      >
                        {meta.title || meta.url}
                      </a>
                    )}
                    {meta.trustScore != null && (
                      <span className="px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-800">
                        可信度 {(meta.trustScore * 100).toFixed(0)}%
                      </span>
                    )}
                  </div>
                  <Button type="button" variant="outline" size="sm" className="mt-4" onClick={() => selectForRegenerate(r)}>
                    保存为引用（用于后续重生）
                  </Button>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </section>
  );
}
