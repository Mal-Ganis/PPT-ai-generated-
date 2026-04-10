import { useState } from 'react';
import { ArrowRight, ArrowLeft, Plus, Trash2, GripVertical, Sparkles, Loader2, Edit2, Check } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import type { OutlineData, SlideData } from '../App';

interface OutlineSectionProps {
  outline: OutlineData;
  onConfirm: (slides: SlideData[]) => void;
  onBack: () => void;
}

const OutlineSection = ({ outline, onConfirm, onBack }: OutlineSectionProps) => {
  const [slides, setSlides] = useState<SlideData[]>(outline.slides);
  const [isLoading, setIsLoading] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editValue, setEditValue] = useState('');

  const handleAddSlide = () => {
    const newSlide: SlideData = {
      id: Date.now(),
      title: '新页面',
      content: ['要点1', '要点2'],
      notes: '',
    };
    setSlides([...slides, newSlide]);
  };

  const handleDeleteSlide = (id: number) => {
    if (slides.length <= 1) {
      alert('至少需要保留一页');
      return;
    }
    setSlides(slides.filter(s => s.id !== id));
  };

  const handleMoveSlide = (index: number, direction: 'up' | 'down') => {
    if (direction === 'up' && index === 0) return;
    if (direction === 'down' && index === slides.length - 1) return;
    
    const newSlides = [...slides];
    const targetIndex = direction === 'up' ? index - 1 : index + 1;
    [newSlides[index], newSlides[targetIndex]] = [newSlides[targetIndex], newSlides[index]];
    setSlides(newSlides);
  };

  const handleEditStart = (slide: SlideData) => {
    setEditingId(slide.id);
    setEditValue(slide.title);
  };

  const handleEditSave = () => {
    if (editingId !== null) {
      setSlides(slides.map(s => 
        s.id === editingId ? { ...s, title: editValue } : s
      ));
      setEditingId(null);
    }
  };

  const handleEditCancel = () => {
    setEditingId(null);
    setEditValue('');
  };

  const handleConfirm = async () => {
    setIsLoading(true);
    // Simulate API call for content generation
    await new Promise(resolve => setTimeout(resolve, 2000));
    onConfirm(slides);
    setIsLoading(false);
  };

  return (
    <section className="min-h-screen pt-24 pb-16 bg-[#f3f3f3]">
      <div className="section-container">
        <div className="section-inner max-w-5xl">
          {/* Header */}
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-8">
            <div>
              <h1 className="text-2xl sm:text-3xl font-bold text-[#1f1f1f] mb-2">
                大纲编辑
              </h1>
              <p className="text-[#1f1f1f]/60">
                主题：{outline.title}
              </p>
            </div>
            <div className="flex items-center gap-3">
              <Button
                variant="outline"
                onClick={onBack}
                className="border-gray-200 text-[#1f1f1f]"
              >
                <ArrowLeft className="w-4 h-4 mr-2" />
                返回
              </Button>
              <Button
                onClick={handleConfirm}
                disabled={isLoading}
                className="bg-[#3898ec] hover:bg-[#0082f3] text-white"
              >
                {isLoading ? (
                  <>
                    <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                    生成中...
                  </>
                ) : (
                  <>
                    <Sparkles className="w-4 h-4 mr-2" />
                    生成内容
                    <ArrowRight className="w-4 h-4 ml-2" />
                  </>
                )}
              </Button>
            </div>
          </div>

          {/* Slides List */}
          <div className="space-y-4 mb-8">
            {slides.map((slide, index) => (
              <div
                key={slide.id}
                className="bg-white rounded-xl shadow-md hover:shadow-lg transition-shadow duration-300 p-5"
              >
                <div className="flex items-start gap-4">
                  {/* Drag Handle */}
                  <div className="flex flex-col gap-1 pt-1">
                    <button
                      onClick={() => handleMoveSlide(index, 'up')}
                      disabled={index === 0}
                      className="p-1 hover:bg-gray-100 rounded disabled:opacity-30 disabled:cursor-not-allowed"
                    >
                      <svg className="w-4 h-4 text-[#1f1f1f]/40" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 15l7-7 7 7" />
                      </svg>
                    </button>
                    <GripVertical className="w-4 h-4 text-[#1f1f1f]/30" />
                    <button
                      onClick={() => handleMoveSlide(index, 'down')}
                      disabled={index === slides.length - 1}
                      className="p-1 hover:bg-gray-100 rounded disabled:opacity-30 disabled:cursor-not-allowed"
                    >
                      <svg className="w-4 h-4 text-[#1f1f1f]/40" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                      </svg>
                    </button>
                  </div>

                  {/* Slide Number */}
                  <div className="w-10 h-10 bg-[#3898ec]/10 rounded-lg flex items-center justify-center flex-shrink-0">
                    <span className="text-sm font-bold text-[#3898ec]">{index + 1}</span>
                  </div>

                  {/* Slide Content */}
                  <div className="flex-1 min-w-0">
                    {editingId === slide.id ? (
                      <div className="flex items-center gap-2">
                        <Input
                          value={editValue}
                          onChange={(e) => setEditValue(e.target.value)}
                          className="flex-1"
                          autoFocus
                          onKeyDown={(e) => {
                            if (e.key === 'Enter') handleEditSave();
                            if (e.key === 'Escape') handleEditCancel();
                          }}
                        />
                        <button
                          onClick={handleEditSave}
                          className="p-2 bg-[#3898ec] text-white rounded-lg hover:bg-[#0082f3]"
                        >
                          <Check className="w-4 h-4" />
                        </button>
                        <button
                          onClick={handleEditCancel}
                          className="p-2 bg-gray-100 text-gray-600 rounded-lg hover:bg-gray-200"
                        >
                          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                          </svg>
                        </button>
                      </div>
                    ) : (
                      <div className="flex items-center gap-2">
                        <h3 className="text-lg font-semibold text-[#1f1f1f]">{slide.title}</h3>
                        <button
                          onClick={() => handleEditStart(slide)}
                          className="p-1 hover:bg-gray-100 rounded opacity-0 group-hover:opacity-100 transition-opacity"
                        >
                          <Edit2 className="w-4 h-4 text-[#1f1f1f]/40" />
                        </button>
                      </div>
                    )}
                    
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

                  {/* Delete Button */}
                  <button
                    onClick={() => handleDeleteSlide(slide.id)}
                    className="p-2 hover:bg-red-50 text-[#1f1f1f]/40 hover:text-red-500 rounded-lg transition-colors"
                  >
                    <Trash2 className="w-5 h-5" />
                  </button>
                </div>
              </div>
            ))}
          </div>

          {/* Add Slide Button */}
          <button
            onClick={handleAddSlide}
            className="w-full py-4 border-2 border-dashed border-gray-300 hover:border-[#3898ec] rounded-xl text-[#1f1f1f]/50 hover:text-[#3898ec] transition-all duration-300 flex items-center justify-center gap-2"
          >
            <Plus className="w-5 h-5" />
            添加页面
          </button>

          {/* Tips */}
          <div className="mt-8 p-4 bg-[#3898ec]/5 rounded-xl">
            <p className="text-sm text-[#3898ec]">
              💡 提示：您可以拖拽调整页面顺序，点击标题进行编辑，或删除不需要的页面
            </p>
          </div>
        </div>
      </div>
    </section>
  );
};

export default OutlineSection;
