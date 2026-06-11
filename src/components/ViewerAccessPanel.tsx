import { useAuth } from '@/contexts/AuthContext';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Loader2, ShieldCheck } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';

export function ViewerAccessPanel() {
  const { user, submitEditorAccessRequest, refreshUser } = useAuth();
  const [message, setMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);

  if (!user || user.role !== 'VIEWER') {
    return null;
  }

  const status = user.editorAccessStatus;

  const handleSubmit = async () => {
    setSubmitting(true);
    try {
      await submitEditorAccessRequest(message.trim() || undefined);
      toast.success('已提交编辑权限申请，请等待管理员审批');
      await refreshUser();
    } catch {
      /* toast from interceptor */
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="max-w-3xl mx-auto mb-8 rounded-2xl border border-[#3898ec]/25 bg-[#3898ec]/8 px-5 py-4">
      <div className="flex items-start gap-3">
        <ShieldCheck className="w-5 h-5 text-[#3898ec] mt-0.5 shrink-0" />
        <div className="flex-1 space-y-3">
          <div>
            <p className="font-medium text-[#1f1f1f]">当前为只读账号</p>
            <p className="text-sm text-[#1f1f1f]/60 mt-1 leading-relaxed">
              您可浏览历史项目与评估报告。如需创建或编辑 PPT，可使用管理员发放的邀请码注册，或在此提交编辑权限申请。
            </p>
          </div>

          {status === 'PENDING' && (
            <p className="text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
              您的编辑权限申请已提交，正在等待管理员审批。
            </p>
          )}

          {status === 'REJECTED' && (
            <p className="text-sm text-rose-800 bg-rose-50 border border-rose-200 rounded-lg px-3 py-2">
              上次申请未通过，可补充说明后重新提交。
            </p>
          )}

          {(!status || status === 'REJECTED') && (
            <>
              <Textarea
                value={message}
                onChange={(e) => setMessage(e.target.value)}
                placeholder="申请说明（可选）：例如所属团队、用途"
                rows={2}
                className="text-sm bg-white"
              />
              <Button
                type="button"
                size="sm"
                className="bg-[#3898ec] hover:bg-[#0082f3]"
                disabled={submitting}
                onClick={() => void handleSubmit()}
              >
                {submitting ? <Loader2 className="w-4 h-4 animate-spin mr-1" /> : null}
                申请编辑权限
              </Button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
