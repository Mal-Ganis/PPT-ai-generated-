import { useState } from 'react';
import './App.css';
import Navbar from './sections/Navbar';
import Hero from './sections/Hero';
import InputSection from './sections/InputSection';
import OutlineSection from './sections/OutlineSection';
import ContentSection from './sections/ContentSection';
import PreviewSection from './sections/PreviewSection';
import Footer from './sections/Footer';

export type AppStep = 'home' | 'input' | 'outline' | 'content' | 'preview';

export interface SlideData {
  id: number;
  title: string;
  content: string[];
  notes: string;
}

export interface OutlineData {
  title: string;
  slides: SlideData[];
}

function App() {
  const [currentStep, setCurrentStep] = useState<AppStep>('home');
  const [inputData, setInputData] = useState<{ type: 'topic' | 'document'; content: string } | null>(null);
  const [outlineData, setOutlineData] = useState<OutlineData | null>(null);
  const [finalSlides, setFinalSlides] = useState<SlideData[] | null>(null);

  const handleStart = () => {
    setCurrentStep('input');
  };

  const handleInputSubmit = (type: 'topic' | 'document', content: string) => {
    setInputData({ type, content });
    // TODO: Call API to generate outline
    // For now, generate mock data
    const mockOutline: OutlineData = {
      title: type === 'topic' ? content : '文档主题分析',
      slides: [
        { id: 1, title: '封面', content: ['标题', '副标题'], notes: '' },
        { id: 2, title: '目录', content: ['要点1', '要点2', '要点3'], notes: '' },
        { id: 3, title: '背景介绍', content: ['行业现状', '问题分析', '市场机会'], notes: '' },
        { id: 4, title: '解决方案', content: ['核心思路', '技术架构', '实施路径'], notes: '' },
        { id: 5, title: '总结与展望', content: ['核心成果', '未来规划'], notes: '' },
      ],
    };
    setOutlineData(mockOutline);
    setCurrentStep('outline');
  };

  const handleOutlineConfirm = (slides: SlideData[]) => {
    setOutlineData(prev => prev ? { ...prev, slides } : null);
    // TODO: Call API to generate content for each slide
    // For now, generate mock content
    const slidesWithContent: SlideData[] = slides.map(slide => ({
      ...slide,
      content: slide.content.map((item, idx) => 
        `${item} - 基于${inputData?.type === 'topic' ? '主题' : '文档'}分析的详细内容要点${idx + 1}`
      ),
      notes: `本页重点：${slide.title}的核心观点和关键数据支撑`,
    }));
    setFinalSlides(slidesWithContent);
    setCurrentStep('content');
  };

  const handleContentConfirm = (slides: SlideData[]) => {
    setFinalSlides(slides);
    setCurrentStep('preview');
  };

  const handleReset = () => {
    setCurrentStep('home');
    setInputData(null);
    setOutlineData(null);
    setFinalSlides(null);
  };

  return (
    <div className="min-h-screen bg-[#f3f3f3]">
      <Navbar 
        currentStep={currentStep} 
        onNavigate={setCurrentStep}
        onReset={handleReset}
      />
      
      {currentStep === 'home' && (
        <Hero onStart={handleStart} />
      )}
      
      {currentStep === 'input' && (
        <InputSection onSubmit={handleInputSubmit} />
      )}
      
      {currentStep === 'outline' && outlineData && (
        <OutlineSection 
          outline={outlineData} 
          onConfirm={handleOutlineConfirm}
          onBack={() => setCurrentStep('input')}
        />
      )}
      
      {currentStep === 'content' && finalSlides && (
        <ContentSection 
          slides={finalSlides}
          onConfirm={handleContentConfirm}
          onBack={() => setCurrentStep('outline')}
        />
      )}
      
      {currentStep === 'preview' && finalSlides && (
        <PreviewSection 
          slides={finalSlides}
          title={outlineData?.title || 'PPT演示文稿'}
          onReset={handleReset}
          onBack={() => setCurrentStep('content')}
        />
      )}
      
      {currentStep === 'home' && <Footer />}
    </div>
  );
}

export default App;
