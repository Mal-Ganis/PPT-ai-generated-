/** 从知识检索返回内容/预览编辑页时恢复页码 */

export type CitationFlowStep = 'content' | 'preview';

export interface CitationReturnContext {
  step: CitationFlowStep;
  projectId: number;
  slideIndex: number;
  slideId?: number;
  savedAt: number;
}

const STORAGE_KEY = 'ppt-citation-return';

export function saveCitationReturnContext(
  ctx: Omit<CitationReturnContext, 'savedAt'>,
): void {
  try {
    sessionStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ ...ctx, savedAt: Date.now() } satisfies CitationReturnContext),
    );
  } catch {
    // ignore
  }
}

export function consumeCitationReturnContext(): CitationReturnContext | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    sessionStorage.removeItem(STORAGE_KEY);
    const parsed = JSON.parse(raw) as CitationReturnContext;
    if (!parsed?.projectId || parsed.slideIndex == null) return null;
    if (Date.now() - (parsed.savedAt ?? 0) > 30 * 60 * 1000) return null;
    return parsed;
  } catch {
    return null;
  }
}
