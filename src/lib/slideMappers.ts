import type { SlideData } from '../App';
import type { ProjectDetailResponse, UpsertOutlinePayload } from './backend';

export function mapDetailToSlides(detail: ProjectDetailResponse): SlideData[] {
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

export function buildOutlinePayload(
  title: string,
  theme: string,
  slides: SlideData[],
): UpsertOutlinePayload {
  return {
    title,
    theme,
    slides: slides.map((s, index) => ({
      position: index + 1,
      chapter: s.chapter ?? null,
      title: s.title.trim() || '未命名页面',
      bullets: s.content,
      sources: s.sources,
    })),
  };
}
