import type { AppStep, OutlineData, SlideData } from '../App';
import { WORKFLOW_STEP_LABELS, type WorkflowStep } from './workflowSteps';

const STORAGE_KEY = 'ppt-main-flow-v1';
const MAX_AGE_MS = 24 * 60 * 60 * 1000;

export interface MainFlowInputData {
  type: 'topic' | 'document';
  content: string;
  presentationDurationMinutes?: number;
  presenterRole?: string;
  llmApiKeyPresetId?: string | null;
  llmApiKeyOverride?: string;
  llmBaseUrlOverride?: string;
  llmModelOverride?: string;
}

export interface MainFlowSession {
  savedAt: number;
  currentStep: AppStep;
  projectId: number | null;
  inputData: MainFlowInputData | null;
  outlineData: OutlineData | null;
  finalSlides: SlideData[] | null;
  /** 是否已解锁预览步骤（完成编辑提炼后） */
  previewUnlocked?: boolean;
  /** 评估页「返回」应回到的流程步骤 */
  evaluationReturnStep?: AppStep;
}

/** 从子路由（如 /projects）返回主流程时的 location.state */
export interface FlowProjectsNavState {
  returnTo?: AppStep;
}

export function getAppStepBackLabel(step: AppStep): string {
  if (step === 'home') return '返回首页';
  if (step === 'evaluation') return '返回评估报告';
  if (step === 'projects') return '返回项目列表';
  if (isWorkflowStep(step)) return `返回${WORKFLOW_STEP_LABELS[step]}`;
  return '返回';
}

const WORKFLOW_STEPS: WorkflowStep[] = ['input', 'outline', 'content', 'preview'];

export function isWorkflowStep(step: AppStep): step is WorkflowStep {
  return WORKFLOW_STEPS.includes(step as WorkflowStep);
}

export function saveMainFlowSession(session: Omit<MainFlowSession, 'savedAt'>): void {
  try {
    const payload: MainFlowSession = { ...session, savedAt: Date.now() };
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
  } catch {
    // quota / private mode
  }
}

export function loadMainFlowSession(): MainFlowSession | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as MainFlowSession;
    if (!parsed?.savedAt || Date.now() - parsed.savedAt > MAX_AGE_MS) {
      clearMainFlowSession();
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function clearMainFlowSession(): void {
  try {
    sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // ignore
  }
}

/** 子路由（如单页高级编辑）点「返回流程」时，路由 state 会带 resumeMainFlow 时间戳 */
export function getResumeSessionFromLocationState(
  state: unknown,
): MainFlowSession | null {
  const st = (state ?? null) as { resumeMainFlow?: number } | null;
  if (st?.resumeMainFlow == null) return null;
  return loadMainFlowSession();
}
