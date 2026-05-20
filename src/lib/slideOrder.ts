import type { SlideData } from '../App';

export function reorderSlidesArray(
  slides: SlideData[],
  fromIndex: number,
  toIndex: number,
): SlideData[] {
  if (fromIndex === toIndex || fromIndex < 0 || toIndex < 0) return slides;
  if (fromIndex >= slides.length || toIndex >= slides.length) return slides;
  const next = [...slides];
  const [moved] = next.splice(fromIndex, 1);
  next.splice(toIndex, 0, moved);
  return next;
}

export function slideAnchor(slide: SlideData | undefined): { slideId?: number; id: number } | null {
  if (!slide) return null;
  return { slideId: slide.slideId, id: slide.id };
}

export function findSlideIndexByAnchor(
  slides: SlideData[],
  anchor: { slideId?: number; id: number } | null,
): number {
  if (!anchor) return -1;
  if (anchor.slideId != null) {
    const bySlideId = slides.findIndex((s) => s.slideId === anchor.slideId);
    if (bySlideId >= 0) return bySlideId;
  }
  return slides.findIndex((s) => s.id === anchor.id);
}
