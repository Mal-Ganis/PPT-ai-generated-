import { cn } from '@/lib/utils';

interface SlideIdLabelProps {
  slideId?: number | null;
  className?: string;
  /** 无 slideId 时是否显示「未同步」提示 */
  showPending?: boolean;
}

/** 预览/编辑页展示后端 slideId，便于单页评估与高级编辑 */
export function SlideIdLabel({ slideId, className, showPending = true }: SlideIdLabelProps) {
  if (slideId == null) {
    if (!showPending) return null;
    return (
      <span className={cn('text-xs text-amber-700/80', className)} title="请先同步大纲后再生成正文">
        slideId 未同步
      </span>
    );
  }
  return (
    <span
      className={cn('text-xs font-mono tabular-nums text-[#1f1f1f]/45', className)}
      title="后端幻灯片 ID（单页评估、高级编辑）"
    >
      slideId {slideId}
    </span>
  );
}
