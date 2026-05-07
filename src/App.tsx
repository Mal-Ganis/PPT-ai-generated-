import { useCallback, useEffect, useState } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import './App.css';
import { Toaster } from '@/components/ui/sonner';
import Navbar from './sections/Navbar';
import Hero from './sections/Hero';
import InputSection from './sections/InputSection';
import OutlineSection from './sections/OutlineSection';
import ContentSection from './sections/ContentSection';
import PreviewSection from './sections/PreviewSection';
import EvaluationSection from './sections/EvaluationSection';
import SystemConfigSection from './sections/SystemConfigSection';
import ProjectsSection from './sections/ProjectsSection';
import Footer from './sections/Footer';
import SlideDetailView from './pages/SlideDetailView';
import KnowledgeSearchPage from './pages/KnowledgeSearchPage';
import {
  createProjectFromTopic,
  createProjectFromDocument,
  uploadDocumentFile,
  syncProjectOutline,
  generateSlideContents,
  fetchProject,
  type ProjectOutlineResponse,
  type ProjectDetailResponse,
  type UpsertOutlinePayload,
  type SystemConfig,
} from './lib/backend';

export type AppStep =
  | 'home'
  | 'input'
  | 'outline'
  | 'content'
  | 'preview'
  | 'evaluation'
  | 'config'
  | 'projects';

export interface SlideData {
  id: number;
  slideId?: number;
  chapter?: string;
  title: string;
  content: string[];
  notes: string;
  sources?: string[];
}

export interface OutlineData {
  title: string;
  slides: SlideData[];
}

function mapOutlineResponse(outline: ProjectOutlineResponse): OutlineData {
  return {
    title: outline.title,
    slides: outline.slides.map((s) => ({
      id: s.id,
      slideId: s.slideId,
      chapter: s.chapter,
      title: s.title,
      content: [...s.content],
      notes: s.notes,
    })),
  };
}

function mapDetailToSlides(detail: ProjectDetailResponse): SlideData[] {
  return [...detail.slides]
    .sort((a, b) => (a.position ?? 0) - (b.position ?? 0))
    .map((s) => ({
      id: s.position ?? s.id,
      slideId: s.id,
      chapter: s.chapter ?? undefined,
      title: s.title,
      content: s.bullets && s.bullets.length > 0 ? [...s.bullets] : s.body ? [s.body] : [],
      notes: s.notes ?? '',
      sources: s.sources,
    }));
}

function buildOutlinePayload(title: string, theme: string, slides: SlideData[]): UpsertOutlinePayload {
  return {
    title,
    theme,
    slides: slides.map((s, index) => ({
      position: index + 1,
      chapter: s.chapter,
      title: s.title,
      bullets: s.content,
      notes: s.notes,
      sources: s.sources,
    })),
  };
}

function ProjectsPage() {
  const navigate = useNavigate();
  return (
    <div className="min-h-screen bg-[#f3f3f3]">
      <ProjectsSection
        onBack={() => navigate('/')}
        onOpenProject={(id) => navigate('/', { state: { openProjectId: id } })}
      />
    </div>
  );
}

export function MainFlow() {
  const location = useLocation();
  const navigate = useNavigate();
  const [currentStep, setCurrentStep] = useState<AppStep>('home');
  const [projectId, setProjectId] = useState<number | null>(null);
  const [inputData, setInputData] = useState<{ type: 'topic' | 'document'; content: string } | null>(null);
  const [outlineData, setOutlineData] = useState<OutlineData | null>(null);
  const [finalSlides, setFinalSlides] = useState<SlideData[] | null>(null);

  const handleStart = () => {
    setCurrentStep('input');
  };

  const handleInputSubmit = async (
    type: 'topic' | 'document',
    content: string,
    meta?: { fileName?: string; formData?: FormData },
  ) => {
    try {
      if (type === 'topic') {
        setInputData({ type, content });
        const result = await createProjectFromTopic(content);
        setProjectId(result.projectId);
        setOutlineData(mapOutlineResponse(result));
      } else if (meta?.formData) {
        const result = await uploadDocumentFile(meta.formData);
        setProjectId(result.projectId);
        setOutlineData(mapOutlineResponse(result));
        setInputData({
          type: 'document',
          content: result.title || meta.fileName?.replace(/\.[^/.]+$/, '') || '文档',
        });
      } else {
        setInputData({ type, content });
        const docTitle = meta?.fileName?.replace(/\.[^/.]+$/, '') || '上传文档';
        const result = await createProjectFromDocument(docTitle, content);
        setProjectId(result.projectId);
        setOutlineData(mapOutlineResponse(result));
      }
      setCurrentStep('outline');
    } catch (error) {
      console.error('Error generating outline:', error);
    }
  };

  const handleOutlineConfirm = async (slides: SlideData[]) => {
    if (!projectId || !outlineData) {
      alert('缺少项目上下文，请返回重新输入。');
      return;
    }
    setOutlineData((prev) => (prev ? { ...prev, slides } : null));
    try {
      await syncProjectOutline(
        projectId,
        buildOutlinePayload(outlineData.title, inputData?.content ?? outlineData.title, slides),
      );
      const detail = await generateSlideContents(projectId, {
        inputType: inputData?.type ?? 'topic',
        inputContent: inputData?.content ?? '',
      });
      setFinalSlides(mapDetailToSlides(detail));
      setCurrentStep('content');
    } catch (error) {
      console.error('Error generating content:', error);
    }
  };

  const handleContentConfirm = async (slides: SlideData[]) => {
    setFinalSlides(slides);
    setCurrentStep('preview');
  };

  const handleReset = useCallback(() => {
    setCurrentStep('home');
    setProjectId(null);
    setInputData(null);
    setOutlineData(null);
    setFinalSlides(null);
  }, []);

  const handleShowEvaluations = () => {
    setCurrentStep('evaluation');
  };

  const handleShowConfig = () => {
    setCurrentStep('config');
  };

  const handleShowProjects = () => {
    setCurrentStep('projects');
  };

  const handleOpenProject = useCallback(async (id: number) => {
    try {
      const detail = await fetchProject(id);
      setProjectId(id);
      setInputData({ type: 'topic', content: detail.theme });
      setOutlineData({
        title: detail.title,
        slides: mapDetailToSlides(detail).map((s) => ({
          ...s,
          content: s.content.length ? s.content : ['（待编辑要点）'],
        })),
      });
      const hasContent = detail.slides.some((s) => (s.bullets?.length ?? 0) > 0 || (s.body?.length ?? 0) > 0);
      if (hasContent) {
        setFinalSlides(mapDetailToSlides(detail));
      } else {
        setFinalSlides(null);
      }
      setCurrentStep('outline');
    } catch (e) {
      console.error(e);
    }
  }, []);

  useEffect(() => {
    const st = (location.state ?? null) as {
      resetMainFlow?: number;
      openProjectId?: number;
    } | null;

    if (st?.resetMainFlow != null) {
      handleReset();
      navigate(location.pathname, { replace: true, state: {} });
      return;
    }

    const id = st?.openProjectId;
    if (id == null || !Number.isFinite(id)) return;

    let cancelled = false;
    void (async () => {
      await handleOpenProject(id);
      if (!cancelled) {
        navigate(location.pathname, { replace: true, state: {} });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [location.state, location.pathname, handleOpenProject, handleReset, navigate]);

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
          onShowProjects={handleShowProjects}
        />
      )}

      {currentStep === 'input' && <InputSection onSubmit={handleInputSubmit} />}

      {currentStep === 'outline' && outlineData && (
        <OutlineSection
          key={projectId ?? 'new'}
          projectId={projectId}
          outline={outlineData}
          onConfirm={handleOutlineConfirm}
          onBack={() => setCurrentStep('input')}
        />
      )}

      {currentStep === 'content' && finalSlides && projectId !== null && (
        <ContentSection
          projectId={projectId}
          slides={finalSlides}
          inputType={inputData?.type ?? 'topic'}
          inputContent={inputData?.content ?? ''}
          onConfirm={handleContentConfirm}
          onBack={() => setCurrentStep('outline')}
        />
      )}

      {currentStep === 'preview' && finalSlides && (
        <PreviewSection
          projectId={projectId}
          slides={finalSlides}
          title={outlineData?.title || 'PPT演示文稿'}
          onReset={handleReset}
          onBack={() => setCurrentStep('content')}
        />
      )}

      {currentStep === 'evaluation' && (
        <EvaluationSection defaultProjectId={projectId} />
      )}

      {currentStep === 'config' && (
        <SystemConfigSection onBack={() => setCurrentStep('home')} onSave={(_config: SystemConfig) => {}} />
      )}

      {currentStep === 'projects' && (
        <ProjectsSection onOpenProject={handleOpenProject} onBack={() => setCurrentStep('home')} />
      )}

      {currentStep === 'home' && <Footer />}
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <Toaster position="top-center" richColors />
      <Routes>
        <Route path="/" element={<MainFlow />} />
        <Route path="/projects" element={<ProjectsPage />} />
        <Route path="/knowledge" element={<KnowledgeSearchPage />} />
        <Route path="/project/:projectId/slide/:slideId" element={<SlideDetailView />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
