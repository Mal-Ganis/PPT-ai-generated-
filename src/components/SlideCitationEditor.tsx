import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, BookOpen, Loader2, Save } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import {
  citationAttentionSummary,
  editableTextToSources,
  slideNeedsCitationAttention,
  sourcesToEditableText,
} from '@/lib/citationHints';

interface SlideCitationEditorProps {
  projectId: number;
  slideId: number | undefined;
  content: string[];
  sources: string[] | undefined;
  disabled?: boolean;
  onSourcesChange: (sources: string[]) => void;
  onPersist: (sources: string[]) => Promise<void>;
  className?: string;
}

export function SlideCitationEditor({
  projectId,
  slideId,
  content,
  sources,
  disabled,
  onSourcesChange,
  onPersist,
  className,
}: SlideCitationEditorProps) {
  const [draft, setDraft] = useState(() => sourcesToEditableText(sources ?? []));
  const [saveHint, setSaveHint] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setDraft(sourcesToEditableText(sources ?? []));
    setSaveHint('');
  }, [slideId, sources]);

  const needsAttention = slideNeedsCitationAttention(content, sources);
  const summary = citationAttentionSummary(content, sources);

  const handleSave = async () => {
    if (slideId == null) return;
    const next = editableTextToSources(draft);
    onSourcesChange(next);
    setSaving(true);
    setSaveHint('保存中…');
    try {
      await onPersist(next);
      setSaveHint('引用已保存');
    } catch {
      setSaveHint('保存失败');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className={className ?? 'mt-6 pt-6 border-t border-gray-200'}>
      <div className="flex flex-wrap items-center justify-between gap-2 mb-2">
        <h4 className="text-sm font-semibold text-[#1f1f1f] flex items-center gap-1.5">
          <BookOpen className="w-4 h-4 text-[#3898ec]" />
          引用来源
          {needsAttention && (
            <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-amber-100 text-amber-900">
              待补充
            </span>
          )}
        </h4>
        <div className="flex items-center gap-2">
          <Button
            type="button"
            size="sm"
            variant="outline"
            className="h-8 text-xs"
            asChild
            disabled={disabled}
          >
            <Link to="/knowledge">去知识检索</Link>
          </Button>
          <Button
            type="button"
            size="sm"
            className="h-8 text-xs bg-[#3898ec] hover:bg-[#0082f3] text-white"
            disabled={disabled || slideId == null || saving}
            onClick={() => void handleSave()}
          >
            {saving ? (
              <Loader2 className="w-3.5 h-3.5 mr-1 animate-spin" />
            ) : (
              <Save className="w-3.5 h-3.5 mr-1" />
            )}
            保存引用
          </Button>
        </div>
      </div>

      {summary && (
        <div className="mb-3 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2.5 text-sm text-amber-950 flex gap-2">
          <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5 text-amber-600" />
          <p>{summary}</p>
        </div>
      )}

      <p className="text-xs text-[#1f1f1f]/55 mb-2 leading-relaxed">
        每行一条：链接（https://…）、文献条目，或「标题 | 网址」。与讲稿中「[待核实]」要点尽量一一对应。
      </p>
      <Textarea
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        onBlur={() => {
          if (slideId == null) return;
          const next = editableTextToSources(draft);
          onSourcesChange(next);
        }}
        disabled={disabled || slideId == null}
        placeholder={
          '示例：\n新华社 | https://...\n项目文档片段 1 | 节选：…… | type=index'
        }
        className="min-h-[88px] text-sm border-gray-200 font-mono leading-relaxed"
      />
      {saveHint ? <p className="text-xs text-[#1f1f1f]/50 mt-1.5">{saveHint}</p> : null}
      <p className="text-xs text-[#1f1f1f]/45 mt-1">
        项目 ID {projectId}
        {slideId != null ? ` · 第 ${slideId} 页（数据库）` : ' · 当前页尚未同步到服务器'}
      </p>
    </div>
  );
}
