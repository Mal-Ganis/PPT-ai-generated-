import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { FileText, Loader2, LogIn, Shield, UserPlus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { AuthSplash } from '@/components/AuthSplash';
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
  const [inviteCode, setInviteCode] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [leaving, setLeaving] = useState(false);

  const from =
    (location.state as { from?: string } | null)?.from && (location.state as { from?: string }).from !== '/login'
      ? (location.state as { from: string }).from
      : '/';

  if (!loading && isAuthenticated && !leaving) {
    return <Navigate to={from} replace />;
  }

  if (loading && !isAuthenticated) {
    return <AuthSplash message="正在恢复登录状态…" />;
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
        const loggedIn = await login(username.trim(), password);
        toast.success(`登录成功（当前权限：${ROLE_LABELS[loggedIn.role]}）`);
      } else {
        await register(
          username.trim(),
          password,
          displayName.trim() || undefined,
          inviteCode.trim() || undefined,
        );
        toast.success(
          inviteCode.trim()
            ? '注册成功，已按邀请码开通相应权限'
            : '注册成功，当前为只读账号',
        );
      }
      setLeaving(true);
      window.setTimeout(() => {
        navigate(from, { replace: true });
      }, 320);
    } catch {
      /* axios 拦截器已 toast */
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#f3f3f3] flex flex-col relative">
      <div
        className={`flex-1 flex items-center justify-center p-4 transition-all duration-300 ease-out ${
          leaving ? 'opacity-0 scale-[0.98] translate-y-2 pointer-events-none' : 'opacity-100 scale-100 translate-y-0'
        }`}
      >
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
                公开注册默认为<strong className="text-[#1f1f1f]">只读访客</strong>。获得编辑能力有两种方式：
              </p>
              <ul className="space-y-2.5 text-sm text-[#1f1f1f]/70 leading-relaxed list-disc pl-5">
                <li>注册时填写管理员发放的<strong>邀请码</strong>，可直接成为编辑者</li>
                <li>注册后在首页<strong>申请编辑权限</strong>，由管理员审批</li>
              </ul>
              <ul className="space-y-2 text-sm text-[#1f1f1f]/60 pt-2 border-t border-gray-100">
                <li>
                  <strong className="text-[#1f1f1f]">{ROLE_LABELS.EDITOR}</strong>：创建项目、生成内容与知识检索
                </li>
                <li>
                  <strong className="text-[#1f1f1f]">{ROLE_LABELS.VIEWER}</strong>：仅可浏览管理员标记的模板项目、预览与评估
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
                <>
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
                  <div className="space-y-2">
                    <Label htmlFor="inviteCode">邀请码（可选）</Label>
                    <Input
                      id="inviteCode"
                      value={inviteCode}
                      onChange={(e) => setInviteCode(e.target.value.toUpperCase())}
                      placeholder="有邀请码可直接开通编辑权限"
                      disabled={submitting}
                      autoComplete="off"
                    />
                  </div>
                </>
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
                未填写邀请码时将注册为只读账号；登录后可在首页提交编辑权限申请，或向管理员索取邀请码。
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

      <footer className={`py-4 text-center text-xs text-[#1f1f1f]/40 transition-opacity duration-300 ${leaving ? 'opacity-0' : ''}`}>
        <Link to="/" className="hover:text-[#3898ec]">
          返回首页
        </Link>
      </footer>

      {leaving && <AuthSplash message="正在进入系统…" overlay />}
    </div>
  );
}
