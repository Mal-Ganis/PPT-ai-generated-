import { useCallback, useEffect, useRef, useState } from 'react';
import {
  FileText,
  ArrowLeft,
  RotateCcw,
  ChevronLeft,
  ChevronRight,
  Presentation,
  Loader2,
  Sparkles,
} from 'lucide-react';
import { FlowExitNav } from '@/components/FlowExitNav';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import {
  extractPptDisplayContents,
  extractPptDisplayForSlide,
  fetchEvaluationReports,
  updateProjectSlide,
  type EvaluationReport,
} from '@/lib/backend';
import type { SlideData } from '../App';

interface PreviewSectionProps {
  projectId?: number | null;
  slides: SlideData[];
  title: string;
  onSlidesChange: (slides: SlideData[]) => void;
  onReset: () => void;
  onBack: () => void;
}

function linesToBullets(text: string): string[] {
  return text
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean);
}

function bulletsToLines(bullets: string[]): string {
  return bullets.join('\n');
}

function bulletsEqual(a: string[], b: string[]): boolean {
  if (a.length !== b.length) return false;
  return a.every((line, i) => line.trim() === b[i]?.trim());
}

/** 讲稿已有内容但尚未提炼出独立的 PPT 要点 */
function slideNeedsPptExtraction(slide: SlideData): boolean {
  if (slide.content.length === 0) return false;
  if (slide.pptContent.length === 0) return true;
  return bulletsEqual(slide.pptContent, slide.content);
}

function projectNeedsPptExtraction(slides: SlideData[]): boolean {
  return slides.some(slideNeedsPptExtraction);
}

const PreviewSection = ({
  projectId,
  slides: initialSlides,
  title,
  onSlidesChange,
  onReset,
  onBack,
}: PreviewSectionProps) => {
  const [slides, setSlides] = useState<SlideData[]>(initialSlides);
  const [currentSlideIndex, setCurrentSlideIndex] = useState(0);
  const [latestEval, setLatestEval] = useState<EvaluationReport | null>(null);
  const [extractMessage, setExtractMessage] = useState('');
  const [isExtracting, setIsExtracting] = useState(false);
  const [saveHint, setSaveHint] = useState('');
  const saveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const slidesRef = useRef(slides);
  const autoExtractStartedRef = useRef(false);

  useEffect(() => {
    setSlides(initialSlides);
    slidesRef.current = initialSlides;
  }, [initialSlides]);

  useEffect(() => {
    slidesRef.current = slides;
    onSlidesChange(slides);
  }, [slides, onSlidesChange]);

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
        setSlides(mapped);
        setExtractMessage('提炼完成');
      } catch (e) {
        setExtractMessage(e instanceof Error ? e.message : '提炼失败');
      } finally {
        setIsExtracting(false);
      }
    },
    [projectId],
  );

  useEffect(() => {
    if (projectId == null || autoExtractStartedRef.current) return;
    if (!projectNeedsPptExtraction(slidesRef.current)) return;
    autoExtractStartedRef.current = true;
    void runExtractAll(true);
  }, [projectId, runExtractAll]);

  const persistCurrentSlide = useCallback(
    (slide: SlideData) => {
      if (projectId == null || slide.slideId == null) return;
      if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
      saveTimerRef.current = setTimeout(async () => {
        setSaveHint('保存中…');
        try {
          await updateProjectSlide(projectId, slide.slideId!, {
            bullets: slide.content,
            pptBullets: slide.pptContent,
          });
          setSaveHint('已自动保存');
        } catch {
          setSaveHint('保存失败');
        }
      }, 700);
    },
    [projectId],
  );

  const updateSlideAt = (index: number, patch: Partial<SlideData>) => {
    setSlides((prev) => {
      const next = [...prev];
      next[index] = { ...next[index], ...patch };
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
            <div className="flex flex-wrap items-center gap-2">
              <Button variant="outline" onClick={onBack}>
                <ArrowLeft className="w-4 h-4 mr-2" />
                返回编辑
              </Button>
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

          {(extractMessage || saveHint) && (
            <div className="mb-4 rounded-xl border border-[#3898ec]/20 bg-[#3898ec]/10 px-4 py-3 text-sm text-[#1f1f1f]">
              {extractMessage}
              {saveHint ? ` · ${saveHint}` : ''}
            </div>
          )}

          <div className="flex items-center justify-between gap-4 mb-4">
            <div className="flex items-center gap-2 overflow-x-auto pb-1">
              {slides.map((slide, index) => (
                <button
                  key={slide.slideId ?? slide.id}
                  type="button"
                  onClick={() => setCurrentSlideIndex(index)}
                  className={`shrink-0 px-3 py-1.5 rounded-lg text-sm transition-all ${
                    index === currentSlideIndex
                      ? 'bg-[#3898ec] text-white'
                      : 'bg-white text-[#1f1f1f]/70 hover:bg-gray-50'
                  }`}
                >
                  {index + 1}. {slide.title}
                </button>
              ))}
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
                <div className="flex flex-col gap-4 lg:min-h-[min(78vh,760px)]">
                  <div className="flex flex-col flex-[3] min-h-[220px] lg:min-h-[400px] bg-white rounded-2xl shadow-lg p-4">
                  <h2 className="text-sm font-semibold text-[#1f1f1f] mb-1">文稿内容</h2>
                  <p className="text-xs text-[#1f1f1f]/50 mb-2">可含衔接语、完整句，供演讲使用</p>
                  <Textarea
                    className="flex-1 min-h-[180px] lg:min-h-[340px] resize-y text-sm leading-relaxed"
                    value={bulletsToLines(currentSlide.content)}
                    onChange={(e) =>
                      updateSlideAt(currentSlideIndex, { content: linesToBullets(e.target.value) })
                    }
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
                  <p className="text-xs text-[#1f1f1f]/50 mb-2">短句、关键词，适合打在幻灯片上</p>
                  <Textarea
                    className="flex-1 min-h-[100px] lg:min-h-[140px] resize-y text-sm"
                    value={bulletsToLines(currentSlide.pptContent)}
                    onChange={(e) =>
                      updateSlideAt(currentSlideIndex, { pptContent: linesToBullets(e.target.value) })
                    }
                  />
                  </div>
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
                <div className="flex items-center justify-center gap-4 mt-3 px-1">
                  <Button
                    type="button"
                    variant="outline"
                    size="icon"
                    className="h-10 w-10 rounded-xl shrink-0"
                    disabled={currentSlideIndex === 0}
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
                    disabled={currentSlideIndex >= slides.length - 1}
                    onClick={goToNextSlide}
                    aria-label="下一页"
                  >
                    <ChevronRight className="w-5 h-5" />
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
