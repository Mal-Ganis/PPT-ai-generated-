import { useCallback, useEffect, useRef, useState } from 'react';
import {
  FileText,
  RotateCcw,
  ChevronLeft,
  ChevronRight,
  Presentation,
  Loader2,
  Sparkles,
  Plus,
  Trash2,
} from 'lucide-react';
import { SlideTitleSortList } from '@/components/SlideTitleSortList';
import { FlowExitNav } from '@/components/FlowExitNav';
import { WorkflowStepActions } from '@/components/WorkflowStepActions';
import type { WorkflowProgress, WorkflowStep } from '@/lib/workflowSteps';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { findSlideIndexByAnchor, reorderSlidesArray, slideAnchor } from '@/lib/slideOrder';
import {
  addProjectSlide,
  deleteProjectSlide,
  persistSlideTitle,
  reorderProjectSlides,
} from '@/lib/slideStructure';
import {
  extractPptDisplayContents,
  extractPptDisplayForSlide,
  fetchEvaluationReports,
  updateProjectSlide,
  type EvaluationReport,
} from '@/lib/backend';
import { SlideCitationEditor } from '@/components/SlideCitationEditor';
import {
  citationAttentionSummary,
  indicesNeedingCitationAttention,
  slideDataNeedsCitationAttention,
} from '@/lib/citationHints';
import { isStructuralSlideData } from '@/lib/structuralSlide';
import { persistSlideSources } from '@/lib/slideStructure';
import { bulletsToEditableText, editableTextToBullets } from '@/lib/bulletsText';
import {
  bulletsEqual,
  projectNeedsPptExtraction,
  slidesHaveReadyPreview,
} from '@/lib/pptExtraction';
import type { SlideData } from '../App';

interface PreviewSectionProps {
  projectId?: number | null;
  slides: SlideData[];
  title: string;
  deckTheme: string;
  inputType: 'topic' | 'document';
  inputContent: string;
  onSlidesChange: (slides: SlideData[]) => void;
  workflowProgress: WorkflowProgress;
  /** 已在内容页完成编辑并提炼过预览时，回到预览不再自动全量提炼 */
  previewUnlocked?: boolean;
  onGoToStep: (step: WorkflowStep) => void;
  onReset: () => void;
}

const PreviewSection = ({
  projectId,
  slides: initialSlides,
  title,
  deckTheme,
  inputType,
  inputContent,
  onSlidesChange,
  workflowProgress,
  previewUnlocked = false,
  onGoToStep,
  onReset,
}: PreviewSectionProps) => {
  const [slides, setSlides] = useState<SlideData[]>(initialSlides);
  const [currentSlideIndex, setCurrentSlideIndex] = useState(0);
  const [latestEval, setLatestEval] = useState<EvaluationReport | null>(null);
  const [extractMessage, setExtractMessage] = useState('');
  const [isExtracting, setIsExtracting] = useState(false);
  const [isStructuring, setIsStructuring] = useState(false);
  const [saveHint, setSaveHint] = useState('');
  const saveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const draftSaveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const slidesRef = useRef(slides);
  const currentSlideIndexRef = useRef(currentSlideIndex);
  const autoExtractStartedRef = useRef(false);
  const draftSkipCommitRef = useRef(true);

  const [scriptDraft, setScriptDraft] = useState('');
  const [pptDraft, setPptDraft] = useState('');

  currentSlideIndexRef.current = currentSlideIndex;

  const applySlides = useCallback((next: SlideData[]) => {
    setSlides(next);
    slidesRef.current = next;
  }, []);

  useEffect(() => {
    setSlides(initialSlides);
    slidesRef.current = initialSlides;
    setCurrentSlideIndex((i) =>
      initialSlides.length === 0 ? 0 : Math.min(i, initialSlides.length - 1),
    );
  }, [initialSlides]);

  useEffect(() => {
    onSlidesChange(slides);
  }, [slides, onSlidesChange]);

  const loadDraftFromSlide = useCallback((slide: SlideData | undefined) => {
    if (!slide) return;
    setScriptDraft(bulletsToEditableText(slide.content));
    setPptDraft(bulletsToEditableText(slide.pptContent));
  }, []);

  const persistCurrentSlide = useCallback(
    (slide: SlideData) => {
      if (projectId == null || slide.slideId == null) return;
      if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
      saveTimerRef.current = setTimeout(async () => {
        setSaveHint('保存中…');
        try {
          await updateProjectSlide(projectId, slide.slideId!, {
            title: slide.title,
            bullets: slide.content,
            pptBullets: slide.pptContent,
          });
          setSaveHint('已自动保存');
        } catch {
          setSaveHint('保存失败');
        }
      }, 1000);
    },
    [projectId],
  );

  const flushDraftsToSlides = useCallback(
    (scriptText: string, pptText: string) => {
      const idx = currentSlideIndexRef.current;
      const content = editableTextToBullets(scriptText);
      const pptContent = editableTextToBullets(pptText);
      setSlides((prev) => {
        const cur = prev[idx];
        if (!cur) return prev;
        const unchanged =
          bulletsEqual(cur.content, content) && bulletsEqual(cur.pptContent, pptContent);
        if (unchanged) return prev;
        const next = [...prev];
        next[idx] = { ...cur, content, pptContent };
        slidesRef.current = next;
        persistCurrentSlide(next[idx]);
        return next;
      });
    },
    [persistCurrentSlide],
  );

  useEffect(() => {
    slidesRef.current = slides;
  }, [slides]);

  useEffect(() => {
    draftSkipCommitRef.current = true;
    loadDraftFromSlide(slidesRef.current[currentSlideIndex]);
    const id = window.setTimeout(() => {
      draftSkipCommitRef.current = false;
    }, 0);
    return () => window.clearTimeout(id);
  }, [currentSlideIndex, loadDraftFromSlide]);

  useEffect(() => {
    if (draftSkipCommitRef.current) return;
    if (draftSaveTimerRef.current) clearTimeout(draftSaveTimerRef.current);
    draftSaveTimerRef.current = setTimeout(() => {
      flushDraftsToSlides(scriptDraft, pptDraft);
    }, 800);
    return () => {
      if (draftSaveTimerRef.current) clearTimeout(draftSaveTimerRef.current);
    };
  }, [scriptDraft, pptDraft, flushDraftsToSlides]);

  const currentSlide = slides[currentSlideIndex];
  const displayBullets = currentSlide?.pptContent ?? [];

  useEffect(() => {
    if (projectId == null) return;
    let cancelled = false;
    fetchEvaluationReports(projectId)
      .then((evs) => {
        if (!cancelled) setLatestEval(evs.length > 0 ? evs[0] : null);
      })
      .catch(() => {
        if (!cancelled) setLatestEval(null);
      });
    return () => {
      cancelled = true;
    };
  }, [projectId]);

  const runExtractAll = useCallback(
    async (force = false) => {
      if (projectId == null) return;
      setIsExtracting(true);
      setExtractMessage('正在提炼 PPT 投影文案…');
      try {
        const detail = await extractPptDisplayContents(projectId, {
          force,
          onProgress: (st) => {
            setExtractMessage(
              st.message?.trim() ||
                (st.totalSlides > 0
                  ? `提炼中 ${st.completedSlides} / ${st.totalSlides} 页…`
                  : '正在提炼…'),
            );
          },
        });
        const mapped = detail.slides
          .sort((a, b) => (a.position ?? 0) - (b.position ?? 0))
          .map((s, i) => {
            const prev = slidesRef.current[i];
            const script =
              s.bullets && s.bullets.length > 0 ? [...s.bullets] : prev?.content ?? [];
            const ppt =
              s.pptBullets && s.pptBullets.length > 0
                ? [...s.pptBullets]
                : prev?.pptContent?.length
                  ? [...prev.pptContent]
                  : [];
            return {
              id: s.id,
              slideId: s.id,
              chapter: s.chapter ?? undefined,
              title: s.title,
              content: script,
              pptContent: ppt,
              sources: s.sources ?? prev?.sources,
            };
          });
        applySlides(mapped);
        loadDraftFromSlide(mapped[currentSlideIndexRef.current]);
        setExtractMessage('提炼完成');
      } catch (e) {
        setExtractMessage(e instanceof Error ? e.message : '提炼失败');
      } finally {
        setIsExtracting(false);
      }
    },
    [projectId, applySlides, loadDraftFromSlide],
  );

  useEffect(() => {
    autoExtractStartedRef.current = false;
  }, [projectId]);

  /** 仅当从未提炼过 PPT 要点时自动提炼；已解锁预览或数据已就绪时不重复调用 */
  useEffect(() => {
    if (projectId == null || autoExtractStartedRef.current) return;
    if (previewUnlocked && slidesHaveReadyPreview(slidesRef.current)) return;
    if (!projectNeedsPptExtraction(slidesRef.current)) return;
    autoExtractStartedRef.current = true;
    void runExtractAll(false);
  }, [projectId, initialSlides, previewUnlocked, runExtractAll]);

  const updateSlideAt = (index: number, patch: Partial<SlideData>) => {
    setSlides((prev) => {
      const next = [...prev];
      next[index] = { ...next[index], ...patch };
      slidesRef.current = next;
      persistCurrentSlide(next[index]);
      return next;
    });
  };

  const handleReextractCurrent = async () => {
    if (projectId == null || currentSlide.slideId == null) return;
    setIsExtracting(true);
    setExtractMessage('正在提炼本页…');
    try {
      const ppt = await extractPptDisplayForSlide(projectId, currentSlide.slideId);
      updateSlideAt(currentSlideIndex, { pptContent: ppt });
      setPptDraft(bulletsToEditableText(ppt));
      setExtractMessage('本页提炼完成');
    } catch (e) {
      setExtractMessage(e instanceof Error ? e.message : '本页提炼失败');
    } finally {
      setIsExtracting(false);
    }
  };

  const goToPrevSlide = () => setCurrentSlideIndex((i) => Math.max(0, i - 1));
  const goToNextSlide = () =>
    setCurrentSlideIndex((i) => Math.min(slides.length - 1, i + 1));

  const busy = isExtracting || isStructuring;
  const citationPendingIndices = indicesNeedingCitationAttention(slides);

  const handleTitleBlur = async () => {
    if (!currentSlide || projectId == null) return;
    try {
      await persistSlideTitle(projectId, currentSlide, currentSlide.title);
      setSaveHint('标题已保存');
    } catch {
      setSaveHint('标题保存失败');
    }
  };

  const handleReorderSlides = async (fromIndex: number, toIndex: number) => {
    if (projectId == null || fromIndex === toIndex) return;
    const anchor = slideAnchor(slides[currentSlideIndex]);
    const reordered = reorderSlidesArray(slides, fromIndex, toIndex);
    setIsStructuring(true);
    setExtractMessage('正在保存页面顺序…');
    try {
      const merged = await reorderProjectSlides(projectId, title, deckTheme, reordered);
      const idx = findSlideIndexByAnchor(merged, anchor);
      const nextIndex = idx >= 0 ? idx : toIndex;
      applySlides(merged);
      setCurrentSlideIndex(nextIndex);
      loadDraftFromSlide(merged[nextIndex]);
      setExtractMessage('页面顺序已更新');
    } catch (e) {
      setExtractMessage(e instanceof Error ? e.message : '调整顺序失败');
    } finally {
      setIsStructuring(false);
    }
  };

  const handleAddPage = async () => {
    if (projectId == null) return;
    setIsStructuring(true);
    setExtractMessage('正在添加页面…');
    try {
      const { slides: merged, newIndex } = await addProjectSlide(
        projectId,
        title,
        deckTheme,
        slides,
        { inputType, inputContent, regenerateContent: false },
      );
      let resultSlides = merged;
      setCurrentSlideIndex(newIndex);
      const added = merged[newIndex];
      if (added?.slideId) {
        try {
          const ppt = await extractPptDisplayForSlide(projectId, added.slideId);
          const withPpt = [...merged];
          withPpt[newIndex] = { ...added, pptContent: ppt };
          resultSlides = withPpt;
        } catch {
          // 用户可手动填写 PPT 要点
        }
      }
      applySlides(resultSlides);
      loadDraftFromSlide(resultSlides[newIndex]);
      setExtractMessage('已添加新页面');
    } catch (e) {
      setExtractMessage(e instanceof Error ? e.message : '添加页面失败');
    } finally {
      setIsStructuring(false);
    }
  };

  const handleDeletePage = async () => {
    if (projectId == null || !currentSlide) return;
    if (slides.length <= 1) {
      alert('至少需要保留一页');
      return;
    }
    if (!confirm(`确定删除第 ${currentSlideIndex + 1} 页「${currentSlide.title}」？`)) return;
    setIsStructuring(true);
    setExtractMessage('正在删除页面…');
    try {
      const merged = await deleteProjectSlide(
        projectId,
        title,
        deckTheme,
        slides,
        currentSlideIndex,
      );
      const nextIndex = Math.min(currentSlideIndex, merged.length - 1);
      applySlides(merged);
      setCurrentSlideIndex(nextIndex);
      loadDraftFromSlide(merged[nextIndex]);
      setExtractMessage('已删除页面');
    } catch (e) {
      setExtractMessage(e instanceof Error ? e.message : '删除页面失败');
    } finally {
      setIsStructuring(false);
    }
  };

  const handleDownloadMarkdown = () => {
    let markdown = `# ${title}\n\n`;
    slides.forEach((slide, index) => {
      markdown += `## ${index + 1}. ${slide.title}\n\n### 讲稿\n\n`;
      slide.content.forEach((item) => {
        markdown += `- ${item}\n`;
      });
      markdown += `\n### PPT 投影要点\n\n`;
      slide.pptContent.forEach((item) => {
        markdown += `- ${item}\n`;
      });
      markdown += `\n---\n\n`;
    });
    const blob = new Blob([markdown], { type: 'text/markdown' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${title}.md`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <section className="min-h-screen pt-24 pb-16 bg-[#f3f3f3]">
      <div className="section-container">
        <div className="section-inner max-w-[1600px]">
          <FlowExitNav className="mb-4" />
          <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4 mb-6">
            <div>
              <h1 className="text-2xl sm:text-3xl font-bold text-[#1f1f1f] mb-2">预览与编辑</h1>
              <p className="text-[#1f1f1f]/60">
                左侧讲稿区加高便于阅读；右侧为 16:9 幻灯片预览（均可编辑，自动保存）
              </p>
              {latestEval && (
                <p className="text-sm text-[#1f1f1f]/70 mt-2">
                  自动总分{' '}
                  <span className="font-semibold text-[#3898ec]">
                    {latestEval.autoTotalScore != null
                      ? `${(latestEval.autoTotalScore / 20).toFixed(1)}/5`
                      : '—'}
                  </span>
                </p>
              )}
            </div>
            <div className="flex flex-col sm:flex-row sm:items-center gap-3 w-full lg:w-auto">
              <WorkflowStepActions
                currentStep="preview"
                progress={workflowProgress}
                onGoToStep={onGoToStep}
                busy={isExtracting || isStructuring}
              />
              <div className="flex flex-wrap items-center gap-2">
              <Button
                variant="outline"
                disabled={isExtracting || projectId == null}
                onClick={() => void runExtractAll(true)}
              >
                {isExtracting ? (
                  <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                ) : (
                  <Sparkles className="w-4 h-4 mr-2" />
                )}
                全部重新提炼
              </Button>
              <Button variant="outline" onClick={handleDownloadMarkdown}>
                <FileText className="w-4 h-4 mr-2" />
                导出 Markdown
              </Button>
              <Button
                variant="outline"
                onClick={() => {
                  if (confirm('确定要重新开始？')) onReset();
                }}
              >
                <RotateCcw className="w-4 h-4 mr-2" />
                重新开始
              </Button>
              </div>
            </div>
          </div>

          {(extractMessage || saveHint) && (
            <div className="mb-4 rounded-xl border border-[#3898ec]/20 bg-[#3898ec]/10 px-4 py-3 text-sm text-[#1f1f1f]">
              {extractMessage}
              {saveHint ? ` · ${saveHint}` : ''}
            </div>
          )}

          <div className="flex items-center justify-between gap-4 mb-4 flex-wrap">
            <div className="flex-1 min-w-0">
              <p className="text-xs text-[#1f1f1f]/45 mb-1.5">拖拽 ≡ 或标题标签可调整顺序</p>
              {citationPendingIndices.length > 0 && (
                <p className="text-xs text-amber-800 bg-amber-50 border border-amber-200 rounded-lg px-2 py-1.5 mb-2">
                  {citationPendingIndices.length} 页待补引用（封面/目录/Q&A 除外）· 琥珀色标签可点击
                </p>
              )}
              <SlideTitleSortList
                slides={slides}
                currentIndex={currentSlideIndex}
                onSelect={setCurrentSlideIndex}
                onReorder={(from, to) => void handleReorderSlides(from, to)}
                disabled={busy}
                layout="horizontal"
                needsCitationAttention={(_, index) => citationPendingIndices.includes(index)}
              />
            </div>
            <div className="flex items-center gap-2 shrink-0">
              <button
                type="button"
                disabled={currentSlideIndex === 0}
                onClick={goToPrevSlide}
                className="p-2 rounded-lg bg-white disabled:opacity-30 hover:bg-gray-50 transition-colors"
                aria-label="上一页"
              >
                <ChevronLeft className="w-5 h-5" />
              </button>
              <span className="text-sm text-[#1f1f1f]/60 tabular-nums">
                {currentSlideIndex + 1} / {slides.length}
              </span>
              <button
                type="button"
                disabled={currentSlideIndex >= slides.length - 1}
                onClick={goToNextSlide}
                className="p-2 rounded-lg bg-white disabled:opacity-30 hover:bg-gray-50 transition-colors"
                aria-label="下一页"
              >
                <ChevronRight className="w-5 h-5" />
              </button>
            </div>
          </div>

          {currentSlide && (
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-4 lg:gap-6 lg:items-start">
              {/* 左：讲稿区加高，便于阅读长文稿（高于右侧 16:9 预览） */}
              <div className="lg:col-span-5 flex flex-col">
                <div className="flex items-center gap-2 mb-2 text-sm font-semibold text-[#1f1f1f] shrink-0 min-h-[1.25rem]">
                  文稿与要点
                </div>
                <div className="mb-3 space-y-1.5">
                  <label className="text-xs font-medium text-[#1f1f1f]/50">页面标题</label>
                  <Input
                    value={currentSlide.title}
                    onChange={(e) =>
                      updateSlideAt(currentSlideIndex, { title: e.target.value })
                    }
                    onBlur={() => void handleTitleBlur()}
                    disabled={busy}
                    className="font-semibold"
                  />
                </div>
                <div className="flex flex-col gap-4 lg:min-h-[min(78vh,760px)]">
                  <div className="flex flex-col flex-[3] min-h-[220px] lg:min-h-[400px] bg-white rounded-2xl shadow-lg p-4">
                  <h2 className="text-sm font-semibold text-[#1f1f1f] mb-1">文稿内容</h2>
                  {currentSlide &&
                    slideDataNeedsCitationAttention(currentSlide) &&
                    citationAttentionSummary(currentSlide.content, currentSlide.sources) && (
                      <p className="text-xs text-amber-800 bg-amber-50 border border-amber-200 rounded-lg px-2 py-1.5 mb-2">
                        {citationAttentionSummary(currentSlide.content, currentSlide.sources)}
                      </p>
                    )}
                  <p className="text-xs text-[#1f1f1f]/50 mb-2">
                    回车可换行；每行一条要点（保存时空行会自动忽略）
                  </p>
                  <Textarea
                    className="flex-1 min-h-[180px] lg:min-h-[340px] resize-y text-sm leading-relaxed"
                    value={scriptDraft}
                    onChange={(e) => setScriptDraft(e.target.value)}
                    onBlur={() => flushDraftsToSlides(scriptDraft, pptDraft)}
                    disabled={busy}
                  />
                  </div>

                  <div className="flex flex-col flex-[2] min-h-[140px] lg:min-h-[200px] bg-white rounded-2xl shadow-lg p-4">
                    <div className="flex items-center justify-between gap-2 mb-1">
                    <h2 className="text-sm font-semibold text-[#1f1f1f]">PPT 投影要点</h2>
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={isExtracting || projectId == null}
                      onClick={() => void handleReextractCurrent()}
                    >
                      本页重新提炼
                    </Button>
                  </div>
                  <p className="text-xs text-[#1f1f1f]/50 mb-2">
                    回车可换行；每行一条投影要点
                  </p>
                  <Textarea
                    className="flex-1 min-h-[100px] lg:min-h-[140px] resize-y text-sm"
                    value={pptDraft}
                    onChange={(e) => setPptDraft(e.target.value)}
                    onBlur={() => flushDraftsToSlides(scriptDraft, pptDraft)}
                    disabled={busy}
                  />
                  </div>

                  {currentSlide && !isStructuralSlideData(currentSlide) && (
                    <SlideCitationEditor
                      className="mt-0 pt-4 border-t border-gray-100"
                      projectId={projectId!}
                      slideId={currentSlide.slideId}
                      content={currentSlide.content}
                      sources={currentSlide.sources}
                      disabled={busy}
                      onSourcesChange={(sources) => updateSlideAt(currentSlideIndex, { sources })}
                      onPersist={(sources) =>
                        persistSlideSources(projectId!, currentSlide, sources)
                      }
                    />
                  )}
                </div>
              </div>

              {/* 右：16:9 预览；滚动页面时保持可见 */}
              <div className="lg:col-span-7 flex flex-col lg:sticky lg:top-24 self-start">
                <div className="flex items-center gap-2 mb-2 text-sm font-semibold text-[#1f1f1f] shrink-0 min-h-[1.25rem]">
                  <Presentation className="w-4 h-4 text-[#3898ec]" />
                  幻灯片预览
                  <span className="text-xs font-normal text-[#1f1f1f]/45">16:9</span>
                </div>
                <div className="bg-white rounded-2xl shadow-lg p-3 sm:p-4">
                  <div className="relative w-full aspect-video overflow-hidden rounded-lg bg-gradient-to-br from-[#1f1f1f] to-[#2a2a2a]">
                    <div className="absolute inset-0 flex flex-col justify-center p-6 sm:p-8 lg:p-10 overflow-y-auto">
                      <h2 className="text-xl sm:text-2xl lg:text-3xl font-bold text-white mb-4 sm:mb-6 leading-tight shrink-0">
                        {currentSlide.title}
                      </h2>
                      {displayBullets.length > 0 ? (
                        <ul className="space-y-2 sm:space-y-3 min-h-0">
                          {displayBullets.map((item, idx) => (
                            <li key={idx} className="flex items-start gap-2 text-white/90">
                              <span className="w-2 h-2 bg-[#3898ec] rounded-full mt-2 shrink-0" />
                              <span className="text-sm sm:text-base lg:text-lg leading-snug">{item}</span>
                            </li>
                          ))}
                        </ul>
                      ) : (
                        <p className="text-white/50 text-sm">
                          {isExtracting
                            ? '正在由 DeepSeek 提炼投影要点…'
                            : '左下方暂无 PPT 要点，可点击「全部重新提炼」或手动填写'}
                        </p>
                      )}
                    </div>
                  </div>
                </div>
                <div className="flex flex-wrap items-center justify-center gap-3 mt-3 px-1">
                  <Button
                    type="button"
                    variant="outline"
                    size="icon"
                    className="h-10 w-10 rounded-xl shrink-0"
                    disabled={currentSlideIndex === 0 || busy}
                    onClick={goToPrevSlide}
                    aria-label="上一页"
                  >
                    <ChevronLeft className="w-5 h-5" />
                  </Button>
                  <span className="text-sm text-[#1f1f1f]/70 tabular-nums min-w-[5.5rem] text-center">
                    第 {currentSlideIndex + 1} / {slides.length} 页
                  </span>
                  <Button
                    type="button"
                    variant="outline"
                    size="icon"
                    className="h-10 w-10 rounded-xl shrink-0"
                    disabled={currentSlideIndex >= slides.length - 1 || busy}
                    onClick={goToNextSlide}
                    aria-label="下一页"
                  >
                    <ChevronRight className="w-5 h-5" />
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={busy || projectId == null}
                    onClick={() => void handleAddPage()}
                    className="shrink-0"
                  >
                    <Plus className="w-4 h-4 mr-1" />
                    添加页面
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={busy || projectId == null || slides.length <= 1}
                    onClick={() => void handleDeletePage()}
                    className="shrink-0 text-red-600 border-red-200 hover:bg-red-50"
                  >
                    <Trash2 className="w-4 h-4 mr-1" />
                    删除页面
                  </Button>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </section>
  );
};

export default PreviewSection;
