import { useState } from 'react';
import './App.css';
import Navbar from './sections/Navbar';
import Hero from './sections/Hero';
import InputSection from './sections/InputSection';
import OutlineSection from './sections/OutlineSection';
import ContentSection from './sections/ContentSection';
import PreviewSection from './sections/PreviewSection';
import Footer from './sections/Footer';
import { generateOutline, generateSlideContent } from './lib/api';

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

  const handleInputSubmit = async (type: 'topic' | 'document', content: string) => {
    setInputData({ type, content });
    try {
      const outline = await generateOutline({ type, content });
      setOutlineData(outline);
      setCurrentStep('outline');
    } catch (error) {
      console.error('Error generating outline:', error);
      alert(`生成大纲失败，请重试：${error instanceof Error ? error.message : '未知错误'}`);
    }
  };

  const handleOutlineConfirm = async (slides: SlideData[]) => {
    setOutlineData(prev => prev ? { ...prev, slides } : null);
    try {
      const slidesWithContent: SlideData[] = [];
      for (const slide of slides) {
        const content = await generateSlideContent({
          slideTitle: slide.title,
          inputType: inputData?.type || 'topic',
          inputContent: inputData?.content || '',
        });
        slidesWithContent.push({
          ...slide,
          content: content.content,
          notes: content.notes,
        });
      }
      setFinalSlides(slidesWithContent);
      setCurrentStep('content');
    } catch (error) {
      console.error('Error generating content:', error);
      alert(`生成内容失败，请重试：${error instanceof Error ? error.message : '未知错误'}`);
    }
  };

  const handleContentConfirm = async (slides: SlideData[]) => {
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
