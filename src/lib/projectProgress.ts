import type { OutlineData, SlideData } from '../App';
import type { ProjectDetailResponse } from './backend';
import { slidesHaveReadyPreview } from './pptExtraction';
import { mapDetailToSlides } from './slideMappers';
import { isStructuralSlide } from './structuralSlide';
import type { WorkflowProgress, WorkflowStep } from './workflowSteps';

const OUTLINE_PLACEHOLDER = '（待编辑要点）';

function lineHasText(line: string | undefined | null): boolean {
  const t = line?.trim() ?? '';
  return t.length > 0 && t !== OUTLINE_PLACEHOLDER;
}

/** 是否有大纲要点（库里有 bullets 即可，含仅大纲未生成正文） */
export function slidesHaveOutlineBullets(slides: SlideData[]): boolean {
  return slides.some((s) => s.content.some(lineHasText));
}

/**
 * 是否已执行过「生成内容」：正文阶段会写入引用来源；仅有大纲时通常无 sources。
 */
function slideHasGeneratedContent(slide: SlideData): boolean {
  if (isStructuralSlide(slide.title, slide.chapter)) return false;
  if (!slide.content.some(lineHasText)) return false;
  return (slide.sources ?? []).some(lineHasText);
}

export function slidesHaveGeneratedContent(slides: SlideData[]): boolean {
  return slides.some(slideHasGeneratedContent);
}

/** @deprecated 语义过宽，请用 slidesHaveGeneratedContent */
export function slidesHaveScript(slides: SlideData[]): boolean {
  return slidesHaveGeneratedContent(slides);
}

export function detailHasOutline(detail: ProjectDetailResponse): boolean {
  return (detail.slides ?? []).length > 0;
}

export function detailHasGeneratedContent(detail: ProjectDetailResponse): boolean {
  return slidesHaveGeneratedContent(mapDetailToSlides(detail));
}

/** @deprecated 请用 detailHasGeneratedContent */
export function detailHasScript(detail: ProjectDetailResponse): boolean {
  return detailHasGeneratedContent(detail);
}

export function detailHasPpt(detail: ProjectDetailResponse): boolean {
  return (detail.slides ?? []).some((s) =>
    (s.pptBullets ?? []).some(lineHasText),
  );
}

export function detailHasReadyPreview(detail: ProjectDetailResponse): boolean {
  return slidesHaveReadyPreview(mapDetailToSlides(detail));
}

/** 根据数据库状态推断应打开的工作流步骤 */
export function resolveStepFromProjectDetail(detail: ProjectDetailResponse): WorkflowStep {
  if (detailHasReadyPreview(detail)) return 'preview';
  if (detailHasGeneratedContent(detail)) return 'content';
  return 'outline';
}

export function projectStageLabel(detail: ProjectDetailResponse): string {
  if (detailHasReadyPreview(detail)) return '可预览';
  if (detailHasGeneratedContent(detail)) return '已有正文';
  return '仅大纲';
}

export function workflowProgressFromState(args: {
  projectId: number | null;
  outlineData: OutlineData | null;
  finalSlides: SlideData[] | null;
  previewUnlocked?: boolean;
}): WorkflowProgress {
  const slides = args.finalSlides ?? [];
  const hasGenerated = slides.length > 0 && slidesHaveGeneratedContent(slides);
  const dataReady = slides.length > 0 && slidesHaveReadyPreview(slides);
  return {
    hasInput: args.projectId != null,
    hasOutline: !!args.outlineData,
    hasContent: hasGenerated,
    hasPreview: !!args.previewUnlocked && dataReady,
  };
}

export function resolveOpenStep(
  detail: ProjectDetailResponse,
  preferred?: WorkflowStep,
): WorkflowStep {
  const best = resolveStepFromProjectDetail(detail);
  if (!preferred || preferred === 'input') return best;
  if (preferred === 'outline') return 'outline';
  if (preferred === 'content') {
    return detailHasGeneratedContent(detail) ? 'content' : 'outline';
  }
  if (preferred === 'preview') {
    if (detailHasReadyPreview(detail)) return 'preview';
    if (detailHasGeneratedContent(detail)) return 'content';
    return 'outline';
  }
  return best;
}
