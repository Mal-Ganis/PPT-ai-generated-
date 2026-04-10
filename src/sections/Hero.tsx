import { useEffect, useRef } from 'react';
import { ArrowRight, Sparkles, FileText, Zap, Clock, Target, CheckCircle, Layers } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface HeroProps {
  onStart: () => void;
}

const Hero = ({ onStart }: HeroProps) => {
  const heroRef = useRef<HTMLDivElement>(null);

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
      <div className="relative z-10 section-container py-20">
        <div className="section-inner text-center">
          {/* Badge */}
          <div 
            className="inline-flex items-center gap-2 px-4 py-2 bg-[#3898ec]/10 rounded-full mb-8 animate-fade-in"
            style={{ animationDelay: '200ms' }}
          >
            <Sparkles className="w-4 h-4 text-[#3898ec]" />
            <span className="text-sm font-medium text-[#3898ec]">基于 LLM + RAG 技术</span>
          </div>

          {/* Main Title */}
          <h1 className="hero-title text-4xl sm:text-5xl lg:text-6xl xl:text-7xl font-bold text-[#1f1f1f] mb-6 leading-tight">
            <span className="inline-block animate-slide-up" style={{ animationDelay: '300ms' }}>
              PPT 大纲
            </span>
            <span className="inline-block animate-slide-up text-[#3898ec]" style={{ animationDelay: '400ms' }}>
              智能生成
            </span>
            <br />
            <span className="inline-block animate-slide-up" style={{ animationDelay: '500ms' }}>
              与内容
            </span>
            <span className="inline-block animate-slide-up text-[#3898ec]" style={{ animationDelay: '600ms' }}>
              深度补全
            </span>
          </h1>

          {/* Subtitle */}
          <p 
            className="hero-subtitle text-lg sm:text-xl lg:text-2xl text-[#1f1f1f]/70 mb-4 max-w-3xl mx-auto animate-slide-up"
            style={{ animationDelay: '800ms' }}
          >
            从零散想法到完整演示，AI 驱动的内容创作新体验
          </p>

          {/* Description */}
          <p 
            className="text-base text-[#1f1f1f]/60 mb-10 max-w-2xl mx-auto animate-fade-in"
            style={{ animationDelay: '1000ms' }}
          >
            输入主题或上传文档，AI 自动生成结构化大纲，RAG 检索补充精准内容，
            输出完整 PPT 草稿
          </p>

          {/* CTA Buttons */}
          <div className="hero-buttons flex flex-col sm:flex-row items-center justify-center gap-4 animate-scale-in" style={{ animationDelay: '1200ms' }}>
            <Button
              size="lg"
              className="btn-magnetic bg-[#3898ec] hover:bg-[#0082f3] text-white px-8 py-6 text-base font-semibold rounded-xl shadow-lg shadow-[#3898ec]/30"
              onClick={onStart}
            >
              <FileText className="w-5 h-5 mr-2" />
              开始体验
              <ArrowRight className="w-5 h-5 ml-2" />
            </Button>
            <a
              href="#features"
              className="btn-magnetic inline-flex items-center gap-2 px-8 py-4 border-2 border-[#1f1f1f]/20 text-[#1f1f1f] hover:bg-[#1f1f1f] hover:text-white rounded-xl transition-all duration-300 font-semibold"
            >
              <Zap className="w-5 h-5" />
              了解更多
            </a>
          </div>

          {/* Stats preview */}
          <div 
            className="mt-16 grid grid-cols-2 md:grid-cols-4 gap-6 max-w-3xl mx-auto animate-fade-in"
            style={{ animationDelay: '1400ms' }}
          >
            {[
              { value: '180秒', label: '生成时间', icon: Clock },
              { value: '4.2/5', label: '质量评分', icon: Target },
              { value: '92%', label: '事实准确率', icon: CheckCircle },
              { value: '3.5条', label: '信息密度', icon: Layers },
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
