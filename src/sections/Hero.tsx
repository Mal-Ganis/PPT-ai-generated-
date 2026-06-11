import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowRight,
  Sparkles,
  FileText,
  FolderOpen,
  Target,
  CheckCircle,
  Layers,
  Settings,
  Search,
  UserCog,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { ViewerAccessPanel } from '@/components/ViewerAccessPanel';
import { fetchEvaluationReports, fetchProject, listProjects } from '@/lib/backend';
import { consumePostLoginEntrance, shouldPlayHeroIntro } from '@/lib/authEntrance';

interface HeroProps {
  onStart: () => void;
  onShowEvaluations: () => void;
  onShowConfig: () => void;
  onShowUsers?: () => void;
  onShowProjects: () => void;
  canWrite?: boolean;
  canManageConfig?: boolean;
}

const Hero = ({
  onStart,
  onShowEvaluations,
  onShowConfig,
  onShowUsers,
  onShowProjects,
  canWrite = true,
  canManageConfig = false,
}: HeroProps) => {
  const heroRef = useRef<HTMLDivElement>(null);
  const postLoginEnter = useRef(consumePostLoginEntrance());
  const playIntro = useRef(shouldPlayHeroIntro());
  const [stats, setStats] = useState({
    projectCount: '—',
    quality: '—',
    sourceCov: '—',
    slideCount: '—',
  });

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const projects = await listProjects();
        if (cancelled || !projects.length) {
          if (!cancelled) {
            setStats((s) => ({ ...s, projectCount: '0' }));
          }
          return;
        }
        const sorted = [...projects].sort(
          (a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime(),
        );
        const detail = await fetchProject(sorted[0].id);
        const evs = await fetchEvaluationReports(sorted[0].id).catch(() => []);
        if (cancelled) return;
        const latest = evs[0];
        const auto = latest?.autoTotalScore;
        const sourceCov = latest?.autoSourceCoverageScore;
        setStats({
          projectCount: String(projects.length),
          quality:
            auto != null && Number.isFinite(auto) ? `${auto.toFixed(1)} 分` : '—',
          sourceCov:
            sourceCov != null && Number.isFinite(sourceCov) ? `${Math.round(sourceCov)} 分` : '—',
          slideCount: detail.slides?.length != null ? `${detail.slides.length} 页` : '—',
        });
      } catch {
        if (!cancelled) {
          setStats({ projectCount: '—', quality: '—', sourceCov: '—', slideCount: '—' });
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const introClass = () => {
    if (postLoginEnter.current) return '';
    if (playIntro.current) return 'animate-slide-up';
    return '';
  };

  const introStyle = (delayMs: number): React.CSSProperties | undefined => {
    if (postLoginEnter.current) return undefined;
    if (playIntro.current) return { animationDelay: `${delayMs}ms` };
    return undefined;
  };

  const fadeIntroClass = () => {
    if (postLoginEnter.current) return '';
    if (playIntro.current) return 'animate-fade-in';
    return '';
  };

  const scaleIntroClass = () => {
    if (postLoginEnter.current) return '';
    if (playIntro.current) return 'animate-scale-in';
    return '';
  };

  useEffect(() => {
    const hero = heroRef.current;
    if (!hero) return;

    const handleScroll = () => {
      const scrollY = window.scrollY;
      const heroHeight = hero.offsetHeight;
      const progress = Math.min(scrollY / heroHeight, 1);
      
      const title = hero.querySelector('.hero-title') as HTMLElement;
      const subtitle = hero.querySelector('.hero-subtitle') as HTMLElement;
      const buttons = hero.querySelector('.hero-buttons') as HTMLElement;
      
      if (title) {
        title.style.transform = `translateY(${-progress * 100}px)`;
        title.style.opacity = `${1 - progress}`;
      }
      if (subtitle) {
        subtitle.style.transform = `translateY(${-progress * 50}px)`;
        subtitle.style.opacity = `${1 - progress}`;
      }
      if (buttons) {
        buttons.style.transform = `translateY(${-progress * 30}px)`;
      }
    };

    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  return (
    <section
      ref={heroRef}
      className="relative min-h-screen flex items-center justify-center overflow-hidden particle-bg"
    >
      {/* Background gradient */}
      <div className="absolute inset-0 bg-gradient-to-br from-white via-[#f8fbff] to-[#f3f3f3]" />
      
      {/* Floating decorative elements */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-[15%] left-[10%] w-20 h-20 rounded-full bg-[#3898ec]/10 animate-float" />
        <div className="absolute top-[25%] right-[15%] w-16 h-16 rounded-full bg-[#3898ec]/15 animate-float-delayed" />
        <div className="absolute bottom-[20%] left-[20%] w-12 h-12 rounded-full bg-[#1f1f1f]/5 animate-float" style={{ animationDelay: '-2s' }} />
        <div className="absolute bottom-[30%] right-[10%] w-24 h-24 rounded-full bg-[#3898ec]/8 animate-float-delayed" style={{ animationDelay: '-4s' }} />
        <div className="absolute top-[40%] left-[5%] w-8 h-8 rounded-lg bg-[#3898ec]/10 rotate-45 animate-float" style={{ animationDelay: '-1s' }} />
        <div className="absolute top-[60%] right-[8%] w-10 h-10 rounded-lg bg-[#1f1f1f]/5 rotate-12 animate-float-delayed" style={{ animationDelay: '-3s' }} />
        <div className="absolute top-1/4 left-1/4 w-96 h-96 bg-[#3898ec]/5 rounded-full blur-3xl animate-pulse" />
        <div className="absolute bottom-1/4 right-1/4 w-80 h-80 bg-[#0082f3]/5 rounded-full blur-3xl animate-pulse" style={{ animationDelay: '-2s' }} />
      </div>

      {/* Content */}
      <div
        className={`relative z-10 section-container py-20 ${
          postLoginEnter.current ? 'animate-page-enter-soft' : ''
        }`}
      >
        <div className="section-inner text-center">
          <ViewerAccessPanel />
          {/* Badge */}
          <div
            className={`inline-flex items-center gap-2 px-4 py-2 bg-[#3898ec]/10 rounded-full mb-8 ${fadeIntroClass()}`}
            style={playIntro.current && !postLoginEnter.current ? { animationDelay: '200ms' } : undefined}
          >
            <Sparkles className="w-4 h-4 text-[#3898ec]" />
            <span className="text-sm font-medium text-[#3898ec]">基于 LLM + RAG 技术</span>
          </div>

          {/* Main Title */}
          <h1 className="hero-title text-4xl sm:text-5xl lg:text-6xl xl:text-7xl font-bold text-[#1f1f1f] mb-6 leading-tight">
            <span className={`inline-block ${introClass()}`} style={introStyle(300)}>
              PPT 大纲
            </span>
            <span className={`inline-block ${introClass()} text-[#3898ec]`} style={introStyle(400)}>
              智能生成
            </span>
            <br />
            <span className={`inline-block ${introClass()}`} style={introStyle(500)}>
              与内容
            </span>
            <span className={`inline-block ${introClass()} text-[#3898ec]`} style={introStyle(600)}>
              深度补全
            </span>
          </h1>

          {/* Subtitle */}
          <p
            className={`hero-subtitle text-lg sm:text-xl lg:text-2xl text-[#1f1f1f]/70 mb-4 max-w-3xl mx-auto ${introClass()}`}
            style={introStyle(800)}
          >
            从零散想法到完整演示，AI 驱动的内容创作新体验
          </p>

          {/* Description */}
          <p
            className={`text-base text-[#1f1f1f]/60 mb-10 max-w-2xl mx-auto ${fadeIntroClass()}`}
            style={playIntro.current && !postLoginEnter.current ? { animationDelay: '1000ms' } : undefined}
          >
            输入主题或上传文档，AI 自动生成结构化大纲，RAG 检索补充精准内容，
            输出完整 PPT 草稿
          </p>

          {/* CTA Buttons */}
          <div
            className={`hero-buttons flex flex-col sm:flex-row items-center justify-center gap-4 ${scaleIntroClass()}`}
            style={playIntro.current && !postLoginEnter.current ? { animationDelay: '1200ms' } : undefined}
          >
            <Button
              size="lg"
              className="btn-magnetic bg-[#3898ec] hover:bg-[#0082f3] text-white px-8 py-6 text-base font-semibold rounded-xl shadow-lg shadow-[#3898ec]/30 disabled:opacity-60"
              onClick={onStart}
              disabled={!canWrite}
              title={canWrite ? undefined : '只读账号无法创建新项目'}
            >
              <FileText className="w-5 h-5 mr-2" />
              {canWrite ? '开始体验' : '只读模式'}
              {canWrite && <ArrowRight className="w-5 h-5 ml-2" />}
            </Button>
            <Button size="lg" variant="outline" className="btn-magnetic inline-flex items-center gap-2 px-8 py-6 rounded-xl border-gray-200 text-[#1f1f1f] hover:bg-gray-100" asChild>
              <Link to="/knowledge">
                <Search className="w-5 h-5" />
                知识检索
              </Link>
            </Button>
            <Button
              size="lg"
              variant="outline"
              className="btn-magnetic inline-flex items-center gap-2 px-8 py-6 rounded-xl border-gray-200 text-[#1f1f1f] hover:bg-gray-100"
              onClick={onShowProjects}
            >
              <FileText className="w-5 h-5" />
              历史项目
            </Button>
            <Button
              size="lg"
              variant="outline"
              className="btn-magnetic inline-flex items-center gap-2 px-8 py-6 rounded-xl border-gray-200 text-[#1f1f1f] hover:bg-gray-100"
              onClick={onShowEvaluations}
            >
              <FileText className="w-5 h-5" />
              评估报告
            </Button>
            {canManageConfig && (
              <>
                <Button
                  size="lg"
                  variant="outline"
                  className="btn-magnetic inline-flex items-center gap-2 px-8 py-6 rounded-xl border-gray-200 text-[#1f1f1f] hover:bg-gray-100"
                  onClick={() => onShowUsers?.()}
                >
                  <UserCog className="w-5 h-5" />
                  用户管理
                </Button>
                <Button
                  size="lg"
                  variant="outline"
                  className="btn-magnetic inline-flex items-center gap-2 px-8 py-6 rounded-xl border-gray-200 text-[#1f1f1f] hover:bg-gray-100"
                  onClick={onShowConfig}
                >
                  <Settings className="w-5 h-5" />
                  系统配置
                </Button>
              </>
            )}
          </div>

          {/* Stats preview */}
          <div
            className={`mt-16 grid grid-cols-2 md:grid-cols-4 gap-6 max-w-3xl mx-auto ${fadeIntroClass()}`}
            style={playIntro.current && !postLoginEnter.current ? { animationDelay: '1400ms' } : undefined}
          >
            {[
              { value: stats.projectCount, label: '历史项目数', icon: FolderOpen },
              { value: stats.quality, label: '最近自动质量', icon: Target },
              { value: stats.sourceCov, label: '最近引用覆盖', icon: CheckCircle },
              { value: stats.slideCount, label: '最近项目页数', icon: Layers },
            ].map((stat, index) => (
              <div key={index} className="text-center">
                <stat.icon className="w-6 h-6 text-[#3898ec] mx-auto mb-2" />
                <div className="text-2xl sm:text-3xl font-bold text-[#3898ec]">{stat.value}</div>
                <div className="text-sm text-[#1f1f1f]/60 mt-1">{stat.label}</div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Bottom gradient fade */}
      <div className="absolute bottom-0 left-0 right-0 h-32 bg-gradient-to-t from-[#f3f3f3] to-transparent" />
    </section>
  );
};

export default Hero;
