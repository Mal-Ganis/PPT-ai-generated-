import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { FileText, Loader2, LogIn, Shield, UserPlus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useAuth } from '@/contexts/AuthContext';
import { ROLE_LABELS } from '@/lib/permissions';
import { toast } from 'sonner';

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login, register, isAuthenticated, loading } = useAuth();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const from =
    (location.state as { from?: string } | null)?.from && (location.state as { from?: string }).from !== '/login'
      ? (location.state as { from: string }).from
      : '/';

  if (!loading && isAuthenticated) {
    return <Navigate to={from} replace />;
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim() || !password) {
      toast.error('请输入用户名和密码');
      return;
    }
    setSubmitting(true);
    try {
      if (mode === 'login') {
        await login(username.trim(), password);
        toast.success('登录成功');
      } else {
        await register(username.trim(), password, displayName.trim() || undefined);
        toast.success('注册成功，已自动登录');
      }
      navigate(from, { replace: true });
    } catch {
      /* axios 拦截器已 toast */
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#f3f3f3] flex flex-col">
      <div className="flex-1 flex items-center justify-center p-4">
        <div className="w-full max-w-4xl grid grid-cols-1 lg:grid-cols-2 gap-8 items-start">
          <div className="pt-4 lg:pt-8">
            <div className="flex items-center gap-3 mb-6">
              <div className="w-10 h-10 bg-[#3898ec] rounded-xl flex items-center justify-center">
                <FileText className="w-6 h-6 text-white" />
              </div>
              <div>
                <h1 className="text-2xl font-bold text-[#1f1f1f]">PPT 智能生成系统</h1>
                <p className="text-sm text-[#1f1f1f]/55 mt-1">请使用已开通的账号登录</p>
              </div>
            </div>

            <div className="bg-white rounded-2xl shadow-lg p-6 space-y-4 hidden lg:block">
              <h2 className="font-semibold text-[#1f1f1f] flex items-center gap-2">
                <Shield className="w-4 h-4 text-[#3898ec]" />
                账号与权限
              </h2>
              <p className="text-sm text-[#1f1f1f]/65 leading-relaxed">
                系统按角色分配功能范围。若需创建项目、编辑内容或管理系统配置，请联系管理员为您开通相应权限。
              </p>
              <ul className="space-y-2.5 text-sm text-[#1f1f1f]/70 leading-relaxed">
                <li>
                  <strong className="text-[#1f1f1f]">{ROLE_LABELS.ADMIN}</strong>
                  ：系统配置、项目管理与全部编辑能力
                </li>
                <li>
                  <strong className="text-[#1f1f1f]">{ROLE_LABELS.EDITOR}</strong>
                  ：创建与编辑项目、生成内容与知识检索
                </li>
                <li>
                  <strong className="text-[#1f1f1f]">{ROLE_LABELS.VIEWER}</strong>
                  ：查看项目、预览与评估报告
                </li>
              </ul>
            </div>
          </div>

          <div className="bg-white rounded-2xl shadow-xl p-6 sm:p-8 w-full max-w-md mx-auto lg:max-w-none">
            <div className="flex gap-2 mb-6 p-1 bg-gray-100 rounded-xl">
              <button
                type="button"
                className={`flex-1 py-2 text-sm font-medium rounded-lg transition-colors ${
                  mode === 'login' ? 'bg-white shadow text-[#3898ec]' : 'text-[#1f1f1f]/60'
                }`}
                onClick={() => setMode('login')}
              >
                登录
              </button>
              <button
                type="button"
                className={`flex-1 py-2 text-sm font-medium rounded-lg transition-colors ${
                  mode === 'register' ? 'bg-white shadow text-[#3898ec]' : 'text-[#1f1f1f]/60'
                }`}
                onClick={() => setMode('register')}
              >
                注册
              </button>
            </div>

            <form onSubmit={(e) => void handleSubmit(e)} className="space-y-4">
              {mode === 'register' && (
                <div className="space-y-2">
                  <Label htmlFor="displayName">姓名（可选）</Label>
                  <Input
                    id="displayName"
                    value={displayName}
                    onChange={(e) => setDisplayName(e.target.value)}
                    placeholder="用于界面展示"
                    disabled={submitting}
                  />
                </div>
              )}
              <div className="space-y-2">
                <Label htmlFor="username">用户名</Label>
                <Input
                  id="username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="请输入用户名"
                  autoComplete="username"
                  disabled={submitting}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="password">密码</Label>
                <Input
                  id="password"
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder={mode === 'register' ? '至少 6 位字符' : '请输入密码'}
                  autoComplete={mode === 'register' ? 'new-password' : 'current-password'}
                  disabled={submitting}
                />
              </div>

              <Button
                type="submit"
                className="w-full bg-[#3898ec] hover:bg-[#0082f3] text-white"
                disabled={submitting}
              >
                {submitting ? (
                  <>
                    <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                    请稍候…
                  </>
                ) : mode === 'login' ? (
                  <>
                    <LogIn className="w-4 h-4 mr-2" />
                    登录
                  </>
                ) : (
                  <>
                    <UserPlus className="w-4 h-4 mr-2" />
                    注册
                  </>
                )}
              </Button>
            </form>

            {mode === 'register' && (
              <p className="mt-4 text-xs text-[#1f1f1f]/50 leading-relaxed">
                新注册账号默认为只读权限。如需编辑或管理功能，请联系系统管理员。
              </p>
            )}

            {mode === 'login' && (
              <p className="mt-4 text-xs text-[#1f1f1f]/45 leading-relaxed">
                忘记密码或无法登录，请联系系统管理员重置账号。
              </p>
            )}
          </div>
        </div>
      </div>

      <footer className="py-4 text-center text-xs text-[#1f1f1f]/40">
        <Link to="/" className="hover:text-[#3898ec]">
          返回首页
        </Link>
      </footer>
    </div>
  );
}
