import { useCallback, useEffect, useRef, useState } from 'react';
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
  regenerateProjectOutline,
  generateSlideContents,
  saveProjectSlides,
  extractPptDisplayContents,
  fetchProject,
  fetchProjectForSlides,
  type ProjectOutlineResponse,
  type SystemConfig,
} from './lib/backend';
import { buildOutlinePayload, mapDetailToSlides } from './lib/slideMappers';
import {
  clearMainFlowSession,
  isWorkflowStep,
  getResumeSessionFromLocationState,
  loadMainFlowSession,
  saveMainFlowSession,
  type MainFlowSession,
} from './lib/mainFlowSession';
import {
  detailHasGeneratedContent,
  resolveOpenStep,
  slidesHaveGeneratedContent,
} from './lib/projectProgress';
import { projectNeedsPptExtraction, slidesHaveReadyPreview } from './lib/pptExtraction';
import {
  canGoToWorkflowStep,
  getWorkflowProgress,
  type WorkflowStep,
} from './lib/workflowSteps';
import { toast } from 'sonner';

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

function ProjectsPage() {
  const navigate = useNavigate();
  return (
    <div className="min-h-screen bg-[#f3f3f3]">
      <ProjectsSection
        onOpenProject={(id, step) =>
          navigate('/', { state: { openProjectId: id, openProjectStep: step } })
        }
      />
    </div>
  );
}

export function MainFlow() {
  const location = useLocation();
  const navigate = useNavigate();
  const resumeSessionOnMount = getResumeSessionFromLocationState(location.state);
  const [currentStep, setCurrentStep] = useState<AppStep>(() =>
    resumeSessionOnMount && isWorkflowStep(resumeSessionOnMount.currentStep)
      ? resumeSessionOnMount.currentStep
      : 'home',
  );
  const [projectId, setProjectId] = useState<number | null>(
    () => resumeSessionOnMount?.projectId ?? null,
  );
  const [inputData, setInputData] = useState<{
    type: 'topic' | 'document';
    content: string;
    presentationDurationMinutes?: number;
  } | null>(() => resumeSessionOnMount?.inputData ?? null);
  const [outlineData, setOutlineData] = useState<OutlineData | null>(
    () => resumeSessionOnMount?.outlineData ?? null,
  );
  const [outlineRevision, setOutlineRevision] = useState(0);
  const [finalSlides, setFinalSlides] = useState<SlideData[] | null>(
    () => resumeSessionOnMount?.finalSlides ?? null,
  );
  const [previewUnlocked, setPreviewUnlocked] = useState(() => {
    const slides = resumeSessionOnMount?.finalSlides;
    const hasGenerated = slides != null && slidesHaveGeneratedContent(slides);
    if (!hasGenerated) return false;
    return (
      resumeSessionOnMount?.previewUnlocked ??
      slidesHaveReadyPreview(slides)
    );
  });
  const sessionHydratedRef = useRef(resumeSessionOnMount != null);

  const bumpOutlineRevision = useCallback(() => {
    setOutlineRevision((r) => r + 1);
  }, []);

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
      bumpOutlineRevision();
    } catch (error) {
      console.error('Error generating outline:', error);
      // 具体文案由 backendApi 拦截器 toast；此处避免静默失败
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
    setPreviewUnlocked(false);
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
      if (projectNeedsPptExtraction(slides)) {
        reportProgress?.('正在提炼适合 PPT 的短要点（DeepSeek）…');
        const detail = await extractPptDisplayContents(projectId, {
          force: false,
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
      } else {
        reportProgress?.('正在加载预览…');
        const detail = await fetchProjectForSlides(projectId);
        setFinalSlides(mapDetailToSlides(detail));
      }
      setPreviewUnlocked(true);
      setCurrentStep('preview');
    } catch (e) {
      console.error(e);
      setFinalSlides(slides);
      setCurrentStep('preview');
    }
  };

  const handleReset = useCallback(() => {
    sessionHydratedRef.current = false;
    clearMainFlowSession();
    setCurrentStep('home');
    setProjectId(null);
    setInputData(null);
    setOutlineData(null);
    setFinalSlides(null);
    setOutlineRevision(0);
    setPreviewUnlocked(false);
  }, []);

  const applyMainFlowSession = useCallback((session: MainFlowSession) => {
    setCurrentStep(session.currentStep);
    setProjectId(session.projectId);
    setInputData(session.inputData);
    setOutlineData(session.outlineData);
    const slides = session.finalSlides;
    const hasGenerated = slides != null && slidesHaveGeneratedContent(slides);
    setFinalSlides(hasGenerated ? slides : null);
    setPreviewUnlocked(
      !!session.previewUnlocked &&
        hasGenerated &&
        slidesHaveReadyPreview(slides ?? []),
    );
    if (session.outlineData) {
      setOutlineRevision((r) => r + 1);
    }
  }, []);

  const refreshProjectState = useCallback(
    async (id: number, preferredStep?: WorkflowStep) => {
      const detail = await fetchProject(id);
      const mapped = mapDetailToSlides(detail);
      setProjectId(id);
      setInputData({ type: 'topic', content: detail.theme });
      setOutlineData({
        title: detail.title,
        presentationDurationMinutes: detail.presentationDurationMinutes,
        slides: mapped.map((s) => ({
          ...s,
          content: s.content.length ? s.content : ['（待编辑要点）'],
        })),
      });
      const hasGenerated = detailHasGeneratedContent(detail);
      /** 仅已生成正文的项目才灌入 finalSlides，避免仅大纲时内容页展示大纲副本 */
      setFinalSlides(hasGenerated && mapped.length > 0 ? mapped : null);
      setPreviewUnlocked(hasGenerated && slidesHaveReadyPreview(mapped));

      const target = resolveOpenStep(detail, preferredStep);
      if (
        preferredStep === 'content' &&
        target === 'outline'
      ) {
        toast.info('该项目尚未保存正文，已打开大纲');
      } else if (preferredStep === 'preview' && target === 'content') {
        toast.info('该项目尚未生成预览，已打开内容');
      } else if (preferredStep === 'preview' && target === 'outline') {
        toast.info('该项目尚未保存正文，已打开大纲');
      }

      setCurrentStep(isWorkflowStep(target) ? target : 'outline');
      bumpOutlineRevision();
    },
    [bumpOutlineRevision],
  );

  const handleShowEvaluations = () => {
    setCurrentStep('evaluation');
  };

  const handleShowConfig = () => {
    setCurrentStep('config');
  };

  const handleShowProjects = () => {
    setCurrentStep('projects');
  };

  const workflowProgress = getWorkflowProgress({
    projectId,
    outlineData,
    finalSlides,
    previewUnlocked,
  });

  const handleRegenerateOutline = useCallback(
    async (topic: string) => {
      if (!projectId) {
        alert('缺少项目上下文，请返回重新输入。');
        return;
      }
      const trimmed = topic.trim();
      if (!trimmed) {
        alert('请输入演示主题');
        return;
      }
      if (workflowProgress.hasContent) {
        const ok = window.confirm(
          '重新生成大纲将替换当前页面结构，已生成的正文与预览需要重新生成。是否继续？',
        );
        if (!ok) return;
      }
      try {
        const minutes =
          outlineData?.presentationDurationMinutes ??
          inputData?.presentationDurationMinutes ??
          15;
        const result = await regenerateProjectOutline(projectId, {
          topic: trimmed,
          presentationDurationMinutes: minutes,
          inputType: inputData?.type ?? 'topic',
          inputContent: inputData?.content ?? trimmed,
        });
        setOutlineData(mapOutlineResponse(result));
        setInputData((prev) =>
          prev
            ? { ...prev, content: trimmed }
            : { type: 'topic', content: trimmed, presentationDurationMinutes: minutes },
        );
        setFinalSlides(null);
        setPreviewUnlocked(false);
        bumpOutlineRevision();
        toast.success('大纲已重新生成');
      } catch (error) {
        console.error('Error regenerating outline:', error);
      }
    },
    [
      projectId,
      workflowProgress.hasContent,
      outlineData?.presentationDurationMinutes,
      inputData,
      bumpOutlineRevision,
    ],
  );

  const handleWorkflowNavigate = useCallback(
    (target: WorkflowStep) => {
      if (!canGoToWorkflowStep(target, workflowProgress)) {
        return;
      }
      if (target === 'preview' && projectId != null) {
        void (async () => {
          try {
            const detail = await fetchProjectForSlides(projectId);
            setFinalSlides(mapDetailToSlides(detail));
          } catch {
            // 保留当前内存中的 slides
          }
          setCurrentStep('preview');
        })();
        return;
      }
      setCurrentStep(target);
    },
    [workflowProgress, projectId],
  );

  const handleNavbarNavigate = useCallback(
    (step: AppStep) => {
      if (step === 'input' || step === 'outline' || step === 'content' || step === 'preview') {
        handleWorkflowNavigate(step);
        return;
      }
      setCurrentStep(step);
    },
    [handleWorkflowNavigate],
  );

  const handleOutlineSlidesChange = useCallback((slides: SlideData[]) => {
    setOutlineData((prev) => (prev ? { ...prev, slides } : null));
  }, []);

  const handleOpenProject = useCallback(
    async (id: number, preferredStep?: WorkflowStep) => {
      try {
        await refreshProjectState(id, preferredStep);
      } catch (e) {
        console.error(e);
        toast.error(e instanceof Error ? e.message : '打开项目失败');
      }
    },
    [refreshProjectState],
  );

  const persistMainFlowSession = useCallback(
    (outlineSlidesOverride?: SlideData[]) => {
      if (currentStep === 'home' && projectId == null) {
        clearMainFlowSession();
        return;
      }
      if (!isWorkflowStep(currentStep) && currentStep !== 'projects') {
        return;
      }
      const outlineToSave =
        outlineSlidesOverride && outlineData
          ? { ...outlineData, slides: outlineSlidesOverride }
          : outlineData;
      saveMainFlowSession({
        currentStep,
        projectId,
        inputData,
        outlineData: outlineToSave,
        finalSlides,
        previewUnlocked,
      });
    },
    [currentStep, projectId, inputData, outlineData, finalSlides, previewUnlocked],
  );

  useEffect(() => {
    persistMainFlowSession();
    return () => persistMainFlowSession();
  }, [persistMainFlowSession]);

  useEffect(() => {
    const st = (location.state ?? null) as {
      resetMainFlow?: number;
      resumeMainFlow?: number;
      openProjectId?: number;
      openProjectStep?: WorkflowStep;
    } | null;

    if (st?.resetMainFlow != null) {
      handleReset();
      navigate(location.pathname, { replace: true, state: {} });
      return;
    }

    if (st?.resumeMainFlow != null) {
      sessionHydratedRef.current = true;
      const session = loadMainFlowSession();
      let cancelled = false;
      void (async () => {
        if (session?.projectId) {
          try {
            const preferred = isWorkflowStep(session.currentStep) ? session.currentStep : undefined;
            await refreshProjectState(session.projectId, preferred);
          } catch {
            applyMainFlowSession(session);
          }
        } else if (session) {
          applyMainFlowSession(session);
        }
        if (!cancelled) {
          navigate(location.pathname, { replace: true, state: {} });
        }
      })();
      return () => {
        cancelled = true;
      };
    }

    const id = st?.openProjectId;
    if (id == null || !Number.isFinite(id)) {
      if (!sessionHydratedRef.current && location.pathname === '/') {
        const session = loadMainFlowSession();
        if (session?.projectId && isWorkflowStep(session.currentStep)) {
          sessionHydratedRef.current = true;
          void refreshProjectState(session.projectId, session.currentStep);
        }
      }
      return;
    }

    sessionHydratedRef.current = true;
    let cancelled = false;
    void (async () => {
      const step =
        st?.openProjectStep && isWorkflowStep(st.openProjectStep) ? st.openProjectStep : undefined;
      await handleOpenProject(id, step);
      if (!cancelled) {
        navigate(location.pathname, { replace: true, state: {} });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [
    location.state,
    location.pathname,
    handleOpenProject,
    handleReset,
    navigate,
    applyMainFlowSession,
    refreshProjectState,
  ]);

  return (
    <div className="min-h-screen bg-[#f3f3f3]">
      <Navbar
        currentStep={currentStep}
        workflowProgress={workflowProgress}
        onNavigate={handleNavbarNavigate}
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

      {currentStep === 'input' && (
        <InputSection
          onSubmit={handleInputSubmit}
          workflowProgress={workflowProgress}
          onGoToStep={handleWorkflowNavigate}
          initialTopic={inputData?.type === 'topic' ? inputData.content : undefined}
          initialInputType={inputData?.type}
          initialPresentationMinutes={inputData?.presentationDurationMinutes}
        />
      )}

      {currentStep === 'outline' && outlineData && (
        <OutlineSection
          key={projectId ?? 'new'}
          projectId={projectId}
          outline={outlineData}
          outlineRevision={outlineRevision}
          deckTheme={inputData?.content ?? outlineData.title}
          inputType={inputData?.type ?? 'topic'}
          workflowProgress={workflowProgress}
          onGoToStep={handleWorkflowNavigate}
          onSlidesChange={handleOutlineSlidesChange}
          onConfirm={handleOutlineConfirm}
          onRegenerateOutline={handleRegenerateOutline}
          onPersistFlowSession={persistMainFlowSession}
        />
      )}

      {currentStep === 'content' && finalSlides && projectId !== null && (
        <ContentSection
          projectId={projectId}
          slides={finalSlides}
          deckTitle={outlineData?.title ?? 'PPT演示文稿'}
          deckTheme={inputData?.content ?? outlineData?.title ?? ''}
          inputType={inputData?.type ?? 'topic'}
          inputContent={inputData?.content ?? ''}
          workflowProgress={workflowProgress}
          onGoToStep={handleWorkflowNavigate}
          onSlidesChange={setFinalSlides}
          onConfirm={handleContentConfirm}
        />
      )}

      {currentStep === 'preview' && finalSlides && (
        <PreviewSection
          projectId={projectId}
          slides={finalSlides}
          title={outlineData?.title || 'PPT演示文稿'}
          deckTheme={inputData?.content ?? outlineData?.title ?? ''}
          inputType={inputData?.type ?? 'topic'}
          inputContent={inputData?.content ?? ''}
          workflowProgress={workflowProgress}
          previewUnlocked={previewUnlocked}
          onGoToStep={handleWorkflowNavigate}
          onSlidesChange={setFinalSlides}
          onReset={handleReset}
        />
      )}

      {currentStep === 'evaluation' && (
        <EvaluationSection defaultProjectId={projectId} />
      )}

      {currentStep === 'config' && (
        <SystemConfigSection onBack={() => setCurrentStep('home')} onSave={(_config: SystemConfig) => {}} />
      )}

      {currentStep === 'projects' && (
        <ProjectsSection onOpenProject={handleOpenProject} />
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
