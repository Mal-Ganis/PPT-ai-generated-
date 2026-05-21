import type { SlideData } from '../App';

/** 与后端 StructuralSlideDetector 对齐：封面 / 目录 / 问答讨论等骨架页 */
export function isStructuralSlide(title: string, chapter?: string): boolean {
  return isCover(title, chapter) || isTableOfContents(title, chapter) || isQaOrDiscussion(title, chapter);
}

export function isStructuralSlideData(slide: SlideData): boolean {
  return isStructuralSlide(slide.title, slide.chapter);
}

function isCover(title: string, chapter?: string): boolean {
  if (chapter?.trim()) {
    const c = chapter.trim();
    if (c === '封面' || c.includes('扉页')) return true;
  }
  const t = title?.trim() ?? '';
  if (!t) return false;
  return (
    t.includes('封面') ||
    t.includes('扉页') ||
    /\bcover\b/i.test(t) ||
    /title\s*slide|opening/i.test(t)
  );
}

function isTableOfContents(title: string, chapter?: string): boolean {
  if (chapter?.trim()) {
    const c = chapter.trim();
    if (c === '目录' || c === '目次') return true;
  }
  const t = title?.trim() ?? '';
  if (!t) return false;
  return t.includes('目录') || t.includes('目次') || /contents|agenda|outline/i.test(t);
}

function isQaOrDiscussion(title: string, chapter?: string): boolean {
  if (matchesQa(title)) return true;
  return chapter != null && matchesQa(chapter);
}

function matchesQa(text: string): boolean {
  const t = text.trim();
  if (!t) return false;
  if (t.includes('问题与讨论') || t.includes('问答') || t.includes('答疑')) return true;
  if (t.includes('讨论') && (t.includes('问题') || t.includes('交流') || t.includes('互动'))) {
    return true;
  }
  return (
    /\bq\s*&?\s*a\b/i.test(t) ||
    t.includes('Q&A') ||
    t.includes('Q＆A') ||
    /questions?\s*(and|&)\s*answers?|discussion/i.test(t)
  );
}
