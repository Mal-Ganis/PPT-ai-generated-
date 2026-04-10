import { FileText, Github, Mail } from 'lucide-react';

const Footer = () => {
  const currentYear = new Date().getFullYear();

  return (
    <footer className="bg-[#1f1f1f] text-white py-12">
      <div className="section-container">
        <div className="section-inner">
          <div className="flex flex-col md:flex-row items-center justify-between gap-6">
            {/* Logo */}
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-[#3898ec] rounded-lg flex items-center justify-center">
                <FileText className="w-6 h-6 text-white" />
              </div>
              <div>
                <h3 className="font-semibold">PPT 大纲智能生成与内容补全系统</h3>
                <p className="text-sm text-white/50">基于 LLM + RAG 技术</p>
              </div>
            </div>

            {/* Links */}
            <div className="flex items-center gap-6">
              <a
                href="#"
                className="text-white/60 hover:text-[#3898ec] transition-colors"
              >
                <Github className="w-5 h-5" />
              </a>
              <a
                href="#"
                className="text-white/60 hover:text-[#3898ec] transition-colors"
              >
                <Mail className="w-5 h-5" />
              </a>
            </div>
          </div>

          <div className="border-t border-white/10 mt-8 pt-8 text-center">
            <p className="text-sm text-white/40">
              © {currentYear} PPT 大纲智能生成与内容补全系统. All rights reserved.
            </p>
            <p className="text-sm text-white/40 mt-2">
              项目团队：文玮浩 · 陈鸿良 · 謝文軒 | 指导老师：黄杰
            </p>
          </div>
        </div>
      </div>
    </footer>
  );
};

export default Footer;
