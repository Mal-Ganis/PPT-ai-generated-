import { useEffect, useState } from 'react';
import { ArrowLeft, FolderOpen, Loader2 } from 'lucide-react';
import { FlowExitNav } from '@/components/FlowExitNav';
import { Button } from '@/components/ui/button';
import { listProjects, type ProjectSummary } from '@/lib/backend';

interface ProjectsSectionProps {
  onOpenProject: (projectId: number) => void;
  onBack: () => void;
}

const ProjectsSection = ({ onOpenProject, onBack }: ProjectsSectionProps) => {
  const [projects, setProjects] = useState<ProjectSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      setError('');
      try {
        const data = await listProjects();
        if (!cancelled) setProjects(data);
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : '加载失败');
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    load();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <section className="min-h-screen pt-24 pb-16 bg-[#f3f3f3]">
      <div className="section-container">
        <div className="section-inner max-w-4xl">
          <FlowExitNav className="mb-4" />
          <div className="flex items-center justify-between gap-4 mb-8">
            <div>
              <h1 className="text-3xl font-bold text-[#1f1f1f]">历史项目</h1>
              <p className="text-[#1f1f1f]/60 mt-2">来源后端 ILF-1，点击继续编辑大纲或生成流程</p>
            </div>
            <Button variant="outline" onClick={onBack} className="border-gray-200 text-[#1f1f1f]">
              <ArrowLeft className="w-4 h-4 mr-2" />
              返回
            </Button>
          </div>

          <div className="bg-white rounded-3xl shadow-lg p-6">
            {loading && (
              <div className="flex items-center gap-2 text-[#1f1f1f]/70">
                <Loader2 className="w-5 h-5 animate-spin" />
                加载中…
              </div>
            )}
            {error && (
              <div className="rounded-2xl bg-red-50 border border-red-200 p-4 text-sm text-red-700">{error}</div>
            )}
            {!loading && !error && projects.length === 0 && (
              <p className="text-[#1f1f1f]/60">暂无项目，请先在首页创建。</p>
            )}
            {!loading && projects.length > 0 && (
              <ul className="space-y-3">
                {projects.map((p) => (
                  <li key={p.id}>
                    <button
                      type="button"
                      onClick={() => onOpenProject(p.id)}
                      className="w-full text-left rounded-2xl border border-gray-200 hover:border-[#3898ec]/40 hover:bg-[#fafbff] transition-colors p-4 flex items-start gap-3"
                    >
                      <div className="w-10 h-10 rounded-xl bg-[#3898ec]/10 flex items-center justify-center flex-shrink-0">
                        <FolderOpen className="w-5 h-5 text-[#3898ec]" />
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="font-semibold text-[#1f1f1f] truncate">{p.title}</p>
                        <p className="text-sm text-[#1f1f1f]/50 mt-1">
                          ID {p.id} · 更新于 {new Date(p.updatedAt).toLocaleString()}
                        </p>
                      </div>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      </div>
    </section>
  );
};

export default ProjectsSection;
