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
  saveProjectSlides,
  extractPptDisplayContents,
  fetchProject,
  fetchProjectForSlides,
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
  /** 讲稿要点（可含口头衔接语，供演讲者阅读） */
  content: string[];
  /** 适合打在幻灯片上的短要点（投影用） */
  pptContent: string[];
  /** @deprecated 已不再生成；保留字段仅为兼容旧数据 */
  notes?: string;
  sources?: string[];
}

export interface OutlineData {
  title: string;
  presentationDurationMinutes?: number;
  slides: SlideData[];
}

function mapOutlineResponse(outline: ProjectOutlineResponse): OutlineData {
  return {
    title: outline.title,
    presentationDurationMinutes: outline.presentationDurationMinutes,
    slides: outline.slides.map((s) => ({
      id: s.slideId != null ? Number(s.slideId) : s.id,
      slideId: s.slideId != null ? Number(s.slideId) : undefined,
      chapter: s.chapter,
      title: s.title,
      content: [...s.content],
      pptContent: [],
    })),
  };
}

function mapDetailToSlides(detail: ProjectDetailResponse): SlideData[] {
  return [...detail.slides]
    .sort((a, b) => (a.position ?? 0) - (b.position ?? 0))
    .map((s) => {
      const script =
        s.bullets && s.bullets.length > 0 ? [...s.bullets] : s.body ? [s.body] : [];
      const ppt = s.pptBullets && s.pptBullets.length > 0 ? [...s.pptBullets] : [];
      return {
        id: s.id,
        slideId: s.id,
        chapter: s.chapter ?? undefined,
        title: s.title,
        content: script,
        pptContent: ppt,
        sources: s.sources,
      };
    });
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
  const [inputData, setInputData] = useState<{
    type: 'topic' | 'document';
    content: string;
    presentationDurationMinutes?: number;
  } | null>(null);
  const [outlineData, setOutlineData] = useState<OutlineData | null>(null);
  const [finalSlides, setFinalSlides] = useState<SlideData[] | null>(null);

  const handleStart = () => {
    setCurrentStep('input');
  };

  const handleInputSubmit = async (
    type: 'topic' | 'document',
    content: string,
    meta?: { fileName?: string; formData?: FormData; presentationDurationMinutes?: number },
  ) => {
    const duration = meta?.presentationDurationMinutes ?? 15;
    try {
      if (type === 'topic') {
        setInputData({ type, content, presentationDurationMinutes: duration });
        const result = await createProjectFromTopic(content, duration);
        setProjectId(result.projectId);
        setOutlineData(mapOutlineResponse(result));
      } else if (meta?.formData) {
        const result = await uploadDocumentFile(meta.formData, duration);
        setProjectId(result.projectId);
        setOutlineData(mapOutlineResponse(result));
        setInputData({
          type: 'document',
          content: result.title || meta.fileName?.replace(/\.[^/.]+$/, '') || '文档',
          presentationDurationMinutes: duration,
        });
      } else {
        setInputData({ type, content, presentationDurationMinutes: duration });
        const docTitle = meta?.fileName?.replace(/\.[^/.]+$/, '') || '上传文档';
        const result = await createProjectFromDocument(docTitle, content, duration);
        setProjectId(result.projectId);
        setOutlineData(mapOutlineResponse(result));
      }
      setCurrentStep('outline');
    } catch (error) {
      console.error('Error generating outline:', error);
    }
  };

  const handleOutlineConfirm = async (
    slides: SlideData[],
    reportProgress?: (message: string) => void,
  ) => {
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
      reportProgress?.('已同步大纲，正在排队生成正文…');
      const detail = await generateSlideContents(
        projectId,
        {
          inputType: inputData?.type ?? 'topic',
          inputContent: inputData?.content ?? '',
        },
        (st) => {
          const msg =
            st.message?.trim() ||
            (st.totalSlides > 0
              ? `正在生成第 ${st.completedSlides} / ${st.totalSlides} 页…`
              : '正在生成幻灯片内容…');
          reportProgress?.(msg);
        },
      );
      setFinalSlides(mapDetailToSlides(detail));
      setCurrentStep('content');
    } catch (error) {
      console.error('Error generating content:', error);
      if (projectId) {
        try {
          const recovered = await fetchProjectForSlides(projectId);
          const ready = recovered.slides.filter((s) => (s.bullets?.length ?? 0) > 0).length;
          if (ready > 0) {
            setFinalSlides(mapDetailToSlides(recovered));
            setCurrentStep('content');
            reportProgress?.('连接中断，已从服务器恢复已生成的页面');
          }
        } catch {
          // ignore secondary failure
        }
      }
    }
  };

  const handleContentConfirm = async (
    slides: SlideData[],
    reportProgress?: (message: string) => void,
  ) => {
    if (!projectId) {
      setFinalSlides(slides);
      setCurrentStep('preview');
      return;
    }
    try {
      reportProgress?.('正在保存讲稿…');
      const toSave = slides.filter((s) => s.slideId != null) as Array<
        SlideData & { slideId: number }
      >;
      await saveProjectSlides(
        projectId,
        toSave.map((s) => ({
          slideId: s.slideId,
          bullets: s.content,
          pptBullets: s.pptContent?.length ? s.pptContent : undefined,
        })),
      );
      {
        reportProgress?.('正在提炼适合 PPT 的短要点（DeepSeek）…');
        const detail = await extractPptDisplayContents(projectId, {
          onProgress: (st) => {
            const msg =
              st.message?.trim() ||
              (st.totalSlides > 0
                ? `提炼中 ${st.completedSlides} / ${st.totalSlides} 页…`
                : '正在提炼…');
            reportProgress?.(msg);
          },
        });
        setFinalSlides(mapDetailToSlides(detail));
      }
      setCurrentStep('preview');
    } catch (e) {
      console.error(e);
      setFinalSlides(slides);
      setCurrentStep('preview');
    }
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
        presentationDurationMinutes: detail.presentationDurationMinutes,
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
          onSlidesChange={setFinalSlides}
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
