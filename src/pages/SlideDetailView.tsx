import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ChevronLeft, ChevronRight, Loader2, Save } from 'lucide-react';
import { FlowExitNav } from '@/components/FlowExitNav';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { fetchProject, updateProjectSlide, type ProjectDetailResponse, type ProjectDetailSlide } from '@/lib/backend';
import { toast } from 'sonner';

function bulletsFromSlide(slide: ProjectDetailSlide): string[] {
  if (slide.bullets?.length) return [...slide.bullets];
  if (slide.body?.trim()) return [slide.body];
  return [];
}

export default function SlideDetailView() {
  const { projectId: projectIdParam, slideId: slideIdParam } = useParams();
  const navigate = useNavigate();
  const projectId = Number(projectIdParam);
  const slideId = Number(slideIdParam);

  const [detail, setDetail] = useState<ProjectDetailResponse | null>(null);
  const [editTitle, setEditTitle] = useState('');
  const [editBullets, setEditBullets] = useState('');
  const [editSources, setEditSources] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!projectId || !slideId) {
      toast.error('路由参数无效');
      return;
    }
    fetchProject(projectId)
      .then(setDetail)
      .catch((e) => toast.error(e instanceof Error ? e.message : '加载失败'));
  }, [projectId, slideId]);

  const slides: ProjectDetailSlide[] = detail
    ? [...detail.slides].sort((a, b) => (a.position ?? 0) - (b.position ?? 0))
    : [];

  const index = slides.findIndex((s) => s.id === slideId);
  const slide = index >= 0 ? slides[index] : null;
  const prevSlide = index > 0 ? slides[index - 1] : null;
  const nextSlide = index >= 0 && index < slides.length - 1 ? slides[index + 1] : null;

  useEffect(() => {
    if (!slide) return;
    const bullets = bulletsFromSlide(slide);
    setEditTitle(slide.title);
    setEditBullets(bullets.join('\n'));
    setEditSources((slide.sources ?? []).join('\n'));
  }, [slide?.id]);

  const handleSave = async () => {
    if (!detail || !slide) return;
    const bulletLines = editBullets
      .split('\n')
      .map((l) => l.trim())
      .filter(Boolean);
    const sourceLines = editSources
      .split('\n')
      .map((l) => l.trim())
      .filter(Boolean);

    setSaving(true);
    try {
      const updated = await updateProjectSlide(projectId, slideId, {
        title: editTitle.trim() || slide.title,
        bullets: bulletLines,
        sources: sourceLines,
        notes: '',
      });
      setDetail(updated);
      toast.success('本页已保存，其它页未改动');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '保存失败');
    } finally {
      setSaving(false);
    }
  };

  if (!detail) {
    return (
      <div className="min-h-screen pt-24 flex justify-center text-[#1f1f1f]/60">
        加载项目详情…
      </div>
    );
  }

  if (!slide) {
    return (
      <div className="min-h-screen pt-24 px-6">
        <p className="text-[#1f1f1f] mb-4">未找到该幻灯片。</p>
        <FlowExitNav />
      </div>
    );
  }

  return (
    <section className="min-h-screen pt-24 pb-16 bg-[#f3f3f3]">
      <div className="section-container">
        <div className="section-inner max-w-3xl">
          <div className="flex flex-wrap items-center gap-2 gap-y-3 mb-8">
            <FlowExitNav />
            <span className="text-sm text-[#1f1f1f]/60 w-full sm:w-auto sm:ml-auto">
              项目 ID {detail.id} · {detail.title}
            </span>
          </div>

          <div className="bg-white rounded-3xl shadow-lg p-8">
            <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4 mb-6">
              <div className="min-w-0 flex-1">
                <p className="text-sm text-[#3898ec] font-medium mb-2">
                  第 {index + 1} / {slides.length} 页 · 可直接编辑并保存
                </p>
                <label className="sr-only" htmlFor="slide-title-input">
                  标题
                </label>
                <Input
                  id="slide-title-input"
                  value={editTitle}
                  onChange={(e) => setEditTitle(e.target.value)}
                  className="text-xl font-bold border-gray-200 mt-1"
                />
                {slide.chapter && (
                  <p className="text-sm text-[#1f1f1f]/50 mt-2">章节：{slide.chapter}</p>
                )}
              </div>
              <Button
                type="button"
                className="shrink-0 bg-[#3898ec] hover:bg-[#0082f3] text-white"
                disabled={saving}
                onClick={() => void handleSave()}
              >
                {saving ? (
                  <>
                    <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                    保存中…
                  </>
                ) : (
                  <>
                    <Save className="w-4 h-4 mr-2" />
                    保存到项目
                  </>
                )}
              </Button>
            </div>

            <div className="space-y-6">
              <div>
                <label className="text-sm font-semibold text-[#1f1f1f]/70 mb-2 block">要点（每行一条）</label>
                <Textarea
                  value={editBullets}
                  onChange={(e) => setEditBullets(e.target.value)}
                  className="min-h-[140px] border-gray-200 text-[#1f1f1f]"
                  placeholder="每行一条要点"
                />
              </div>

              <div>
                <label className="text-sm font-semibold text-[#1f1f1f]/70 mb-2 block">引用来源（可选）</label>
                <p className="text-xs text-[#1f1f1f]/55 mb-2 leading-relaxed">
                  用于记录本页要点所依据的出处：可与外部检索（Tavily / 维基）或向量索引命中片段对应，便于在预览、导出 Markdown
                  或事实抽检时核对依据；每行一条链接或简短文献说明，不需要可留空。
                </p>
                <Textarea
                  value={editSources}
                  onChange={(e) => setEditSources(e.target.value)}
                  className="min-h-[72px] border-gray-200 text-sm"
                  placeholder="每行一条，例如：https://..."
                />
              </div>
            </div>

            <p className="text-xs text-[#1f1f1f]/50 mt-6">
              保存仅更新当前页并写入数据库，其它幻灯片不受影响。若曾在「内容」步骤生成过正文，修改要点后如需重新生成该页可返回流程中使用「重新生成」。
            </p>

            <div className="flex justify-between mt-10 pt-8 border-t border-gray-200">
              <Button
                variant="outline"
                disabled={!prevSlide}
                onClick={() =>
                  prevSlide && navigate(`/project/${projectId}/slide/${prevSlide.id}`)
                }
                className="border-gray-200"
              >
                <ChevronLeft className="w-4 h-4 mr-1" />
                上一页
              </Button>
              <Button
                variant="outline"
                disabled={!nextSlide}
                onClick={() =>
                  nextSlide && navigate(`/project/${projectId}/slide/${nextSlide.id}`)
                }
                className="border-gray-200"
              >
                下一页
                <ChevronRight className="w-4 h-4 ml-1" />
              </Button>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
