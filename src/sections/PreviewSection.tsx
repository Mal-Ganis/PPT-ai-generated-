import { useState } from 'react';
import { FileText, ArrowLeft, RotateCcw, ChevronLeft, ChevronRight, Presentation } from 'lucide-react';
import { Button } from '@/components/ui/button';
import type { SlideData } from '../App';

interface PreviewSectionProps {
  slides: SlideData[];
  title: string;
  onReset: () => void;
  onBack: () => void;
}

const PreviewSection = ({ slides, title, onReset, onBack }: PreviewSectionProps) => {
  const [currentSlideIndex, setCurrentSlideIndex] = useState(0);
  const [viewMode, setViewMode] = useState<'preview' | 'list'>('preview');

  const currentSlide = slides[currentSlideIndex];

  const handleDownloadMarkdown = () => {
    let markdown = `# ${title}\n\n`;
    slides.forEach((slide, index) => {
      markdown += `## ${index + 1}. ${slide.title}\n\n`;
      slide.content.forEach(item => {
        markdown += `- ${item}\n`;
      });
      markdown += `\n**演讲备注：** ${slide.notes}\n\n---\n\n`;
    });

    const blob = new Blob([markdown], { type: 'text/markdown' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${title}.md`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  return (
    <section className="min-h-screen pt-24 pb-16 bg-[#f3f3f3]">
      <div className="section-container">
        <div className="section-inner max-w-6xl">
          {/* Header */}
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-6">
            <div>
              <h1 className="text-2xl sm:text-3xl font-bold text-[#1f1f1f] mb-2">
                PPT 预览
              </h1>
              <p className="text-[#1f1f1f]/60">
                共 {slides.length} 页：{title}
              </p>
            </div>
            <div className="flex items-center gap-3">
              <Button
                variant="outline"
                onClick={onBack}
                className="border-gray-200 text-[#1f1f1f]"
              >
                <ArrowLeft className="w-4 h-4 mr-2" />
                返回编辑
              </Button>
              <Button
                onClick={() => {
                  if (confirm('确定要重新开始？当前进度将丢失。')) {
                    onReset();
                  }
                }}
                variant="outline"
                className="border-gray-200 text-[#1f1f1f]"
              >
                <RotateCcw className="w-4 h-4 mr-2" />
                重新开始
              </Button>
              <Button
                onClick={handleDownloadMarkdown}
                variant="outline"
                className="border-gray-200 text-[#1f1f1f]"
              >
                <FileText className="w-4 h-4 mr-2" />
                导出 Markdown
              </Button>
            </div>
          </div>

          {/* View Mode Toggle */}
          <div className="flex items-center gap-2 mb-6">
            <button
              onClick={() => setViewMode('preview')}
              className={`flex items-center gap-2 px-4 py-2 rounded-lg transition-all duration-300 ${
                viewMode === 'preview'
                  ? 'bg-[#3898ec] text-white'
                  : 'bg-white text-[#1f1f1f]/60 hover:bg-gray-50'
              }`}
            >
              <Presentation className="w-4 h-4" />
              幻灯片预览
            </button>
            <button
              onClick={() => setViewMode('list')}
              className={`flex items-center gap-2 px-4 py-2 rounded-lg transition-all duration-300 ${
                viewMode === 'list'
                  ? 'bg-[#3898ec] text-white'
                  : 'bg-white text-[#1f1f1f]/60 hover:bg-gray-50'
              }`}
            >
              <FileText className="w-4 h-4" />
              列表视图
            </button>
          </div>

          {viewMode === 'preview' ? (
            <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
              {/* Slide Thumbnails */}
              <div className="lg:col-span-1 space-y-2 max-h-[600px] overflow-y-auto">
                {slides.map((slide, index) => (
                  <button
                    key={slide.id}
                    onClick={() => setCurrentSlideIndex(index)}
                    className={`w-full text-left p-3 rounded-xl transition-all duration-300 ${
                      index === currentSlideIndex
                        ? 'bg-[#3898ec] text-white shadow-lg'
                        : 'bg-white hover:bg-gray-50 text-[#1f1f1f]'
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      <span className={`w-5 h-5 rounded-full flex items-center justify-center text-xs font-medium ${
                        index === currentSlideIndex
                          ? 'bg-white text-[#3898ec]'
                          : 'bg-[#3898ec]/10 text-[#3898ec]'
                      }`}>
                        {index + 1}
                      </span>
                      <span className="text-sm font-medium truncate">{slide.title}</span>
                    </div>
                  </button>
                ))}
              </div>

              {/* Main Preview */}
              <div className="lg:col-span-3">
                <div className="bg-white rounded-2xl shadow-lg overflow-hidden">
                  {/* Slide Display */}
                  <div className="aspect-video bg-gradient-to-br from-[#1f1f1f] to-[#2a2a2a] p-12 flex flex-col justify-center">
                    <h2 className="text-3xl lg:text-4xl font-bold text-white mb-8">
                      {currentSlide.title}
                    </h2>
                    <ul className="space-y-4">
                      {currentSlide.content.map((item, idx) => (
                        <li key={idx} className="flex items-start gap-3 text-white/90">
                          <span className="w-2 h-2 bg-[#3898ec] rounded-full mt-2 flex-shrink-0" />
                          <span className="text-lg">{item}</span>
                        </li>
                      ))}
                    </ul>
                  </div>

                  {/* Navigation */}
                  <div className="p-4 border-t border-gray-200 flex items-center justify-between">
                    <button
                      onClick={() => setCurrentSlideIndex(Math.max(0, currentSlideIndex - 1))}
                      disabled={currentSlideIndex === 0}
                      className="flex items-center gap-2 px-4 py-2 text-[#1f1f1f]/60 hover:text-[#3898ec] disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                    >
                      <ChevronLeft className="w-5 h-5" />
                      上一页
                    </button>
                    <span className="text-sm text-[#1f1f1f]/60">
                      {currentSlideIndex + 1} / {slides.length}
                    </span>
                    <button
                      onClick={() => setCurrentSlideIndex(Math.min(slides.length - 1, currentSlideIndex + 1))}
                      disabled={currentSlideIndex === slides.length - 1}
                      className="flex items-center gap-2 px-4 py-2 text-[#1f1f1f]/60 hover:text-[#3898ec] disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                    >
                      下一页
                      <ChevronRight className="w-5 h-5" />
                    </button>
                  </div>
                </div>

                {/* Speaker Notes */}
                <div className="mt-4 bg-[#fff9e6] rounded-xl p-4">
                  <h4 className="text-sm font-medium text-[#f4b70d] mb-2">演讲备注</h4>
                  <p className="text-sm text-[#1f1f1f]/70">{currentSlide.notes}</p>
                </div>
              </div>
            </div>
          ) : (
            <div className="bg-white rounded-2xl shadow-lg p-6">
              <div className="space-y-6">
                {slides.map((slide, index) => (
                  <div key={slide.id} className="border-b border-gray-200 last:border-0 pb-6 last:pb-0">
                    <div className="flex items-start gap-4">
                      <span className="w-8 h-8 bg-[#3898ec]/10 text-[#3898ec] rounded-full flex items-center justify-center text-sm font-medium flex-shrink-0">
                        {index + 1}
                      </span>
                      <div className="flex-1">
                        <h3 className="text-lg font-semibold text-[#1f1f1f] mb-3">{slide.title}</h3>
                        <ul className="space-y-2">
                          {slide.content.map((item, idx) => (
                            <li key={idx} className="flex items-start gap-2 text-[#1f1f1f]/70">
                              <span className="w-1.5 h-1.5 bg-[#3898ec] rounded-full mt-2 flex-shrink-0" />
                              <span>{item}</span>
                            </li>
                          ))}
                        </ul>
                        <p className="mt-3 text-sm text-[#1f1f1f]/50">
                          <span className="font-medium">备注：</span>{slide.notes}
                        </p>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

        </div>
      </div>
    </section>
  );
};

export default PreviewSection;
