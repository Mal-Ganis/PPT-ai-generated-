import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, BookOpen, Check, Loader2, Plus, RefreshCw, Save, Search } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import {
  citationAttentionSummary,
  editableTextToSources,
  slideNeedsCitationAttention,
  sourcesReadyForRegeneration,
  sourcesToEditableText,
} from '@/lib/citationHints';
import { regenerateSlideFromSources } from '@/lib/backend';
import { toast } from 'sonner';
import {
  listPendingCitations,
  removePendingCitation,
  type PendingCitation,
} from '@/lib/citationClipboard';
import type { CitationFlowStep } from '@/lib/citationReturnContext';
import {
  buildKnowledgeSearchUrl,
  bulletSearchQuery,
  stripVerificationMarks,
} from '@/lib/citationFormat';
import { saveCitationReturnContext } from '@/lib/citationReturnContext';

const PENDING_VERIFICATION = /\[待核实\]|【待核实】|\[待补充权威来源\]/;

interface SlideCitationEditorProps {
  projectId: number;
  slideId: number | undefined;
  slideTitle?: string;
  slideIndex: number;
  flowStep: CitationFlowStep;
  deckTheme?: string;
  content: string[];
  sources: string[] | undefined;
  disabled?: boolean;
  onSourcesChange: (sources: string[]) => void;
  onPersist: (sources: string[]) => Promise<void>;
  onVerifyBullet?: (bulletIndex: number) => void;
  onRegenerated?: (patch: {
    content: string[];
    pptContent: string[];
    sources: string[];
  }) => void;
  className?: string;
}

export function SlideCitationEditor({
  projectId,
  slideId,
  slideTitle,
  slideIndex,
  flowStep,
  deckTheme,
  content,
  sources,
  disabled,
  onSourcesChange,
  onPersist,
  onVerifyBullet,
  onRegenerated,
  className,
}: SlideCitationEditorProps) {
  const [draft, setDraft] = useState(() => sourcesToEditableText(sources ?? []));
  const [saveHint, setSaveHint] = useState('');
  const [saving, setSaving] = useState(false);
  const [regenerating, setRegenerating] = useState(false);
  const [pending, setPending] = useState<PendingCitation[]>([]);

  const refreshPending = () => {
    if (slideId == null) {
      setPending([]);
      return;
    }
    setPending(listPendingCitations(projectId, slideId));
  };

  useEffect(() => {
    setDraft(sourcesToEditableText(sources ?? []));
    setSaveHint('');
    refreshPending();
  }, [slideId, sources, projectId]);

  const needsAttention = slideNeedsCitationAttention(content, sources);
  const summary = citationAttentionSummary(content, sources);
  const draftSources = editableTextToSources(draft);
  const canRegenerateFromSources =
    slideId != null && sourcesReadyForRegeneration(draftSources) && onRegenerated != null;

  const pendingBullets = content
    .map((line, index) => ({ line, index }))
    .filter(({ line }) => PENDING_VERIFICATION.test(line));

  const appendLines = (lines: string[]) => {
    const existing = editableTextToSources(draft);
    const merged = [...existing];
    for (const line of lines) {
      if (!merged.includes(line)) merged.push(line);
    }
    const text = merged.join('\n');
    setDraft(text);
    onSourcesChange(merged);
    return merged;
  };

  const insertPending = async (item: PendingCitation) => {
    const merged = appendLines([item.line]);
    removePendingCitation(projectId, slideId!, item.line);
    refreshPending();
    if (slideId != null) {
      setSaving(true);
      try {
        await onPersist(merged);
        setSaveHint('已插入并保存引用');
        if (item.bulletIndex != null && onVerifyBullet) {
          onVerifyBullet(item.bulletIndex);
        }
      } catch {
        setSaveHint('已插入，但保存失败，请点「保存引用」');
      } finally {
        setSaving(false);
      }
    }
  };

  const insertAllPending = async () => {
    if (!pending.length || slideId == null) return;
    const lines = pending.map((p) => p.line);
    const merged = appendLines(lines);
    for (const item of pending) {
      removePendingCitation(projectId, slideId, item.line);
    }
    refreshPending();
    setSaving(true);
    try {
      await onPersist(merged);
      setSaveHint(`已插入 ${lines.length} 条引用并保存`);
    } catch {
      setSaveHint('已插入，但保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleRegenerateFromSources = async () => {
    if (slideId == null || !onRegenerated) return;
    const nextSources = editableTextToSources(draft);
    if (!sourcesReadyForRegeneration(nextSources)) {
      toast.error('请先保存至少一条有效引用（含链接或 type=index）');
      return;
    }
    if (
      !confirm(
        '将根据当前标题与引用来源重新生成本页讲稿和 PPT 投影要点，原有要点将被替换。引用列表保持不变。确定继续？',
      )
    ) {
      return;
    }
    setRegenerating(true);
    setSaveHint('正在按引用重生…');
    try {
      onSourcesChange(nextSources);
      await onPersist(nextSources);
      const result = await regenerateSlideFromSources(projectId, slideId, {
        title: slideTitle,
        sources: nextSources,
        inputContent: deckTheme,
        previousContent: content,
      });
      const patch = {
        content: result.content?.length ? result.content : content,
        pptContent: result.pptBullets?.length ? result.pptBullets : [],
        sources: result.sources?.length ? result.sources : nextSources,
      };
      onRegenerated(patch);
      setSaveHint('已按引用重生本页内容');
      toast.success('本页讲稿与 PPT 要点已根据引用重新生成');
    } catch {
      setSaveHint('按引用重生失败');
    } finally {
      setRegenerating(false);
    }
  };

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

  const rememberReturn = () => {
    saveCitationReturnContext({
      step: flowStep,
      projectId,
      slideIndex,
      slideId,
    });
  };

  const knowledgeBaseUrl =
    slideId != null
      ? buildKnowledgeSearchUrl({
          projectId,
          slideId,
          slideIndex,
          flowStep,
          query: slideTitle ?? '',
        })
      : '/knowledge';

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
        <div className="flex items-center gap-2 flex-wrap justify-end">
          <Button
            type="button"
            size="sm"
            variant="outline"
            className="h-8 text-xs"
            asChild
            disabled={disabled || regenerating}
          >
            <Link to={knowledgeBaseUrl} onClick={rememberReturn}>
              去知识检索
            </Link>
          </Button>
          {canRegenerateFromSources && (
            <Button
              type="button"
              size="sm"
              variant="outline"
              className="h-8 text-xs border-[#3898ec]/40 text-[#3898ec] hover:bg-[#3898ec]/5"
              disabled={disabled || saving || regenerating}
              onClick={() => void handleRegenerateFromSources()}
            >
              {regenerating ? (
                <Loader2 className="w-3.5 h-3.5 mr-1 animate-spin" />
              ) : (
                <RefreshCw className="w-3.5 h-3.5 mr-1" />
              )}
              按引用重生本页
            </Button>
          )}
          <Button
            type="button"
            size="sm"
            className="h-8 text-xs bg-[#3898ec] hover:bg-[#0082f3] text-white"
            disabled={disabled || slideId == null || saving || regenerating}
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
          <div>
            <p>{summary}</p>
            <p className="text-xs mt-1.5 text-amber-800/90">
              引用按「页」保存，不会自动与每条要点一一绑定。建议：对含「待核实」的要点逐条点「检索此条」→
              在检索页「追加到本页引用」→ 回到此处「插入」并确认对应关系。
            </p>
          </div>
        </div>
      )}

      {pendingBullets.length > 0 && slideId != null && (
        <div className="mb-3 rounded-xl border border-[#3898ec]/20 bg-[#3898ec]/5 px-3 py-2.5 space-y-2">
          <p className="text-xs font-medium text-[#1f1f1f]/70">待核实要点（建议逐条检索）</p>
          {pendingBullets.map(({ line, index }) => (
            <div
              key={index}
              className="flex flex-col sm:flex-row sm:items-center gap-2 text-sm border-b border-[#3898ec]/10 last:border-0 pb-2 last:pb-0"
            >
              <span className="flex-1 text-[#1f1f1f]/85 leading-snug">
                {index + 1}. {stripVerificationMarks(line)}
              </span>
              <div className="flex gap-1 shrink-0">
                <Button type="button" size="sm" variant="outline" className="h-7 text-xs" asChild>
                  <Link
                    to={buildKnowledgeSearchUrl({
                      projectId,
                      slideId,
                      slideIndex,
                      flowStep,
                      bulletIndex: index,
                      query: bulletSearchQuery(line, slideTitle),
                    })}
                    onClick={rememberReturn}
                  >
                    <Search className="w-3 h-3 mr-1" />
                    检索此条
                  </Link>
                </Button>
                {onVerifyBullet && (
                  <Button
                    type="button"
                    size="sm"
                    variant="ghost"
                    className="h-7 text-xs text-emerald-700 hover:text-emerald-800 hover:bg-emerald-50"
                    disabled={disabled}
                    onClick={() => onVerifyBullet(index)}
                    title="已人工核对，去掉「待核实」标记"
                  >
                    <Check className="w-3 h-3 mr-1" />
                    已核实
                  </Button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {pending.length > 0 && slideId != null && (
        <div className="mb-3 rounded-xl border border-emerald-200 bg-emerald-50/80 px-3 py-2.5 space-y-2">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="text-sm font-medium text-emerald-950">
              知识检索待插入（{pending.length} 条）
            </p>
            <Button
              type="button"
              size="sm"
              className="h-7 text-xs bg-emerald-600 hover:bg-emerald-700 text-white"
              disabled={disabled || saving}
              onClick={() => void insertAllPending()}
            >
              <Plus className="w-3 h-3 mr-1" />
              全部插入并保存
            </Button>
          </div>
          <ul className="space-y-1.5">
            {pending.map((item) => (
              <li
                key={item.line}
                className="flex flex-col sm:flex-row sm:items-start gap-2 text-xs bg-white/70 rounded-lg px-2 py-1.5 border border-emerald-100"
              >
                <code className="flex-1 text-[#1f1f1f]/80 break-all font-mono">{item.line}</code>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  className="h-7 text-xs shrink-0"
                  disabled={disabled || saving}
                  onClick={() => void insertPending(item)}
                >
                  插入本条
                </Button>
              </li>
            ))}
          </ul>
        </div>
      )}

      <p className="text-xs text-[#1f1f1f]/55 mb-2 leading-relaxed">
        每行一条：链接（https://…）或「标题 | 网址 | type=index」。与要点对应关系需人工确认；插入后可在上方点「已核实」去掉
        [待核实]。
      </p>
      <Textarea
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        onBlur={() => {
          if (slideId == null) return;
          onSourcesChange(editableTextToSources(draft));
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
        {slideId != null ? ` · 幻灯片 ${slideId}` : ' · 当前页尚未同步到服务器'}
      </p>
    </div>
  );
}
