import { useEffect, useState } from 'react';
import { FileText, ArrowLeft, RotateCcw, ChevronLeft, ChevronRight, Presentation } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { loadExternalSources, searchExternalSources, type ExternalSourceDocument } from '@/lib/backend';
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
  const [externalDocs, setExternalDocs] = useState<ExternalSourceDocument[]>([]);
  const [isExternalLoading, setIsExternalLoading] = useState(false);
  const [externalLoadStatus, setExternalLoadStatus] = useState('');

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

  useEffect(() => {
    const loadExternalDocs = async () => {
      setIsExternalLoading(true);
      try {
        const results = await searchExternalSources(title, 3);
        setExternalDocs(results);
      } catch (error) {
        console.error('Failed to load external sources:', error);
      } finally {
        setIsExternalLoading(false);
      }
    };

    loadExternalDocs();
  }, [title]);

  const handleLoadExternalSourcesToIndex = async () => {
    setExternalLoadStatus('loading');
    try {
      const count = await loadExternalSources(title, 0, 3);
      setExternalLoadStatus(`已索引 ${count} 条外部文档到本地向量库。`);
    } catch (error) {
      console.error('Failed to index external sources:', error);
      setExternalLoadStatus('外部文档索引失败，请稍后重试。');
    }
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

          {/* External Source Citations */}
          <div className="mb-6 p-5 rounded-2xl bg-white shadow-sm border border-gray-200">
            <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4 mb-4">
              <div>
                <h2 className="text-lg font-semibold text-[#1f1f1f] mb-1">外部权威来源</h2>
                <p className="text-sm text-[#1f1f1f]/70">
                  通过外部权威知识源检索相关文档，展示来源、发布日期、作者、摘要与可信度评分。
                </p>
              </div>
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  onClick={handleLoadExternalSourcesToIndex}
                  className="border-gray-200 text-[#1f1f1f]"
                >
                  加载到本地索引
                </Button>
                {externalLoadStatus && (
                  <span className="text-sm text-[#1f1f1f]/70">{externalLoadStatus}</span>
                )}
              </div>
            </div>

            {isExternalLoading ? (
              <div className="text-sm text-[#1f1f1f]/70">正在检索外部文档，请稍候…</div>
            ) : externalDocs.length === 0 ? (
              <div className="text-sm text-[#1f1f1f]/70">未找到匹配的外部来源。</div>
            ) : (
              <div className="space-y-4">
                {externalDocs.map((doc) => (
                  <div key={doc.url} className="rounded-2xl border border-gray-200 p-4 bg-[#fafbff]">
                    <div className="flex flex-col sm:flex-row sm:justify-between sm:items-start gap-2">
                      <a href={doc.url} target="_blank" rel="noreferrer" className="text-base font-semibold text-[#1f1f1f] hover:text-[#3898ec]">
                        {doc.title}
                      </a>
                      <div className="text-sm text-[#1f1f1f]/60">
                        {doc.source} · {doc.publishedAt.split('T')[0] || doc.publishedAt}
                      </div>
                    </div>
                    <p className="mt-2 text-sm text-[#1f1f1f]/70">{doc.summary}</p>
                    <div className="mt-3 flex flex-wrap gap-2 text-sm">
                      <span className="px-2 py-1 rounded-full bg-[#e7f5ff] text-[#0f5abb]">作者：{doc.author || '未知'}</span>
                      <span className="px-2 py-1 rounded-full bg-[#e8f7e9] text-[#1f6b2e]">可信度：{(doc.trustScore * 100).toFixed(0)}%</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
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
