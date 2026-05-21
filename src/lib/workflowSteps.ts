import type { OutlineData, SlideData } from '../App';
import { slidesHaveReadyPreview } from './pptExtraction';
import { slidesHaveGeneratedContent } from './projectProgress';

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
  /** 为 true 时允许进入预览（由「完成编辑」或历史项目加载设置） */
  previewUnlocked?: boolean;
}): WorkflowProgress {
  const slides = args.finalSlides ?? [];
  const hasScript = slides.length > 0 && slidesHaveGeneratedContent(slides);
  const dataReady = slides.length > 0 && slidesHaveReadyPreview(slides);
  return {
    hasInput: args.projectId != null,
    hasOutline: !!args.outlineData,
    /** 已生成过正文讲稿（大纲页「生成内容」之后） */
    hasContent: hasScript,
    /** 须在内容页完成编辑并提炼预览，或从历史加载已提炼项目 */
    hasPreview: !!args.previewUnlocked && dataReady,
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
