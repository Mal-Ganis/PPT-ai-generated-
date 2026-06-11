import { FileText } from 'lucide-react';

interface AuthSplashProps {
  message?: string;
  /** 是否作为全屏遮罩（登录成功离场时使用） */
  overlay?: boolean;
}

export function AuthSplash({ message = '正在验证登录状态…', overlay = false }: AuthSplashProps) {
  return (
    <div
      className={`min-h-screen flex flex-col items-center justify-center bg-[#f3f3f3] ${
        overlay ? 'fixed inset-0 z-50 animate-auth-splash-in' : ''
      }`}
    >
      <div className="absolute inset-0 bg-gradient-to-br from-white via-[#f8fbff] to-[#f3f3f3] pointer-events-none" />
      <div className="relative flex flex-col items-center gap-5 px-6 animate-auth-content-in">
        <div className="relative">
          <div className="absolute inset-0 rounded-2xl bg-[#3898ec]/20 blur-xl scale-110 animate-auth-glow" />
          <div className="relative w-16 h-16 bg-[#3898ec] rounded-2xl flex items-center justify-center shadow-lg shadow-[#3898ec]/25">
            <FileText className="w-8 h-8 text-white" />
          </div>
        </div>
        <div className="text-center space-y-2">
          <p className="text-lg font-semibold text-[#1f1f1f]">PPT 智能生成系统</p>
          <p className="text-sm text-[#1f1f1f]/55">{message}</p>
        </div>
        <div className="flex items-center gap-1.5" aria-hidden>
          {[0, 1, 2].map((i) => (
            <span
              key={i}
              className="w-2 h-2 rounded-full bg-[#3898ec]/70 animate-auth-dot"
              style={{ animationDelay: `${i * 0.15}s` }}
            />
          ))}
        </div>
      </div>
    </div>
  );
}
