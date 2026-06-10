/** 知识检索结果暂存，供内容/预览页「引用来源」一键插入 */

export interface PendingCitation {
  projectId: number;
  slideId: number;
  /** 写入 sources 的一行文本（与后端 SlideSourceCitationService 格式一致） */
  line: string;
  snippet?: string;
  bulletIndex?: number;
  addedAt: number;
}

const STORAGE_KEY = 'ppt-pending-citations';

function readAll(): PendingCitation[] {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as PendingCitation[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function writeAll(items: PendingCitation[]): void {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(items));
}

export function listPendingCitations(projectId: number, slideId: number): PendingCitation[] {
  return readAll()
    .filter((c) => c.projectId === projectId && c.slideId === slideId)
    .sort((a, b) => a.addedAt - b.addedAt);
}

export function pushPendingCitation(
  item: Omit<PendingCitation, 'addedAt'>,
): PendingCitation[] {
  const entry: PendingCitation = { ...item, addedAt: Date.now() };
  const all = readAll();
  const exists = all.some(
    (c) =>
      c.projectId === entry.projectId &&
      c.slideId === entry.slideId &&
      c.line === entry.line,
  );
  if (!exists) {
    all.push(entry);
    writeAll(all);
  }
  return listPendingCitations(entry.projectId, entry.slideId);
}

export function removePendingCitation(
  projectId: number,
  slideId: number,
  line: string,
): PendingCitation[] {
  const next = readAll().filter(
    (c) => !(c.projectId === projectId && c.slideId === slideId && c.line === line),
  );
  writeAll(next);
  return listPendingCitations(projectId, slideId);
}

export function clearPendingCitations(projectId: number, slideId: number): void {
  const next = readAll().filter((c) => !(c.projectId === projectId && c.slideId === slideId));
  writeAll(next);
}

/** 迁移旧版单条 sessionStorage */
export function migrateLegacySelectedCitation(): void {
  const legacy = sessionStorage.getItem('ppt-selected-citation');
  if (!legacy) return;
  try {
    const o = JSON.parse(legacy) as {
      segmentId?: string;
      snippet?: string;
      url?: string;
      title?: string;
    };
    sessionStorage.removeItem('ppt-selected-citation');
    if (!o.url && !o.snippet) return;
    const line = o.url
      ? `${o.title || o.segmentId || '检索片段'} | ${o.url} | type=index`
      : `检索片段 | 节选：${(o.snippet ?? '').slice(0, 72)} | type=index`;
    pushPendingCitation({ projectId: 0, slideId: 0, line, snippet: o.snippet });
  } catch {
    sessionStorage.removeItem('ppt-selected-citation');
  }
}
