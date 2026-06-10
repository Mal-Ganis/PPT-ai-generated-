import type { ReactNode } from 'react';

/** 从检索词提取用于字面高亮的短语/词（中英文） */
export function extractSearchHighlightTerms(query: string): string[] {
  const trimmed = query.trim();
  if (!trimmed) return [];

  const set = new Set<string>();
  if (trimmed.length >= 2) {
    set.add(trimmed);
  }

  trimmed
    .split(/[\s,，。；;！!？?、·「」【】()（）\[\]{}<>《》/\\|]+/)
    .map((t) => t.trim())
    .filter((t) => t.length >= 2)
    .forEach((t) => set.add(t));

  // 较长中文连续句：补充 2 字词便于看到部分重合
  for (const run of trimmed.match(/[\u4e00-\u9fff]{4,}/g) ?? []) {
    for (let i = 0; i < run.length - 1; i++) {
      set.add(run.slice(i, i + 2));
    }
  }

  // 英文/数字词
  for (const word of trimmed.match(/[a-zA-Z0-9][a-zA-Z0-9_-]{1,}/g) ?? []) {
    set.add(word);
  }

  return [...set].sort((a, b) => b.length - a.length);
}

export function countHighlightMatches(text: string, terms: string[]): number {
  if (!text || terms.length === 0) return 0;
  const regex = buildHighlightRegex(terms);
  if (!regex) return 0;
  const matches = text.match(regex);
  return matches?.length ?? 0;
}

function buildHighlightRegex(terms: string[]): RegExp | null {
  const escaped = terms
    .filter((t) => t.length >= 2)
    .sort((a, b) => b.length - a.length)
    .map((t) => t.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
  if (escaped.length === 0) return null;
  return new RegExp(`(${escaped.join('|')})`, 'gi');
}

/** 将文本中与检索词重合的部分用 mark 高亮 */
export function renderHighlightedText(text: string, terms: string[]): ReactNode {
  if (!text) return null;
  const regex = buildHighlightRegex(terms);
  if (!regex) return text;

  const parts = text.split(regex);
  return parts.map((part, index) => {
    if (!part) return null;
    const isHighlight = index % 2 === 1;
    if (isHighlight) {
      return (
        <mark
          key={`${index}-${part.slice(0, 12)}`}
          className="bg-amber-200 text-amber-950 rounded-sm px-0.5 font-medium"
        >
          {part}
        </mark>
      );
    }
    return <span key={`${index}-t`}>{part}</span>;
  });
}
