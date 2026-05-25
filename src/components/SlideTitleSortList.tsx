import { useState, type DragEvent } from 'react';
import { AlertCircle, GripVertical } from 'lucide-react';
import { cn } from '@/lib/utils';
import type { SlideData } from '../App';

export interface SlideTitleSortListProps {
  slides: SlideData[];
  currentIndex: number;
  onSelect: (index: number) => void;
  onReorder: (fromIndex: number, toIndex: number) => void;
  disabled?: boolean;
  /** 侧栏竖排（内容页）或顶栏横排（预览页） */
  layout?: 'vertical' | 'horizontal';
  /** 返回 true 时在侧栏标题旁标「待补引用」 */
  needsCitationAttention?: (slide: SlideData, index: number) => boolean;
}

/**
 * 通过拖拽标题行调整页面顺序。拖拽手柄在标题左侧，点击标题选中该页。
 */
export function SlideTitleSortList({
  slides,
  currentIndex,
  onSelect,
  onReorder,
  disabled = false,
  layout = 'vertical',
  needsCitationAttention,
}: SlideTitleSortListProps) {
  const [dragFrom, setDragFrom] = useState<number | null>(null);
  const [dropTarget, setDropTarget] = useState<number | null>(null);

  const finishDrag = () => {
    setDragFrom(null);
    setDropTarget(null);
  };

  const handleDrop = (toIndex: number) => {
    if (disabled || dragFrom == null || dragFrom === toIndex) {
      finishDrag();
      return;
    }
    onReorder(dragFrom, toIndex);
    finishDrag();
  };

  const isVertical = layout === 'vertical';

  const startDrag = (e: DragEvent, index: number) => {
    if (disabled) return;
    setDragFrom(index);
    e.dataTransfer.effectAllowed = 'move';
    e.dataTransfer.setData('text/plain', String(index));
  };

  return (
    <div
      className={cn(
        isVertical ? 'flex flex-col gap-2' : 'flex items-center gap-2 overflow-x-auto pb-1',
      )}
      role="list"
      aria-label="幻灯片列表，可拖拽标题排序"
    >
      {slides.map((slide, index) => {
        const isActive = index === currentIndex;
        const isDropHint = dropTarget === index && dragFrom != null && dragFrom !== index;
        const needsCitation = needsCitationAttention?.(slide, index) ?? false;

        return (
          <div
            key={slide.slideId ?? slide.id}
            role="listitem"
            className={cn(
              'flex items-center gap-2 min-w-0 transition-colors',
              isVertical ? 'w-full' : 'shrink-0',
              isDropHint && 'ring-2 ring-[#3898ec] ring-offset-1 rounded-xl',
            )}
            onDragOver={(e) => {
              if (disabled || dragFrom == null) return;
              e.preventDefault();
              e.dataTransfer.dropEffect = 'move';
              setDropTarget(index);
            }}
            onDragLeave={() => {
              if (dropTarget === index) setDropTarget(null);
            }}
            onDrop={(e) => {
              e.preventDefault();
              handleDrop(index);
            }}
          >
            <span
              draggable={!disabled}
              onDragStart={(e) => startDrag(e, index)}
              onDragEnd={finishDrag}
              className={cn(
                'flex items-center justify-center shrink-0 touch-none',
                isVertical ? 'p-2' : 'p-1.5',
                disabled
                  ? 'opacity-30 cursor-not-allowed'
                  : 'cursor-grab active:cursor-grabbing text-[#1f1f1f]/35 hover:text-[#3898ec]',
              )}
              title="拖拽以调整顺序"
              aria-label={`拖拽调整第 ${index + 1} 页顺序`}
            >
              <GripVertical className={isVertical ? 'w-4 h-4' : 'w-3.5 h-3.5'} />
            </span>

            <button
              type="button"
              disabled={disabled}
              draggable={!disabled}
              onDragStart={(e) => startDrag(e, index)}
              onDragEnd={finishDrag}
              onClick={() => onSelect(index)}
              className={cn(
                'text-left transition-all duration-300 min-w-0',
                !disabled && 'cursor-grab active:cursor-grabbing',
                isVertical
                  ? cn(
                      'flex-1 p-4 rounded-xl',
                      isActive
                        ? 'bg-[#3898ec] text-white shadow-lg'
                        : needsCitation
                          ? 'bg-amber-50 hover:bg-amber-100/80 text-[#1f1f1f] border border-amber-200'
                          : 'bg-white hover:bg-gray-50 text-[#1f1f1f]',
                    )
                  : cn(
                      'px-3 py-1.5 rounded-lg text-sm',
                      isActive
                        ? 'bg-[#3898ec] text-white'
                        : needsCitation
                          ? 'bg-amber-50 text-amber-950 border border-amber-200 hover:bg-amber-100/80'
                          : 'bg-white text-[#1f1f1f]/70 hover:bg-gray-50',
                    ),
              )}
            >
              {isVertical ? (
                <div className="flex items-center gap-3">
                  <span
                    className={cn(
                      'w-6 h-6 rounded-full flex items-center justify-center text-xs font-medium shrink-0',
                      isActive ? 'bg-white text-[#3898ec]' : 'bg-[#3898ec]/10 text-[#3898ec]',
                    )}
                  >
                    {index + 1}
                  </span>
                  <span className="font-medium truncate flex-1 min-w-0">{slide.title}</span>
                  {needsCitation && !isActive && (
                    <span
                      className="shrink-0 inline-flex items-center gap-0.5 text-[10px] font-medium text-amber-800 bg-amber-100 px-1.5 py-0.5 rounded-full"
                      title="待补充引用或核实要点"
                    >
                      <AlertCircle className="w-3 h-3" />
                      待补引用
                    </span>
                  )}
                </div>
              ) : (
                <span className="whitespace-nowrap inline-flex items-center gap-1">
                  {index + 1}. {slide.title}
                  {needsCitation && (
                    <AlertCircle className="w-3.5 h-3.5 text-amber-600" aria-label="待补引用" />
                  )}
                </span>
              )}
            </button>
          </div>
        );
      })}
    </div>
  );
}
