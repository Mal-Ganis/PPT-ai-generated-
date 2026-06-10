import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ArrowLeft, Loader2, Plus, Search } from 'lucide-react';
import { FlowExitNav } from '@/components/FlowExitNav';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group';
import { pushPendingCitation } from '@/lib/citationClipboard';
import { formatIndexHitAsSourceLine } from '@/lib/citationFormat';
import { saveCitationReturnContext } from '@/lib/citationReturnContext';
import {
  countHighlightMatches,
  extractSearchHighlightTerms,
  renderHighlightedText,
} from '@/lib/searchHighlight';
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
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const ctxProjectId = searchParams.get('projectId') ?? '';
  const ctxSlideId = searchParams.get('slideId') ?? '';
  const ctxSlideIndex = searchParams.get('slideIndex');
  const ctxFlowStep = searchParams.get('flowStep');
  const ctxBullet = searchParams.get('bullet');
  const ctxQuery = searchParams.get('q') ?? '';

  const [projectId, setProjectId] = useState(ctxProjectId);
  const [query, setQuery] = useState(ctxQuery);
  const [loading, setLoading] = useState(false);
  const [results, setResults] = useState<IndexSearchResult[]>([]);
  /** 最近一次检索提交的关键词，用于高亮（避免输入框改动影响已出结果） */
  const [highlightQuery, setHighlightQuery] = useState(ctxQuery.trim());
  /** 结果排序：vector=后端向量相关度（默认）；literal=字面重合优先 */
  const [sortMode, setSortMode] = useState<'vector' | 'literal'>('vector');
  /** 是否在正文中标出与检索词字面重合的部分 */
  const [highlightEnabled, setHighlightEnabled] = useState(true);

  useEffect(() => {
    if (ctxProjectId) setProjectId(ctxProjectId);
    if (ctxQuery) setQuery(ctxQuery);
  }, [ctxProjectId, ctxQuery]);

  const targetSlideId = ctxSlideId ? Number(ctxSlideId) : null;
  const targetProjectId = projectId.trim() ? Number(projectId) : null;
  const slideIndex = ctxSlideIndex != null && ctxSlideIndex !== '' ? Number(ctxSlideIndex) : 0;
  const flowStep =
    ctxFlowStep === 'content' || ctxFlowStep === 'preview' ? ctxFlowStep : 'content';
  const bulletIndex = ctxBullet != null && ctxBullet !== '' ? Number(ctxBullet) : undefined;
  const canAppendToSlide =
    targetProjectId != null &&
    Number.isFinite(targetProjectId) &&
    targetSlideId != null &&
    Number.isFinite(targetSlideId);

  const contextHint = useMemo(() => {
    if (!canAppendToSlide) return null;
    const parts = [`项目 ${targetProjectId}`, `第 ${slideIndex + 1} 页`];
    if (bulletIndex != null && Number.isFinite(bulletIndex)) {
      parts.push(`要点 ${bulletIndex + 1}`);
    }
    return parts.join(' · ');
  }, [canAppendToSlide, targetProjectId, slideIndex, bulletIndex]);

  const highlightTerms = useMemo(
    () => (highlightEnabled ? extractSearchHighlightTerms(highlightQuery) : []),
    [highlightEnabled, highlightQuery],
  );

  const displayResults = useMemo(() => {
    if (sortMode === 'literal') {
      return [...results].sort((a, b) => {
        const ma = countHighlightMatches(a.content, highlightTerms);
        const mb = countHighlightMatches(b.content, highlightTerms);
        if (mb !== ma) return mb - ma;
        return a.distance - b.distance;
      });
    }
    return results;
  }, [results, highlightTerms, sortMode]);

  const handleReturnToEdit = () => {
    if (canAppendToSlide) {
      saveCitationReturnContext({
        step: flowStep,
        projectId: targetProjectId!,
        slideIndex: Number.isFinite(slideIndex) ? slideIndex : 0,
        slideId: targetSlideId!,
      });
    }
    navigate('/', { state: { resumeMainFlow: Date.now() } });
  };

  const handleSearch = async () => {
    const pid = projectId.trim() ? Number(projectId) : undefined;
    if (!query.trim()) {
      toast.error('请输入检索关键词');
      return;
    }
    setLoading(true);
    const submitted = query.trim();
    setHighlightQuery(submitted);
    try {
      const res = await searchIndexByText(submitted, pid, 10);
      setResults(res.results ?? []);
      if (!res.results?.length) {
        toast.message('未命中片段，可尝试扩大主题或先在大纲阶段上传/索引文档');
      }
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '检索失败');
    } finally {
      setLoading(false);
    }
  };

  const appendToSlideCitation = (r: IndexSearchResult, index: number) => {
    if (!canAppendToSlide) {
      toast.error('请从「内容编辑 → 引用来源 → 检索此条」进入，以便回填到对应页面');
      return;
    }
    const line = formatIndexHitAsSourceLine(r, index + 1);
    if (!line) {
      toast.error('该结果无法格式化为有效引用行');
      return;
    }
    pushPendingCitation({
      projectId: targetProjectId!,
      slideId: targetSlideId!,
      line,
      snippet: r.content,
      bulletIndex: Number.isFinite(bulletIndex) ? bulletIndex : undefined,
    });
    toast.success('已加入本页待插入列表，返回内容页点「插入本条」或「全部插入并保存」');
  };

  return (
    <section className="min-h-screen pt-24 pb-16 bg-[#f3f3f3]">
      <div className="section-container">
        <div className="section-inner max-w-4xl">
          <div className="flex flex-wrap items-center gap-4 mb-8">
            <FlowExitNav />
            <h1 className="text-2xl font-bold text-[#1f1f1f]">知识检索</h1>
          </div>

          {contextHint && (
            <div className="mb-4 rounded-xl border border-[#3898ec]/25 bg-[#3898ec]/10 px-4 py-3 text-sm text-[#1f1f1f] flex flex-wrap items-center justify-between gap-2">
              <span>
                正在为 <strong>{contextHint}</strong> 检索引用；命中后点「追加到本页引用」，再返回编辑页插入。
              </span>
              <Button
                type="button"
                size="sm"
                variant="outline"
                className="h-8"
                onClick={handleReturnToEdit}
              >
                <ArrowLeft className="w-3.5 h-3.5 mr-1" />
                返回编辑页
              </Button>
            </div>
          )}

          <div className="bg-white rounded-3xl shadow-lg p-6 mb-8 space-y-4">
            <p className="text-sm text-[#1f1f1f]/60">
              检索项目向量库或全局索引。默认按
              <strong className="font-medium text-[#1f1f1f]/75">向量相关度</strong>
              排序；可切换为「字面重合优先」或开启
              <mark className="mx-1 bg-amber-200 text-amber-950 rounded px-1 font-medium">琥珀色</mark>
              高亮，便于对照关键词。
            </p>
            <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center sm:justify-between">
              <div className="space-y-2">
                <Label className="text-xs text-[#1f1f1f]/55">结果排序</Label>
                <ToggleGroup
                  type="single"
                  value={sortMode}
                  onValueChange={(v) => {
                    if (v === 'vector' || v === 'literal') setSortMode(v);
                  }}
                  variant="outline"
                  size="sm"
                  className="justify-start"
                >
                  <ToggleGroupItem value="vector" aria-label="按向量相关度排序">
                    向量相关度
                  </ToggleGroupItem>
                  <ToggleGroupItem value="literal" aria-label="按字面重合优先排序">
                    字面重合优先
                  </ToggleGroupItem>
                </ToggleGroup>
              </div>
              <div className="flex items-center gap-2">
                <Switch
                  id="highlight-enabled"
                  checked={highlightEnabled}
                  onCheckedChange={setHighlightEnabled}
                />
                <Label htmlFor="highlight-enabled" className="text-sm text-[#1f1f1f]/70 cursor-pointer">
                  标出检索词重合
                </Label>
              </div>
            </div>
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
                onKeyDown={(e) => {
                  if (e.key === 'Enter') void handleSearch();
                }}
                className="rounded-2xl border border-gray-200 px-4 py-3 text-sm md:col-span-1"
              />
              <Button type="button" onClick={() => void handleSearch()} disabled={loading} className="md:w-auto">
                {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Search className="w-4 h-4 mr-2" />}
                检索
              </Button>
            </div>
          </div>

          <div className="space-y-4">
            {displayResults.map((r, idx) => {
              const meta = parseMeta(r.metadata);
              const relevance = Math.max(0, 1 - r.distance / 2);
              const matchCount = countHighlightMatches(r.content, highlightTerms);
              return (
                <div
                  key={r.id}
                  className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm"
                >
                  <div className="flex flex-col sm:flex-row sm:justify-between gap-2 mb-3">
                    <span className="text-xs font-mono text-[#1f1f1f]/50">#{r.segmentId}</span>
                    <span className="text-sm text-[#1f1f1f]/60 flex flex-wrap gap-x-2 gap-y-1 justify-end">
                      {highlightTerms.length > 0 && (
                        <span className="text-amber-800 bg-amber-50 px-2 py-0.5 rounded-full text-xs">
                          字面重合 {matchCount} 处
                        </span>
                      )}
                      <span>
                        相关度（近似） {(relevance * 100).toFixed(1)}% · distance {r.distance.toFixed(4)}
                      </span>
                    </span>
                  </div>
                  <p className="text-[#1f1f1f] mb-4 whitespace-pre-wrap leading-relaxed">
                    {highlightEnabled
                      ? renderHighlightedText(r.content, highlightTerms)
                      : r.content}
                  </p>
                  <div className="flex flex-wrap gap-2 text-sm mb-4">
                    {meta.url && (
                      <a
                        href={meta.url}
                        target="_blank"
                        rel="noreferrer"
                        className="text-[#3898ec] underline"
                      >
                        {meta.title
                          ? highlightEnabled
                            ? renderHighlightedText(meta.title, highlightTerms)
                            : meta.title
                          : meta.url}
                      </a>
                    )}
                    {meta.trustScore != null && (
                      <span className="px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-800">
                        可信度 {(meta.trustScore * 100).toFixed(0)}%
                      </span>
                    )}
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <Button
                      type="button"
                      variant="default"
                      size="sm"
                      className="bg-[#3898ec] hover:bg-[#0082f3]"
                      disabled={!canAppendToSlide}
                      onClick={() => appendToSlideCitation(r, idx)}
                    >
                      <Plus className="w-4 h-4 mr-1" />
                      追加到本页引用
                    </Button>
                    {!canAppendToSlide && (
                      <span className="text-xs text-[#1f1f1f]/45 self-center">
                        需从内容页「检索此条」进入才能回填
                      </span>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </section>
  );
}
