import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { FolderOpen, Loader2, Trash2 } from 'lucide-react';
import { FlowExitNav } from '@/components/FlowExitNav';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import {
  deleteProject,
  deleteProjectsBatch,
  listProjects,
  type ProjectSummary,
} from '@/lib/backend';
import type { WorkflowStep } from '@/lib/workflowSteps';

interface ProjectsSectionProps {
  onOpenProject: (projectId: number, step?: WorkflowStep) => void;
}

type DeleteDialogMode = { kind: 'single'; project: ProjectSummary } | { kind: 'batch'; ids: number[] };

function summaryMeta(p: ProjectSummary) {
  const hasScript = p.hasScript === true;
  const hasPpt = p.hasPpt === true;
  const stage = p.stage ?? (hasPpt ? '可预览' : hasScript ? '已有正文' : '仅大纲');
  return { hasScript, hasPpt, stage };
}

const ProjectsSection = ({ onOpenProject }: ProjectsSectionProps) => {
  const [projects, setProjects] = useState<ProjectSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [deleteDialog, setDeleteDialog] = useState<DeleteDialogMode | null>(null);
  const [deleting, setDeleting] = useState(false);
  const loadGenRef = useRef(0);

  const loadList = useCallback(async () => {
    const gen = ++loadGenRef.current;
    setLoading(true);
    setError('');
    try {
      const data = await listProjects();
      if (loadGenRef.current !== gen) return;
      setProjects(data);
      setSelectedIds((prev) => {
        const valid = new Set(data.map((p) => p.id));
        const next = new Set<number>();
        prev.forEach((id) => {
          if (valid.has(id)) next.add(id);
        });
        return next;
      });
    } catch (e) {
      if (loadGenRef.current !== gen) return;
      const msg = e instanceof Error ? e.message : '加载失败';
      const hint =
        msg.includes('timeout') || msg.includes('Network Error')
          ? '请确认后端已启动（默认 http://localhost:8080）且数据库可连接。'
          : '';
      setError(hint ? `${msg}。${hint}` : msg);
    } finally {
      if (loadGenRef.current === gen) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    void loadList();
  }, [loadList]);

  const allSelected = useMemo(
    () => projects.length > 0 && selectedIds.size === projects.length,
    [projects.length, selectedIds.size],
  );

  const someSelected = selectedIds.size > 0;

  const toggleSelectAll = (checked: boolean) => {
    if (checked) {
      setSelectedIds(new Set(projects.map((p) => p.id)));
    } else {
      setSelectedIds(new Set());
    }
  };

  const openWithStep = (id: number, step?: WorkflowStep) => {
    void onOpenProject(id, step);
  };

  const toggleSelect = (id: number, checked: boolean) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (checked) {
        next.add(id);
      } else {
        next.delete(id);
      }
      return next;
    });
  };

  const confirmDelete = async () => {
    if (!deleteDialog) return;
    setDeleting(true);
    setError('');
    try {
      if (deleteDialog.kind === 'single') {
        await deleteProject(deleteDialog.project.id);
        const removedId = deleteDialog.project.id;
        setProjects((prev) => prev.filter((p) => p.id !== removedId));
        setSelectedIds((prev) => {
          const next = new Set(prev);
          next.delete(removedId);
          return next;
        });
      } else {
        const ids = deleteDialog.ids;
        await deleteProjectsBatch(ids);
        const removed = new Set(ids);
        setProjects((prev) => prev.filter((p) => !removed.has(p.id)));
        setSelectedIds((prev) => {
          const next = new Set(prev);
          ids.forEach((id) => next.delete(id));
          return next;
        });
      }
      setDeleteDialog(null);
    } catch (e) {
      const msg = e instanceof Error ? e.message : '删除失败';
      setError(msg);
    } finally {
      setDeleting(false);
    }
  };

  const batchTitlesPreview = useMemo(() => {
    if (deleteDialog?.kind !== 'batch') return '';
    const titles = projects
      .filter((p) => deleteDialog.ids.includes(p.id))
      .slice(0, 3)
      .map((p) => p.title);
    const more = deleteDialog.ids.length - titles.length;
    if (more > 0) {
      return `${titles.join('、')} 等 ${deleteDialog.ids.length} 项`;
    }
    return titles.join('、');
  }, [deleteDialog, projects]);

  return (
    <section className="min-h-screen pt-24 pb-16 bg-[#f3f3f3]">
      <div className="section-container">
        <div className="section-inner max-w-4xl">
          <FlowExitNav className="mb-4" onProjectsPage />
          <div className="mb-8">
            <h1 className="text-3xl font-bold text-[#1f1f1f]">历史项目</h1>
            <p className="text-[#1f1f1f]/60 mt-2">
              点击项目打开最近进度；可用顶部导航返回首页
            </p>
          </div>

          <div className="bg-white rounded-3xl shadow-lg p-6">
            {loading && (
              <div className="flex items-center gap-2 text-[#1f1f1f]/70">
                <Loader2 className="w-5 h-5 animate-spin" />
                加载项目列表…
              </div>
            )}
            {error && (
              <div className="rounded-2xl bg-red-50 border border-red-200 p-4 text-sm text-red-700 mb-4">
                {error}
              </div>
            )}
            {!loading && !error && projects.length === 0 && (
              <p className="text-[#1f1f1f]/60">暂无项目，请先在首页创建。</p>
            )}
            {!loading && projects.length > 0 && (
              <>
                <div className="flex flex-wrap items-center gap-3 mb-4 pb-4 border-b border-gray-100">
                  <label className="flex items-center gap-2 text-sm text-[#1f1f1f]/70 cursor-pointer select-none">
                    <Checkbox
                      checked={allSelected ? true : someSelected ? 'indeterminate' : false}
                      onCheckedChange={(v) => toggleSelectAll(v === true)}
                    />
                    全选
                  </label>
                  {someSelected && (
                    <span className="text-sm text-[#1f1f1f]/50">已选 {selectedIds.size} 项</span>
                  )}
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    className="ml-auto border-red-200 text-red-600 hover:bg-red-50 hover:text-red-700 disabled:opacity-40"
                    disabled={!someSelected || deleting}
                    onClick={() =>
                      setDeleteDialog({ kind: 'batch', ids: Array.from(selectedIds) })
                    }
                  >
                    <Trash2 className="w-4 h-4 mr-1" />
                    批量删除
                  </Button>
                </div>
                <ul className="space-y-3">
                  {projects.map((p) => {
                    const meta = summaryMeta(p);
                    const checked = selectedIds.has(p.id);
                    return (
                      <li key={p.id}>
                        <div
                          className={`rounded-2xl border transition-colors p-4 ${
                            checked
                              ? 'border-[#3898ec]/50 bg-[#f5f9ff]'
                              : 'border-gray-200 hover:border-[#3898ec]/40 hover:bg-[#fafbff]'
                          }`}
                        >
                          <div className="flex items-start gap-3">
                            <Checkbox
                              className="mt-3"
                              checked={checked}
                              onCheckedChange={(v) => toggleSelect(p.id, v === true)}
                              onClick={(e) => e.stopPropagation()}
                              aria-label={`选择项目 ${p.title}`}
                            />
                            <button
                              type="button"
                              onClick={() => openWithStep(p.id)}
                              className="flex-1 min-w-0 text-left flex items-start gap-3"
                            >
                              <div className="w-10 h-10 rounded-xl bg-[#3898ec]/10 flex items-center justify-center flex-shrink-0">
                                <FolderOpen className="w-5 h-5 text-[#3898ec]" />
                              </div>
                              <div className="min-w-0 flex-1">
                                <div className="flex items-center gap-2 flex-wrap">
                                  <p className="font-semibold text-[#1f1f1f] truncate">{p.title}</p>
                                  <span
                                    className={`text-xs px-2 py-0.5 rounded-full shrink-0 ${
                                      meta.hasPpt
                                        ? 'bg-[#e7f0ff] text-[#0f5abb]'
                                        : meta.hasScript
                                          ? 'bg-emerald-50 text-emerald-800'
                                          : 'bg-gray-100 text-[#1f1f1f]/60'
                                    }`}
                                  >
                                    {meta.stage}
                                  </span>
                                </div>
                                <p className="text-sm text-[#1f1f1f]/50 mt-1">
                                  ID {p.id} · 更新于 {new Date(p.updatedAt).toLocaleString()}
                                </p>
                                <p className="text-xs text-[#3898ec] mt-1">
                                  点击标题打开最近进度，或使用下方按钮进入指定步骤
                                </p>
                              </div>
                            </button>
                            <Button
                              type="button"
                              variant="ghost"
                              size="icon"
                              className="shrink-0 text-[#1f1f1f]/40 hover:text-red-600 hover:bg-red-50"
                              title="删除项目"
                              onClick={() => setDeleteDialog({ kind: 'single', project: p })}
                            >
                              <Trash2 className="w-4 h-4" />
                            </Button>
                          </div>
                          <div className="flex flex-wrap gap-2 mt-3 pl-10">
                            <Button
                              type="button"
                              size="sm"
                              variant="outline"
                              onClick={() => openWithStep(p.id, 'outline')}
                            >
                              大纲
                            </Button>
                            <Button
                              type="button"
                              size="sm"
                              variant="outline"
                              disabled={!meta.hasScript}
                              onClick={() => openWithStep(p.id, 'content')}
                            >
                              内容
                            </Button>
                            <Button
                              type="button"
                              size="sm"
                              variant="outline"
                              disabled={!meta.hasPpt}
                              onClick={() => openWithStep(p.id, 'preview')}
                            >
                              预览
                            </Button>
                          </div>
                        </div>
                      </li>
                    );
                  })}
                </ul>
              </>
            )}
          </div>
        </div>
      </div>

      <AlertDialog
        open={deleteDialog != null}
        onOpenChange={(open) => {
          if (!open && !deleting) setDeleteDialog(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {deleteDialog?.kind === 'batch'
                ? `删除选中的 ${deleteDialog.ids.length} 个项目？`
                : '删除此项目？'}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {deleteDialog?.kind === 'batch' ? (
                <>
                  将永久删除 {batchTitlesPreview} 的大纲、讲稿、PPT 要点、评估记录与检索索引，且无法恢复。
                </>
              ) : (
                <>
                  将永久删除「{deleteDialog?.project.title}」（ID {deleteDialog?.project.id}
                  ）及其大纲、讲稿、PPT 要点、评估记录与检索索引，且无法恢复。
                </>
              )}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleting}>取消</AlertDialogCancel>
            <AlertDialogAction
              disabled={deleting}
              className="bg-red-600 hover:bg-red-700"
              onClick={(e) => {
                e.preventDefault();
                void confirmDelete();
              }}
            >
              {deleting ? '删除中…' : '确认删除'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </section>
  );
};

export default ProjectsSection;
