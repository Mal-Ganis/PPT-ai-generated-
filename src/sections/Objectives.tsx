import { useEffect, useRef, useState } from 'react';
import { FileText, Search, BarChart3, Puzzle } from 'lucide-react';

interface ObjectiveCard {
  icon: React.ElementType;
  title: string;
  description: string;
}

const objectives: ObjectiveCard[] = [
  {
    icon: FileText,
    title: '大纲结构化生成',
    description: '基于 LLM 的智能大纲生成，支持主题输入与文档上传，自动构建逻辑清晰的 PPT 骨架',
  },
  {
    icon: Search,
    title: '知识检索与内容补全',
    description: '基于 RAG 技术的外部知识检索，智能筛选与多层次浓缩，为每页 PPT 补充精准内容',
  },
  {
    icon: BarChart3,
    title: '端到端评估体系',
    description: '构建多维度评估指标体系，结合人工评测与自动化方法进行系统性质量验证',
  },
  {
    icon: Puzzle,
    title: '原型系统集成',
    description: '实现完整的端到端流程，从输入到可编辑 PPT 草稿的一站式解决方案',
  },
];

const Objectives = () => {
  const sectionRef = useRef<HTMLDivElement>(null);
  const [isVisible, setIsVisible] = useState(false);

  useEffect(() => {
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setIsVisible(true);
          observer.disconnect();
        }
      },
      { threshold: 0.2 }
    );

    if (sectionRef.current) {
      observer.observe(sectionRef.current);
    }

    return () => observer.disconnect();
  }, []);

  return (
    <section
      id="objectives"
      ref={sectionRef}
      className="relative py-24 lg:py-32 bg-[#f3f3f3]"
    >
      <div className="section-container">
        <div className="section-inner">
          {/* Section Header */}
          <div className="text-center mb-16">
            <h2 
              className={`text-3xl sm:text-4xl lg:text-5xl font-bold text-[#1f1f1f] mb-4 transition-all duration-700 ${
                isVisible ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-8'
              }`}
            >
              目标
            </h2>
            <p 
              className={`text-xl sm:text-2xl text-[#3898ec] font-semibold transition-all duration-700 delay-100 ${
                isVisible ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-8'
              }`}
            >
              智能生成 · 深度补全
            </p>
          </div>

          {/* Cards Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6 lg:gap-8">
            {objectives.map((objective, index) => {
              const Icon = objective.icon;
              return (
                <div
                  key={index}
                  className={`group relative bg-white rounded-2xl p-8 shadow-lg hover:shadow-2xl transition-all duration-500 card-3d ${
                    isVisible ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-12'
                  }`}
                  style={{
                    transitionDelay: isVisible ? `${400 + index * 120}ms` : '0ms',
                  }}
                >
                  {/* Icon */}
                  <div className="w-14 h-14 bg-[#3898ec]/10 rounded-xl flex items-center justify-center mb-6 group-hover:bg-[#3898ec] group-hover:scale-110 transition-all duration-300">
                    <Icon className="w-7 h-7 text-[#3898ec] group-hover:text-white transition-colors duration-300" />
                  </div>

                  {/* Content */}
                  <h3 className="text-xl font-bold text-[#1f1f1f] mb-3 group-hover:text-[#3898ec] transition-colors duration-300">
                    {objective.title}
                  </h3>
                  <p className="text-[#1f1f1f]/60 leading-relaxed">
                    {objective.description}
                  </p>

                  {/* Hover gradient overlay */}
                  <div className="absolute inset-0 rounded-2xl bg-gradient-to-br from-[#3898ec]/5 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300 pointer-events-none" />
                </div>
              );
            })}
          </div>

          {/* Additional Info */}
          <div 
            className={`mt-16 bg-white rounded-2xl p-8 shadow-lg transition-all duration-700 ${
              isVisible ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-12'
            }`}
            style={{ transitionDelay: isVisible ? '900ms' : '0ms' }}
          >
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
              <div>
                <h3 className="text-xl font-bold text-[#1f1f1f] mb-4">项目背景</h3>
                <p className="text-[#1f1f1f]/60 leading-relaxed mb-4">
                  在商务、教育、科研等领域，高质量 PPT 的制作仍高度依赖人工经验，从零散想法、粗糙提纲或海量文档中提炼出逻辑严谨、叙事流畅、内容充实的演示文稿往往耗时数小时甚至数天。
                </p>
                <p className="text-[#1f1f1f]/60 leading-relaxed">
                  现有 AI 工具多停留在模板填充或浅层文本生成层面，难以产出具有强叙事张力的大纲结构，也缺乏对外部权威知识的深度检索与精准整合，导致内容空泛、逻辑跳跃或事实偏差。
                </p>
              </div>
              <div>
                <h3 className="text-xl font-bold text-[#1f1f1f] mb-4">项目目标</h3>
                <p className="text-[#1f1f1f]/60 leading-relaxed mb-4">
                  研发一套基于 LLM 与 RAG 的 PPT 大纲智能生成与内容深度补全原型系统，能够稳定地将用户输入的简短主题或长文档转译为层次清晰、逻辑连贯的 PPT 叙事骨架。
                </p>
                <p className="text-[#1f1f1f]/60 leading-relaxed">
                  通过外部知识的智能检索、筛选与多层次浓缩，为每页自动补充精准、精炼、具有说服力的文字、数据与论据，最终通过多真实办公场景的系统性测试与评估，验证方案的内容质量、可使用性及工程化潜力。
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};

export default Objectives;
