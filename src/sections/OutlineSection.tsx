import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, Plus, Trash2, GripVertical, Sparkles, Loader2 } from 'lucide-react';
import { FlowExitNav } from '@/components/FlowExitNav';
import { WorkflowStepActions } from '@/components/WorkflowStepActions';
import { Button } from '@/components/ui/button';
import type { WorkflowProgress, WorkflowStep } from '@/lib/workflowSteps';
import { reorderSlidesArray } from '@/lib/slideOrder';
import { cn } from '@/lib/utils';
import type { OutlineData, SlideData } from '../App';

interface OutlineSectionProps {
  projectId?: number | null;
  outline: OutlineData;
  workflowProgress: WorkflowProgress;
  onGoToStep: (step: WorkflowStep) => void;
  onSlidesChange: (slides: SlideData[]) => void;
  onConfirm: (slides: SlideData[], reportProgress?: (message: string) => void) => Promise<void>;
}

const OutlineSection = ({
  projectId,
  outline,
  workflowProgress,
  onGoToStep,
  onSlidesChange,
  onConfirm,
}: OutlineSectionProps) => {
  const [slides, setSlides] = useState<SlideData[]>(outline.slides);

  useEffect(() => {
    setSlides(outline.slides);
  }, [outline]);

  useEffect(() => {
    onSlidesChange(slides);
  }, [slides, onSlidesChange]);

  const structureHints = useMemo(() => {
    const titles = slides.map((s) => s.title.toLowerCase());
    const hints: string[] = [];
    const hasCover = titles.some((t) => /封面|首页|标题|opening/.test(t));
    const hasToc = titles.some((t) => /目录|纲要|contents/.test(t));
    const hasSummary = titles.some((t) => /总结|致谢|展望|结论|结束/.test(t));
    if (!hasCover) hints.push('建议补充明确标注的封面或标题页');
    if (!hasToc && slides.length > 4) hints.push('页数较多时建议加入目录页以便导航');
    if (!hasSummary) hints.push('建议加入总结或致谢页以形成闭环');
    if (slides.length < 5) hints.push('演示通常至少包含 5–8 页结构');
    const minutes = outline.presentationDurationMinutes;
    if (minutes != null) {
      const maxSuggested =
        minutes <= 8 ? 8 : minutes <= 15 ? 11 : minutes <= 25 ? 14 : 18;
      if (slides.length > maxSuggested) {
        hints.push(
          `当前 ${slides.length} 页，对 ${minutes} 分钟演讲可能偏多，建议删减次要页`,
        );
      }
    }
    return hints;
  }, [slides, outline.presentationDurationMinutes]);
  const [isLoading, setIsLoading] = useState(false);
  const [statusMessage, setStatusMessage] = useState('');
  const [dragFromIndex, setDragFromIndex] = useState<number | null>(null);
  const [dropTargetIndex, setDropTargetIndex] = useState<number | null>(null);

  const handleAddSlide = () => {
    const newSlide: SlideData = {
      id: Date.now(),
      title: '新页面',
      content: ['要点1', '要点2'],
      pptContent: [],
    };
    setSlides([...slides, newSlide]);
  };

  const handleDeleteSlide = (id: number) => {
    if (slides.length <= 1) {
      alert('至少需要保留一页');
      return;
    }
    setSlides(slides.filter((s) => s.id !== id));
  };

  const handleMoveSlide = (index: number, direction: 'up' | 'down') => {
    if (direction === 'up' && index === 0) return;
    if (direction === 'down' && index === slides.length - 1) return;
    const targetIndex = direction === 'up' ? index - 1 : index + 1;
    setSlides(reorderSlidesArray(slides, index, targetIndex));
  };

  const finishOutlineDrag = () => {
    setDragFromIndex(null);
    setDropTargetIndex(null);
  };

  const handleOutlineDrop = (toIndex: number) => {
    if (dragFromIndex == null || dragFromIndex === toIndex) {
      finishOutlineDrag();
      return;
    }
    setSlides(reorderSlidesArray(slides, dragFromIndex, toIndex));
    finishOutlineDrag();
  };

  const handleConfirm = async () => {
    setIsLoading(true);
    setStatusMessage('AI 正在全力工作，正在生成幻灯片内容...');
    try {
      await onConfirm(slides, setStatusMessage);
    } finally {
      setIsLoading(false);
      setStatusMessage('');
    }
  };

  return (
    <section className="min-h-screen pt-24 pb-16 bg-[#f3f3f3]">
      <div className="section-container">
        <div className="section-inner max-w-5xl">
          <FlowExitNav className="mb-4" />
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-8">
            <div>
              <h1 className="text-2xl sm:text-3xl font-bold text-[#1f1f1f] mb-2">大纲编辑</h1>
              <p className="text-[#1f1f1f]/60">主题：{outline.title}</p>
              {outline.presentationDurationMinutes != null && (
                <p className="text-sm text-[#3898ec] mt-1">
                  目标演讲时长：{outline.presentationDurationMinutes} 分钟（正文将按此时长控制密度）
                </p>
              )}
            </div>
            <WorkflowStepActions
              currentStep="outline"
              progress={workflowProgress}
              onGoToStep={onGoToStep}
              busy={isLoading}
              primaryAction={
                <Button
                  onClick={() => void handleConfirm()}
                  disabled={isLoading}
                  className="bg-[#3898ec] hover:bg-[#0082f3] text-white"
                >
                  {isLoading ? (
                    <>
                      <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                      AI 正在全力工作...
                    </>
                  ) : workflowProgress.hasContent ? (
                    <>
                      <Sparkles className="w-4 h-4 mr-2" />
                      重新生成内容
                      <ArrowRight className="w-4 h-4 ml-2" />
                    </>
                  ) : (
                    <>
                      <Sparkles className="w-4 h-4 mr-2" />
                      生成内容
                      <ArrowRight className="w-4 h-4 ml-2" />
                    </>
                  )}
                </Button>
              }
            />
          </div>

          {statusMessage && (
            <div className="mb-6 rounded-2xl border border-[#3898ec]/20 bg-[#3898ec]/10 p-4 text-sm text-[#1f1f1f]">
              {statusMessage}
            </div>
          )}

          {structureHints.length > 0 && (
            <div className="mb-6 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950">
              <p className="font-medium mb-2">结构提示（逻辑校验）</p>
              <ul className="list-disc pl-5 space-y-1">
                {structureHints.map((h, i) => (
                  <li key={i}>{h}</li>
                ))}
              </ul>
            </div>
          )}

          <div className="space-y-4 mb-8">
            {slides.map((slide, index) => (
              <div
                key={slide.id}
                className={cn(
                  'bg-white rounded-xl shadow-md hover:shadow-lg transition-shadow duration-300 p-5',
                  dropTargetIndex === index &&
                    dragFromIndex != null &&
                    dragFromIndex !== index &&
                    'ring-2 ring-[#3898ec] ring-offset-2',
                )}
                onDragOver={(e) => {
                  if (dragFromIndex == null) return;
                  e.preventDefault();
                  e.dataTransfer.dropEffect = 'move';
                  setDropTargetIndex(index);
                }}
                onDragLeave={() => {
                  if (dropTargetIndex === index) setDropTargetIndex(null);
                }}
                onDrop={(e) => {
                  e.preventDefault();
                  handleOutlineDrop(index);
                }}
              >
                <div className="flex items-start gap-4">
                  <div className="flex flex-col gap-1 pt-1 items-center">
                    <button
                      type="button"
                      onClick={() => handleMoveSlide(index, 'up')}
                      disabled={index === 0}
                      className="p-1 hover:bg-gray-100 rounded disabled:opacity-30 disabled:cursor-not-allowed"
                    >
                      <svg className="w-4 h-4 text-[#1f1f1f]/40" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 15l7-7 7 7" />
                      </svg>
                    </button>
                    <span
                      draggable
                      onDragStart={(e) => {
                        setDragFromIndex(index);
                        e.dataTransfer.effectAllowed = 'move';
                        e.dataTransfer.setData('text/plain', String(index));
                      }}
                      onDragEnd={finishOutlineDrag}
                      className="p-1 cursor-grab active:cursor-grabbing text-[#1f1f1f]/30 hover:text-[#3898ec]"
                      title="拖拽以调整顺序"
                    >
                      <GripVertical className="w-4 h-4" />
                    </span>
                    <button
                      type="button"
                      onClick={() => handleMoveSlide(index, 'down')}
                      disabled={index === slides.length - 1}
                      className="p-1 hover:bg-gray-100 rounded disabled:opacity-30 disabled:cursor-not-allowed"
                    >
                      <svg className="w-4 h-4 text-[#1f1f1f]/40" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                      </svg>
                    </button>
                  </div>

                  <div className="w-10 h-10 bg-[#3898ec]/10 rounded-lg flex items-center justify-center flex-shrink-0">
                    <span className="text-sm font-bold text-[#3898ec]">{index + 1}</span>
                  </div>

                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      {slide.chapter ? (
                        <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-[#e7f0ff] text-[#0f5abb] shrink-0">
                          {slide.chapter}
                        </span>
                      ) : null}
                      <h3 className="text-lg font-semibold text-[#1f1f1f]">{slide.title}</h3>
                      {projectId != null && slide.slideId != null && (
                        <Link
                          to={`/project/${projectId}/slide/${slide.slideId}`}
                          className="text-sm font-medium text-[#3898ec] hover:underline shrink-0"
                        >
                          单页详情（编辑）
                        </Link>
                      )}
                    </div>

                    <div className="mt-2 flex flex-wrap gap-2">
                      {slide.content.map((item, idx) => (
                        <span
                          key={idx}
                          className="px-3 py-1 bg-[#f3f3f3] text-[#1f1f1f]/70 rounded-full text-sm"
                        >
                          {item}
                        </span>
                      ))}
                    </div>
                  </div>

                  <button
                    type="button"
                    onClick={() => handleDeleteSlide(slide.id)}
                    className="p-2 hover:bg-red-50 text-[#1f1f1f]/40 hover:text-red-500 rounded-lg transition-colors"
                  >
                    <Trash2 className="w-5 h-5" />
                  </button>
                </div>
              </div>
            ))}
          </div>

          <button
            type="button"
            onClick={handleAddSlide}
            className="w-full py-4 border-2 border-dashed border-gray-300 hover:border-[#3898ec] rounded-xl text-[#1f1f1f]/50 hover:text-[#3898ec] transition-all duration-300 flex items-center justify-center gap-2"
          >
            <Plus className="w-5 h-5" />
            添加页面
          </button>

          <div className="mt-8 p-4 bg-[#3898ec]/5 rounded-xl">
            <p className="text-sm text-[#3898ec]">
              提示：拖拽左侧 ≡ 调整顺序；可增删页。使用顶部「上一步 / 下一步」在输入、大纲、内容之间切换；标题与要点可在「单页详情」中编辑。
            </p>
          </div>
        </div>
      </div>
    </section>
  );
};

export default OutlineSection;
