import type { AppStep, OutlineData, SlideData } from '../App';
import type { WorkflowStep } from './workflowSteps';

const STORAGE_KEY = 'ppt-main-flow-v1';
const MAX_AGE_MS = 24 * 60 * 60 * 1000;

export interface MainFlowInputData {
  type: 'topic' | 'document';
  content: string;
  presentationDurationMinutes?: number;
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
