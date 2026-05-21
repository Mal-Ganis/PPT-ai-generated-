import type { SlideData } from '../App';
import { isStructuralSlideData } from './structuralSlide';

export function bulletsEqual(a: string[], b: string[]): boolean {
  if (a.length !== b.length) return false;
  return a.every((line, i) => line.trim() === b[i]?.trim());
}

const OUTLINE_PLACEHOLDER = '（待编辑要点）';

function lineHasText(line: string | undefined | null): boolean {
  const t = line?.trim() ?? '';
  return t.length > 0 && t !== OUTLINE_PLACEHOLDER;
}

/** 讲稿已有内容但尚未提炼出独立的 PPT 要点（或 ppt 与讲稿完全相同视为未提炼） */
export function slideNeedsPptExtraction(slide: SlideData): boolean {
  if (slide.content.length === 0) return false;
  if (slide.pptContent.length === 0) return true;
  return bulletsEqual(slide.pptContent, slide.content);
}

export function projectNeedsPptExtraction(slides: SlideData[]): boolean {
  return slides.some(slideNeedsPptExtraction);
}

function slideHasDistinctPpt(slide: SlideData): boolean {
  const hasScript = slide.content.some(lineHasText);
  const hasPpt = slide.pptContent.some(lineHasText);
  if (!hasScript || !hasPpt) return false;
  return !bulletsEqual(slide.pptContent, slide.content);
}

/**
 * 是否已完成「内容页 → 完成编辑」后的全稿 PPT 提炼。
 * 骨架页（封面/目录）在生成正文时会有 ppt，不计入；要求每一页正文页均有独立 ppt 要点。
 */
export function slidesHaveReadyPreview(slides: SlideData[]): boolean {
  const bodySlides = slides.filter(
    (s) => !isStructuralSlideData(s) && s.content.some(lineHasText),
  );
  if (bodySlides.length === 0) return false;
  return bodySlides.every(slideHasDistinctPpt);
}
