/** 要点数组 → 文本框展示（一行一条，保留行内空格） */
export function bulletsToEditableText(bullets: string[] | undefined): string {
  if (!bullets?.length) return '';
  return bullets.join('\n');
}

/** 文本框内容 → 保存到后端的要点（去掉首尾空白行，行尾空白） */
export function editableTextToBullets(text: string): string[] {
  if (!text) return [];
  return text
    .split(/\r?\n/)
    .map((line) => line.trimEnd())
    .filter((line) => line.length > 0);
}
