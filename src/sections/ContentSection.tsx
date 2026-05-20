import { useState, useEffect, useCallback } from 'react';
import { ArrowRight, ArrowLeft, Loader2, Edit2, RefreshCw, Plus, Trash2 } from 'lucide-react';
import { SlideTitleSortList } from '@/components/SlideTitleSortList';
import { FlowExitNav } from '@/components/FlowExitNav';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { regenerateSlide } from '@/lib/backend';
import { findSlideIndexByAnchor, reorderSlidesArray, slideAnchor } from '@/lib/slideOrder';
import {
  addProjectSlide,
  deleteProjectSlide,
  persistSlideBullets,
  persistSlideTitle,
  reorderProjectSlides,
} from '@/lib/slideStructure';
import type { SlideData } from '../App';

interface ContentSectionProps {
  projectId: number;
  slides: SlideData[];
  deckTitle: string;
  deckTheme: string;
  inputType: 'topic' | 'document';
  inputContent: string;
  onSlidesChange?: (slides: SlideData[]) => void;
  onConfirm: (slides: SlideData[], reportProgress?: (message: string) => void) => Promise<void>;
  onBack: () => void;
}

const ContentSection = ({
  projectId,
  slides: initialSlides,
  deckTitle,
  deckTheme,
  inputType,
  inputContent,
  onSlidesChange,
  onConfirm,
  onBack,
}: ContentSectionProps) => {
  const [slides, setSlides] = useState<SlideData[]>(initialSlides);

  useEffect(() => {
    setSlides(initialSlides);
    setCurrentSlideIndex((i) =>
      initialSlides.length === 0 ? 0 : Math.min(i, initialSlides.length - 1),
    );
  }, [initialSlides]);

  const applySlides = useCallback(
    (next: SlideData[]) => {
      setSlides(next);
      onSlidesChange?.(next);
    },
    [onSlidesChange],
  );

  const [currentSlideIndex, setCurrentSlideIndex] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [isStructuring, setIsStructuring] = useState(false);
  const [statusMessage, setStatusMessage] = useState('');
  const [editingContent, setEditingContent] = useState<string | null>(null);
  const [editValue, setEditValue] = useState('');

  const currentSlide = slides[currentSlideIndex];

  const patchCurrentSlide = (patch: Partial<SlideData>) => {
    const updatedSlides = [...slides];
    updatedSlides[currentSlideIndex] = { ...currentSlide, ...patch };
    applySlides(updatedSlides);
  };

  const handleRegenerateContent = async () => {
    if (!currentSlide?.slideId) {
      alert('当前页缺少后端 slideId，请刷新或重新同步大纲。');
      return;
    }
    setIsLoading(true);
    setStatusMessage('正在通过后端重新生成（含向量检索上下文）…');
    try {
      const result = await regenerateSlide(projectId, currentSlide.slideId, {
        inputType,
        inputContent,
      });
      patchCurrentSlide({
        content: result.content?.length ? result.content : currentSlide.content,
        pptContent: [],
        sources: result.sources?.length ? result.sources : currentSlide.sources,
      });
    } catch (error) {
      console.error(error);
      alert(`重新生成失败：${error instanceof Error ? error.message : '未知错误'}`);
    } finally {
      setIsLoading(false);
      setStatusMessage('');
    }
  };

  const handleEditStart = (content: string) => {
    setEditingContent(content);
    setEditValue(content);
  };

  const handleEditSave = async () => {
    if (editingContent === null || !currentSlide) return;
    const updatedSlides = [...slides];
    updatedSlides[currentSlideIndex] = {
      ...currentSlide,
      content: currentSlide.content.map((c) => (c === editingContent ? editValue : c)),
    };
    applySlides(updatedSlides);
    setEditingContent(null);
    try {
      await persistSlideBullets(projectId, updatedSlides[currentSlideIndex]);
    } catch {
      // 完成编辑时会再次批量保存
    }
  };

  const handleEditCancel = () => {
    setEditingContent(null);
    setEditValue('');
  };

  const handleTitleBlur = async () => {
    if (!currentSlide) return;
    try {
      await persistSlideTitle(projectId, currentSlide, currentSlide.title);
    } catch (e) {
      alert(e instanceof Error ? e.message : '标题保存失败');
    }
  };

  const handleAddBullet = async () => {
    if (!currentSlide) return;
    const nextContent = [...currentSlide.content, '新要点'];
    patchCurrentSlide({ content: nextContent });
    try {
      await persistSlideBullets(projectId, { ...currentSlide, content: nextContent });
    } catch {
      // ignore
    }
  };

  const handleRemoveBullet = async (index: number) => {
    if (!currentSlide) return;
    if (currentSlide.content.length <= 1) {
      alert('每页至少保留一条要点');
      return;
    }
    const nextContent = currentSlide.content.filter((_, i) => i !== index);
    const updated = { ...currentSlide, content: nextContent };
    const updatedSlides = [...slides];
    updatedSlides[currentSlideIndex] = updated;
    applySlides(updatedSlides);
    if (editingContent === currentSlide.content[index]) {
      setEditingContent(null);
    }
    try {
      await persistSlideBullets(projectId, updated);
    } catch {
      // ignore
    }
  };

  const handleAddPage = async () => {
    setIsStructuring(true);
    setStatusMessage('正在添加页面并生成讲稿…');
    try {
      const { slides: merged, newIndex } = await addProjectSlide(
        projectId,
        deckTitle,
        deckTheme,
        slides,
        { inputType, inputContent, regenerateContent: true },
      );
      applySlides(merged);
      setCurrentSlideIndex(newIndex);
    } catch (e) {
      alert(e instanceof Error ? e.message : '添加页面失败');
    } finally {
      setIsStructuring(false);
      setStatusMessage('');
    }
  };

  const handleReorderSlides = async (fromIndex: number, toIndex: number) => {
    if (fromIndex === toIndex) return;
    const anchor = slideAnchor(slides[currentSlideIndex]);
    const reordered = reorderSlidesArray(slides, fromIndex, toIndex);
    setIsStructuring(true);
    setStatusMessage('正在保存页面顺序…');
    try {
      const merged = await reorderProjectSlides(projectId, deckTitle, deckTheme, reordered);
      applySlides(merged);
      const idx = findSlideIndexByAnchor(merged, anchor);
      setCurrentSlideIndex(idx >= 0 ? idx : toIndex);
    } catch (e) {
      alert(e instanceof Error ? e.message : '调整顺序失败');
    } finally {
      setIsStructuring(false);
      setStatusMessage('');
    }
  };

  const handleDeletePage = async () => {
    if (!currentSlide) return;
    if (slides.length <= 1) {
      alert('至少需要保留一页');
      return;
    }
    if (!confirm(`确定删除第 ${currentSlideIndex + 1} 页「${currentSlide.title}」？`)) return;
    setIsStructuring(true);
    setStatusMessage('正在删除页面…');
    try {
      const merged = await deleteProjectSlide(
        projectId,
        deckTitle,
        deckTheme,
        slides,
        currentSlideIndex,
      );
      applySlides(merged);
      setCurrentSlideIndex((i) => Math.min(i, merged.length - 1));
    } catch (e) {
      alert(e instanceof Error ? e.message : '删除页面失败');
    } finally {
      setIsStructuring(false);
      setStatusMessage('');
    }
  };

  const handleConfirm = async () => {
    setIsLoading(true);
    setStatusMessage('正在保存并准备预览…');
    try {
      await onConfirm(slides, setStatusMessage);
    } finally {
      setIsLoading(false);
      setStatusMessage('');
    }
  };

  const busy = isLoading || isStructuring;

  if (!currentSlide) {
    return null;
  }

  return (
    <section className="min-h-screen pt-24 pb-16 bg-[#f3f3f3]">
      <div className="section-container">
        <div className="section-inner max-w-6xl">
          <FlowExitNav className="mb-4" />
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-6">
            <div>
              <h1 className="text-2xl sm:text-3xl font-bold text-[#1f1f1f] mb-2">内容编辑</h1>
              <p className="text-[#1f1f1f]/60">
                项目 ID {projectId} · 第 {currentSlideIndex + 1} / {slides.length} 页
              </p>
            </div>
            <div className="flex items-center gap-3 flex-wrap">
              <Button variant="outline" onClick={onBack} disabled={busy} className="border-gray-200 text-[#1f1f1f]">
                <ArrowLeft className="w-4 h-4 mr-2" />
                返回
              </Button>
              <Button
                onClick={handleConfirm}
                disabled={busy}
                className="bg-[#3898ec] hover:bg-[#0082f3] text-white disabled:opacity-60"
              >
                {isLoading ? (
                  <>
                    <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                    正在保存...
                  </>
                ) : (
                  <>
                    完成编辑
                    <ArrowRight className="w-4 h-4 ml-2" />
                  </>
                )}
              </Button>
            </div>
          </div>

          {statusMessage && (
            <div className="mb-6 rounded-2xl border border-[#3898ec]/20 bg-[#3898ec]/10 p-4 text-sm text-[#1f1f1f]">
              {statusMessage}
            </div>
          )}

          <div className="flex gap-1 mb-8">
            {slides.map((_, index) => (
              <button
                key={index}
                type="button"
                onClick={() => setCurrentSlideIndex(index)}
                className={`flex-1 h-2 rounded-full transition-all duration-300 ${
                  index === currentSlideIndex
                    ? 'bg-[#3898ec]'
                    : index < currentSlideIndex
                      ? 'bg-[#3898ec]/50'
                      : 'bg-gray-200'
                }`}
              />
            ))}
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="lg:col-span-1 flex flex-col gap-2">
              <p className="text-xs text-[#1f1f1f]/45 px-1">拖拽 ≡ 调整页面顺序</p>
              <SlideTitleSortList
                slides={slides}
                currentIndex={currentSlideIndex}
                onSelect={setCurrentSlideIndex}
                onReorder={(from, to) => void handleReorderSlides(from, to)}
                disabled={busy}
                layout="vertical"
              />
              <Button
                type="button"
                variant="outline"
                onClick={() => void handleAddPage()}
                disabled={busy}
                className="w-full border-dashed border-2 border-gray-300 text-[#1f1f1f]/60 hover:border-[#3898ec] hover:text-[#3898ec]"
              >
                <Plus className="w-4 h-4 mr-2" />
                添加页面
              </Button>
            </div>

            <div className="lg:col-span-2">
              <div className="bg-white rounded-2xl shadow-lg p-6">
                <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4 mb-4">
                  <div className="flex-1 min-w-0 space-y-2">
                    <label className="text-xs font-medium text-[#1f1f1f]/50">页面标题</label>
                    <Input
                      value={currentSlide.title}
                      onChange={(e) => patchCurrentSlide({ title: e.target.value })}
                      onBlur={() => void handleTitleBlur()}
                      disabled={busy}
                      className="text-lg font-semibold"
                    />
                  </div>
                  <div className="flex flex-wrap gap-2 shrink-0">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={handleRegenerateContent}
                      disabled={busy}
                      className="text-[#3898ec] border-[#3898ec]/30 hover:bg-[#3898ec]/10"
                    >
                      {isLoading ? (
                        <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                      ) : (
                        <RefreshCw className="w-4 h-4 mr-2" />
                      )}
                      {isLoading ? '生成中…' : '重新生成'}
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => void handleDeletePage()}
                      disabled={busy || slides.length <= 1}
                      className="text-red-600 border-red-200 hover:bg-red-50"
                    >
                      <Trash2 className="w-4 h-4 mr-2" />
                      删除本页
                    </Button>
                  </div>
                </div>

                <div className="space-y-4">
                  {currentSlide.content.map((content, index) => (
                    <div
                      key={`${index}-${content.slice(0, 24)}`}
                      className="border border-gray-200 rounded-xl p-4 hover:border-[#3898ec]/30 transition-colors"
                    >
                      {editingContent === content ? (
                        <div className="space-y-3">
                          <Textarea
                            value={editValue}
                            onChange={(e) => setEditValue(e.target.value)}
                            className="min-h-[100px] resize-none"
                            autoFocus
                          />
                          <div className="flex justify-end gap-2">
                            <button
                              type="button"
                              onClick={handleEditCancel}
                              className="px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
                            >
                              取消
                            </button>
                            <button
                              type="button"
                              onClick={() => void handleEditSave()}
                              className="px-3 py-1.5 text-sm bg-[#3898ec] text-white hover:bg-[#0082f3] rounded-lg transition-colors"
                            >
                              保存
                            </button>
                          </div>
                        </div>
                      ) : (
                        <div className="flex items-start justify-between gap-4">
                          <div className="flex-1">
                            <span className="inline-block w-6 h-6 bg-[#3898ec]/10 text-[#3898ec] rounded-full text-xs font-medium flex items-center justify-center mb-2">
                              {index + 1}
                            </span>
                            <p className="text-[#1f1f1f] leading-relaxed">{content}</p>
                          </div>
                          <div className="flex gap-1 shrink-0">
                            <button
                              type="button"
                              onClick={() => handleEditStart(content)}
                              disabled={busy}
                              className="p-2 hover:bg-gray-100 rounded-lg text-[#1f1f1f]/40 hover:text-[#3898ec] transition-colors disabled:opacity-40"
                            >
                              <Edit2 className="w-4 h-4" />
                            </button>
                            <button
                              type="button"
                              onClick={() => void handleRemoveBullet(index)}
                              disabled={busy || currentSlide.content.length <= 1}
                              className="p-2 hover:bg-red-50 rounded-lg text-[#1f1f1f]/40 hover:text-red-500 transition-colors disabled:opacity-40"
                              aria-label="删除要点"
                            >
                              <Trash2 className="w-4 h-4" />
                            </button>
                          </div>
                        </div>
                      )}
                    </div>
                  ))}
                </div>

                <Button
                  type="button"
                  variant="outline"
                  onClick={() => void handleAddBullet()}
                  disabled={busy}
                  className="w-full mt-4 border-dashed"
                >
                  <Plus className="w-4 h-4 mr-2" />
                  添加要点
                </Button>

                {currentSlide.sources && currentSlide.sources.length > 0 && (
                  <div className="mt-6 pt-6 border-t border-gray-200">
                    <h4 className="text-sm font-medium text-[#1f1f1f]/60 mb-2">引用来源</h4>
                    <ul className="text-sm text-[#1f1f1f]/80 space-y-1 list-disc pl-5">
                      {currentSlide.sources.map((s, i) => (
                        <li key={i}>{s}</li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>

              <div className="flex justify-between mt-6">
                <Button
                  variant="outline"
                  onClick={() => setCurrentSlideIndex(Math.max(0, currentSlideIndex - 1))}
                  disabled={currentSlideIndex === 0 || busy}
                  className="border-gray-200 text-[#1f1f1f]"
                >
                  <ArrowLeft className="w-4 h-4 mr-2" />
                  上一页
                </Button>
                <Button
                  variant="outline"
                  onClick={() =>
                    setCurrentSlideIndex(Math.min(slides.length - 1, currentSlideIndex + 1))
                  }
                  disabled={currentSlideIndex === slides.length - 1 || busy}
                  className="border-gray-200 text-[#1f1f1f]"
                >
                  下一页
                  <ArrowRight className="w-4 h-4 ml-2" />
                </Button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};

export default ContentSection;
