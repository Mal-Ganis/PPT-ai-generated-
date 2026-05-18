import { useState, useEffect } from 'react';
import { ArrowRight, ArrowLeft, Loader2, Edit2, RefreshCw } from 'lucide-react';
import { FlowExitNav } from '@/components/FlowExitNav';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { regenerateSlide } from '@/lib/backend';
import type { SlideData } from '../App';

interface ContentSectionProps {
  projectId: number;
  slides: SlideData[];
  inputType: 'topic' | 'document';
  inputContent: string;
  onConfirm: (slides: SlideData[], reportProgress?: (message: string) => void) => Promise<void>;
  onBack: () => void;
}

const ContentSection = ({
  projectId,
  slides: initialSlides,
  inputType,
  inputContent,
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

  const [currentSlideIndex, setCurrentSlideIndex] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [statusMessage, setStatusMessage] = useState('');
  const [editingContent, setEditingContent] = useState<string | null>(null);
  const [editValue, setEditValue] = useState('');

  const currentSlide = slides[currentSlideIndex];

  const handleRegenerateContent = async () => {
    if (!currentSlide.slideId) {
      alert('当前页缺少后端 slideId，请从大纲重新生成项目。');
      return;
    }
    setIsLoading(true);
    setStatusMessage('正在通过后端重新生成（含向量检索上下文）…');
    try {
      const result = await regenerateSlide(projectId, currentSlide.slideId, {
        inputType,
        inputContent,
      });
      const updatedSlides = [...slides];
      updatedSlides[currentSlideIndex] = {
        ...currentSlide,
        content: result.content?.length ? result.content : currentSlide.content,
        pptContent: [],
        sources: result.sources?.length ? result.sources : currentSlide.sources,
      };
      setSlides(updatedSlides);
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

  const handleEditSave = () => {
    if (editingContent !== null) {
      const updatedSlides = [...slides];
      updatedSlides[currentSlideIndex] = {
        ...currentSlide,
        content: currentSlide.content.map((c) => (c === editingContent ? editValue : c)),
      };
      setSlides(updatedSlides);
      setEditingContent(null);
    }
  };

  const handleEditCancel = () => {
    setEditingContent(null);
    setEditValue('');
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

  return (
    <section className="min-h-screen pt-24 pb-16 bg-[#f3f3f3]">
      <div className="section-container">
        <div className="section-inner max-w-6xl">
          <FlowExitNav className="mb-4" />
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-6">
            <div>
              <h1 className="text-2xl sm:text-3xl font-bold text-[#1f1f1f] mb-2">内容编辑</h1>
              <p className="text-[#1f1f1f]/60">
                项目 ID {projectId} · 第 {currentSlideIndex + 1} / {slides.length} 页：{currentSlide.title}
              </p>
            </div>
            <div className="flex items-center gap-3">
              <Button variant="outline" onClick={onBack} className="border-gray-200 text-[#1f1f1f]">
                <ArrowLeft className="w-4 h-4 mr-2" />
                返回
              </Button>
              <Button
                onClick={handleConfirm}
                disabled={isLoading}
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
            <div className="lg:col-span-1 space-y-2">
              {slides.map((slide, index) => (
                <button
                  key={slide.slideId ?? slide.id}
                  type="button"
                  onClick={() => setCurrentSlideIndex(index)}
                  className={`w-full text-left p-4 rounded-xl transition-all duration-300 ${
                    index === currentSlideIndex
                      ? 'bg-[#3898ec] text-white shadow-lg'
                      : 'bg-white hover:bg-gray-50 text-[#1f1f1f]'
                  }`}
                >
                  <div className="flex items-center gap-3">
                    <span
                      className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-medium ${
                        index === currentSlideIndex
                          ? 'bg-white text-[#3898ec]'
                          : 'bg-[#3898ec]/10 text-[#3898ec]'
                      }`}
                    >
                      {index + 1}
                    </span>
                    <span className="font-medium truncate">{slide.title}</span>
                  </div>
                </button>
              ))}
            </div>

            <div className="lg:col-span-2">
              <div className="bg-white rounded-2xl shadow-lg p-6">
                <div className="flex items-center justify-between mb-6">
                  <h2 className="text-xl font-bold text-[#1f1f1f]">{currentSlide.title}</h2>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={handleRegenerateContent}
                    disabled={isLoading}
                    className="text-[#3898ec] border-[#3898ec]/30 hover:bg-[#3898ec]/10"
                  >
                    {isLoading ? (
                      <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                    ) : (
                      <RefreshCw className="w-4 h-4 mr-2" />
                    )}
                    {isLoading ? '生成中…' : '重新生成'}
                  </Button>
                </div>

                <div className="space-y-4">
                  {currentSlide.content.map((content, index) => (
                    <div
                      key={index}
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
                              onClick={handleEditSave}
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
                          <button
                            type="button"
                            onClick={() => handleEditStart(content)}
                            className="p-2 hover:bg-gray-100 rounded-lg text-[#1f1f1f]/40 hover:text-[#3898ec] transition-colors"
                          >
                            <Edit2 className="w-4 h-4" />
                          </button>
                        </div>
                      )}
                    </div>
                  ))}
                </div>

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
                  disabled={currentSlideIndex === 0}
                  className="border-gray-200 text-[#1f1f1f]"
                >
                  <ArrowLeft className="w-4 h-4 mr-2" />
                  上一页
                </Button>
                <Button
                  variant="outline"
                  onClick={() => setCurrentSlideIndex(Math.min(slides.length - 1, currentSlideIndex + 1))}
                  disabled={currentSlideIndex === slides.length - 1}
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
