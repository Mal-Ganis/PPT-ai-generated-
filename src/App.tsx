import { useEffect, useState } from 'react';
import './App.css';
import Navbar from './sections/Navbar';
import Hero from './sections/Hero';
import InputSection from './sections/InputSection';
import OutlineSection from './sections/OutlineSection';
import ContentSection from './sections/ContentSection';
import PreviewSection from './sections/PreviewSection';
import EvaluationSection from './sections/EvaluationSection';
import SystemConfigSection from './sections/SystemConfigSection';
import Footer from './sections/Footer';
import { fetchSystemConfig, createProjectFromTopic } from './lib/backend';
import { generateOutline, generateSlideContent } from './lib/api';
import type { SystemConfig } from './lib/api';

export type AppStep = 'home' | 'input' | 'outline' | 'content' | 'preview' | 'evaluation' | 'config';

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

const defaultSystemConfig: SystemConfig = {
  llmModel: 'deepseek-reasoner',
  temperature: 0.7,
  maxTokens: 1024,
  topP: 0.95,
  topK: 1,
  retrievalLimit: 5,
  outlinePromptTemplate: `请根据以下主题生成一个专业的PPT大纲。主题：{content}\n\n请以JSON格式返回，格式如下：{\n  \"title\": \"大纲标题\",\n  \"slides\": [\n    {\n      \"id\": 1,\n      \"title\": \"幻灯片标题\",\n      \"content\": [\"要点1\", \"要点2\"],\n      \"notes\": \"演讲者备注\"\n    }\n  ]\n}\n\n大纲应该包括封面、目录、主要内容章节和总结，至少5-8页。`,
  slidePromptTemplate: `请为PPT幻灯片生成详细内容。\n\n幻灯片标题：{slideTitle}\n原始输入类型：{inputType}\n原始输入内容：{inputContent}\n\n请生成：\n1. 3-5个主要内容要点（数组）\n2. 演讲者备注（字符串）\n\n请以JSON格式返回：{\n  \"content\": [\"要点1\", \"要点2\", \"要点3\"],\n  \"notes\": \"演讲者备注内容\"\n}`,
};

function App() {
  const [currentStep, setCurrentStep] = useState<AppStep>('home');
  const [inputData, setInputData] = useState<{ type: 'topic' | 'document'; content: string } | null>(null);
  const [outlineData, setOutlineData] = useState<OutlineData | null>(null);
  const [finalSlides, setFinalSlides] = useState<SlideData[] | null>(null);
  const [systemConfig, setSystemConfig] = useState<SystemConfig | null>(null);

  useEffect(() => {
    const loadConfig = async () => {
      try {
        const config = await fetchSystemConfig();
        setSystemConfig(config);
      } catch (error) {
        console.error('Failed to load system config:', error);
      }
    };

    loadConfig();
  }, []);

  const handleStart = () => {
    setCurrentStep('input');
  };

  const handleInputSubmit = async (type: 'topic' | 'document', content: string) => {
    setInputData({ type, content });
    try {
      if (type === 'topic') {
        const result = await createProjectFromTopic(content);
        setOutlineData({ title: result.title, slides: result.slides });
      } else {
        const outline = await generateOutline({ type, content }, systemConfig ?? defaultSystemConfig);
        setOutlineData(outline);
      }
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
        }, systemConfig ?? defaultSystemConfig);
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

  const handleShowEvaluations = () => {
    setCurrentStep('evaluation');
  };

  const handleShowConfig = () => {
    setCurrentStep('config');
  };

  const handleConfigSave = (config: SystemConfig) => {
    setSystemConfig(config);
  };

  return (
    <div className="min-h-screen bg-[#f3f3f3]">
      <Navbar 
        currentStep={currentStep} 
        onNavigate={setCurrentStep}
        onReset={handleReset}
        onOpenConfig={handleShowConfig}
      />
      
      {currentStep === 'home' && (
        <Hero
          onStart={handleStart}
          onShowEvaluations={handleShowEvaluations}
          onShowConfig={handleShowConfig}
        />
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

      {currentStep === 'evaluation' && (
        <EvaluationSection />
      )}

      {currentStep === 'config' && (
        <SystemConfigSection onBack={() => setCurrentStep('home')} onSave={handleConfigSave} />
      )}
      
      {currentStep === 'home' && <Footer />}
    </div>
  );
}

export default App;
