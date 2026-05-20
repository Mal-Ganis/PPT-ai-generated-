import type { SlideData } from '../App';
import {
  fetchProjectForSlides,
  regenerateSlide,
  saveProjectSlides,
  syncProjectOutline,
  updateProjectSlide,
} from './backend';
import { buildOutlinePayload, mapDetailToSlides } from './slideMappers';

/** 同步整份大纲结构（增删页、调序），并尽量保留各页讲稿与 PPT 要点 */
export async function syncProjectSlidesStructure(
  projectId: number,
  deckTitle: string,
  deckTheme: string,
  slides: SlideData[],
): Promise<SlideData[]> {
  const pptSnapshot = slides.map((s) => [...(s.pptContent ?? [])]);
  const contentSnapshot = slides.map((s) => [...s.content]);
  const sourcesSnapshot = slides.map((s) => (s.sources ? [...s.sources] : undefined));
  const chapterSnapshot = slides.map((s) => s.chapter);

  await syncProjectOutline(projectId, buildOutlinePayload(deckTitle, deckTheme, slides));

  const detail = await fetchProjectForSlides(projectId);
  const mapped = mapDetailToSlides(detail);

  const merged = mapped.map((serverSlide, i) => ({
    ...serverSlide,
    title: slides[i]?.title ?? serverSlide.title,
    chapter: chapterSnapshot[i] ?? serverSlide.chapter,
    content:
      contentSnapshot[i]?.length > 0
        ? contentSnapshot[i]
        : serverSlide.content.length > 0
          ? serverSlide.content
          : [],
    pptContent: pptSnapshot[i]?.length ? pptSnapshot[i] : serverSlide.pptContent,
    sources: sourcesSnapshot[i] ?? serverSlide.sources,
  }));

  const toSave = merged.filter((s) => s.slideId != null);
  if (toSave.length > 0) {
    await saveProjectSlides(
      projectId,
      toSave.map((s) => ({
        slideId: s.slideId!,
        bullets: s.content,
        pptBullets: s.pptContent?.length ? s.pptContent : undefined,
      })),
    );
  }

  return merged;
}

export async function addProjectSlide(
  projectId: number,
  deckTitle: string,
  deckTheme: string,
  slides: SlideData[],
  options: {
    inputType: string;
    inputContent: string;
    /** 内容阶段：为新页调用 AI 生成讲稿 */
    regenerateContent?: boolean;
  },
): Promise<{ slides: SlideData[]; newIndex: number }> {
  const newSlide: SlideData = {
    id: Date.now(),
    title: '新页面',
    content: ['新要点'],
    pptContent: [],
  };
  const next = [...slides, newSlide];
  let merged = await syncProjectSlidesStructure(projectId, deckTitle, deckTheme, next);
  const newIndex = merged.length - 1;

  if (options.regenerateContent && merged[newIndex]?.slideId) {
    try {
      const result = await regenerateSlide(projectId, merged[newIndex].slideId!, {
        inputType: options.inputType,
        inputContent: options.inputContent,
      });
      merged = [...merged];
      merged[newIndex] = {
        ...merged[newIndex],
        content: result.content?.length ? result.content : merged[newIndex].content,
        sources: result.sources?.length ? result.sources : merged[newIndex].sources,
        pptContent: [],
      };
      await updateProjectSlide(projectId, merged[newIndex].slideId!, {
        title: merged[newIndex].title,
        bullets: merged[newIndex].content,
      });
    } catch {
      // 保留占位要点，用户可手动编辑
    }
  }

  return { slides: merged, newIndex };
}

/** 调整页面顺序并同步到后端 */
export async function reorderProjectSlides(
  projectId: number,
  deckTitle: string,
  deckTheme: string,
  slides: SlideData[],
): Promise<SlideData[]> {
  return syncProjectSlidesStructure(projectId, deckTitle, deckTheme, slides);
}

export async function deleteProjectSlide(
  projectId: number,
  deckTitle: string,
  deckTheme: string,
  slides: SlideData[],
  slideIndex: number,
): Promise<SlideData[]> {
  if (slides.length <= 1) {
    throw new Error('至少需要保留一页');
  }
  const next = slides.filter((_, i) => i !== slideIndex);
  return syncProjectSlidesStructure(projectId, deckTitle, deckTheme, next);
}

export async function persistSlideTitle(
  projectId: number,
  slide: SlideData,
  title: string,
): Promise<void> {
  if (slide.slideId == null) return;
  await updateProjectSlide(projectId, slide.slideId, { title: title.trim() || slide.title });
}

export async function persistSlideBullets(
  projectId: number,
  slide: SlideData,
): Promise<void> {
  if (slide.slideId == null) return;
  await updateProjectSlide(projectId, slide.slideId, {
    title: slide.title,
    bullets: slide.content,
    pptBullets: slide.pptContent?.length ? slide.pptContent : undefined,
  });
}
