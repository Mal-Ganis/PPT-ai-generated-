import type { OutlineData, SlideData } from '../App';

export type WorkflowStep = 'input' | 'outline' | 'content' | 'preview';

export const WORKFLOW_STEP_ORDER: WorkflowStep[] = ['input', 'outline', 'content', 'preview'];

export const WORKFLOW_STEP_LABELS: Record<WorkflowStep, string> = {
  input: '输入',
  outline: '大纲',
  content: '内容',
  preview: '预览',
};

export interface WorkflowProgress {
  hasInput: boolean;
  hasOutline: boolean;
  hasContent: boolean;
  hasPreview: boolean;
}

export function getWorkflowProgress(args: {
  projectId: number | null;
  outlineData: OutlineData | null;
  finalSlides: SlideData[] | null;
}): WorkflowProgress {
  const hasContent = !!(args.finalSlides && args.finalSlides.length > 0);
  return {
    hasInput: args.projectId != null,
    hasOutline: !!args.outlineData,
    hasContent,
    hasPreview: hasContent,
  };
}

export function canGoToWorkflowStep(step: WorkflowStep, progress: WorkflowProgress): boolean {
  switch (step) {
    case 'input':
      return progress.hasInput;
    case 'outline':
      return progress.hasOutline;
    case 'content':
      return progress.hasContent;
    case 'preview':
      return progress.hasPreview;
    default:
      return false;
  }
}

export function getPrevWorkflowStep(current: WorkflowStep): WorkflowStep | null {
  const idx = WORKFLOW_STEP_ORDER.indexOf(current);
  return idx > 0 ? WORKFLOW_STEP_ORDER[idx - 1] : null;
}

export function getNextWorkflowStep(current: WorkflowStep): WorkflowStep | null {
  const idx = WORKFLOW_STEP_ORDER.indexOf(current);
  return idx >= 0 && idx < WORKFLOW_STEP_ORDER.length - 1 ? WORKFLOW_STEP_ORDER[idx + 1] : null;
}
