import type { IndexSearchResult } from './backend';
import type { CitationFlowStep } from './citationReturnContext';

import { isPlaceholderSourceLine } from './citationHints';

const URL_PATTERN = /https?:\/\/[^\s|"<>]+/gi;
const JSON_OBJECT_LIKE = /^\s*\{[\s\S]*\}\s*$/;

function parseMeta(raw?: string): { url?: string; title?: string } {
  if (!raw) return {};
  try {
    const o = JSON.parse(raw) as Record<string, unknown>;
    return {
      url: typeof o.url === 'string' ? o.url : undefined,
      title: typeof o.title === 'string' ? o.title : undefined,
    };
  } catch {
    return {};
  }
}

function formatSourceMap(map: Record<string, unknown>): string {
  const title = typeof map.title === 'string' ? map.title.trim() : '';
  const url = typeof map.url === 'string' ? map.url.trim() : '';
  const type = typeof map.type === 'string' ? map.type.trim() : '';
  const parts: string[] = [];
  if (title) parts.push(title);
  if (url) parts.push(url);
  if (type) parts.push(`type=${type}`);
  return parts.join(' | ');
}

/** 与后端 SlideSourceCitationService.normalizeSourceLine 对齐：JSON 对象串 → 「标题 | URL | type=…」 */
export function normalizeSourceLine(line: string): string {
  const trimmed = line.trim();
  if (!trimmed) return '';

  if (JSON_OBJECT_LIKE.test(trimmed)) {
    try {
      const map = JSON.parse(trimmed) as Record<string, unknown>;
      const formatted = formatSourceMap(map);
      if (formatted) return formatted;
    } catch {
      // fall through
    }
  }

  const urlMatch = trimmed.match(URL_PATTERN);
  if (urlMatch && !trimmed.includes(' | ')) {
    const url = urlMatch[0];
    const title = trimmed.replace(url, '').replace(/[\s|：:]+$/g, '').trim();
    return title ? `${title} | ${url}` : url;
  }

  return trimmed;
}

export function normalizeSourceLines(sources: string[]): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const line of sources ?? []) {
    const normalized = normalizeSourceLine(line);
    if (!normalized || seen.has(normalized)) continue;
    if (isPlaceholderSourceLine(normalized)) continue;
    seen.add(normalized);
    out.push(normalized);
  }
  return out;
}

/** 与后端 SlideSourceCitationService.formatIndexHit 对齐 */
export function formatIndexHitAsSourceLine(hit: IndexSearchResult, index = 1): string {
  const meta = parseMeta(hit.metadata);
  const url = meta.url?.trim();
  if (url) {
    const label = hit.segmentId?.trim() || `片段#${index}`;
    return `${label} | ${url} | type=index`;
  }
  const excerpt = (hit.content ?? '').trim().replace(/\s+/g, ' ').slice(0, 72);
  if (!excerpt) return '';
  return `项目文档片段 ${index} | 节选：${excerpt}${excerpt.length >= 72 ? '…' : ''} | type=index`;
}

export function stripVerificationMarks(text: string): string {
  return text
    .replace(/\[待核实\]/g, '')
    .replace(/【待核实】/g, '')
    .replace(/\[待补充权威来源\]/g, '')
    .replace(/\s{2,}/g, ' ')
    .trim();
}

export function bulletSearchQuery(line: string, slideTitle?: string): string {
  const cleaned = stripVerificationMarks(line);
  if (cleaned.length >= 8) return cleaned;
  return slideTitle ? `${slideTitle} ${cleaned}`.trim() : cleaned;
}

export function buildKnowledgeSearchUrl(params: {
  projectId: number;
  slideId?: number;
  slideIndex?: number;
  bulletIndex?: number;
  query: string;
  flowStep?: CitationFlowStep;
}): string {
  const sp = new URLSearchParams();
  sp.set('projectId', String(params.projectId));
  if (params.slideId != null) sp.set('slideId', String(params.slideId));
  if (params.slideIndex != null) sp.set('slideIndex', String(params.slideIndex));
  if (params.bulletIndex != null) sp.set('bullet', String(params.bulletIndex));
  if (params.flowStep) sp.set('flowStep', params.flowStep);
  if (params.query.trim()) sp.set('q', params.query.trim());
  return `/knowledge?${sp.toString()}`;
}
