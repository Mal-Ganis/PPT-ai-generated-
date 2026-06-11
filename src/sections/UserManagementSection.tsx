import { useCallback, useEffect, useState } from 'react';
import { ArrowLeft, Copy, Loader2, UserCog } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import {
  approveEditorRequest,
  createInviteCode,
  deactivateInviteCode,
  fetchAdminUsers,
  fetchInviteCodes,
  fetchPendingEditorRequests,
  rejectEditorRequest,
  updateAdminUserRole,
  type AdminUserRow,
  type EditorAccessRequestRow,
  type InviteCodeRow,
} from '@/lib/backend';
import { ROLE_LABELS, type UserRole } from '@/lib/permissions';
import { toast } from 'sonner';

interface UserManagementSectionProps {
  onBack: () => void;
}

type Tab = 'users' | 'requests' | 'invites';

export default function UserManagementSection({ onBack }: UserManagementSectionProps) {
  const [tab, setTab] = useState<Tab>('requests');
  const [loading, setLoading] = useState(true);
  const [users, setUsers] = useState<AdminUserRow[]>([]);
  const [requests, setRequests] = useState<EditorAccessRequestRow[]>([]);
  const [invites, setInvites] = useState<InviteCodeRow[]>([]);
  const [inviteNote, setInviteNote] = useState('');
  const [creatingInvite, setCreatingInvite] = useState(false);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const [u, r, i] = await Promise.all([
        fetchAdminUsers(),
        fetchPendingEditorRequests(),
        fetchInviteCodes(),
      ]);
      setUsers(u);
      setRequests(r);
      setInvites(i);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  const handleRoleChange = async (userId: number, role: UserRole) => {
    try {
      await updateAdminUserRole(userId, role);
      toast.success('角色已更新');
      await reload();
    } catch {
      /* toast */
    }
  };

  const handleApprove = async (id: number) => {
    try {
      await approveEditorRequest(id);
      toast.success('已通过，该用户现为编辑者');
      await reload();
    } catch {
      /* toast */
    }
  };

  const handleReject = async (id: number) => {
    try {
      await rejectEditorRequest(id);
      toast.success('已拒绝申请');
      await reload();
    } catch {
      /* toast */
    }
  };

  const handleCreateInvite = async () => {
    setCreatingInvite(true);
    try {
      const row = await createInviteCode({
        role: 'EDITOR',
        maxUses: 1,
        note: inviteNote.trim() || undefined,
      });
      toast.success(`邀请码已生成：${row.code}`);
      setInviteNote('');
      await reload();
    } catch {
      /* toast */
    } finally {
      setCreatingInvite(false);
    }
  };

  const copyCode = async (code: string) => {
    try {
      await navigator.clipboard.writeText(code);
      toast.success('邀请码已复制');
    } catch {
      toast.error('复制失败，请手动复制');
    }
  };

  return (
    <section className="min-h-screen pt-24 pb-16">
      <div className="section-container">
        <div className="section-inner max-w-5xl">
          <div className="flex flex-wrap items-center gap-4 mb-8">
            <Button type="button" variant="outline" size="sm" onClick={onBack}>
              <ArrowLeft className="w-4 h-4 mr-1" />
              返回
            </Button>
            <h1 className="text-2xl font-bold text-[#1f1f1f] flex items-center gap-2">
              <UserCog className="w-6 h-6 text-[#3898ec]" />
              用户与权限
            </h1>
          </div>

          <div className="flex flex-wrap gap-2 mb-6">
            {(
              [
                ['requests', `待审批 (${requests.length})`],
                ['users', '全部用户'],
                ['invites', '邀请码'],
              ] as const
            ).map(([key, label]) => (
              <Button
                key={key}
                type="button"
                size="sm"
                variant={tab === key ? 'default' : 'outline'}
                className={tab === key ? 'bg-[#3898ec] hover:bg-[#0082f3]' : ''}
                onClick={() => setTab(key)}
              >
                {label}
              </Button>
            ))}
          </div>

          {loading ? (
            <div className="flex justify-center py-16 text-[#1f1f1f]/50">
              <Loader2 className="w-6 h-6 animate-spin" />
            </div>
          ) : (
            <>
              {tab === 'requests' && (
                <div className="space-y-4">
                  {requests.length === 0 ? (
                    <p className="text-sm text-[#1f1f1f]/50 bg-white rounded-2xl p-6 shadow-sm">
                      暂无待审批的编辑权限申请。
                    </p>
                  ) : (
                    requests.map((r) => (
                      <div key={r.id} className="bg-white rounded-2xl p-5 shadow-sm border border-gray-100">
                        <div className="flex flex-wrap justify-between gap-3 mb-2">
                          <div>
                            <p className="font-medium text-[#1f1f1f]">
                              {r.displayName}{' '}
                              <span className="text-[#1f1f1f]/45 font-normal">@{r.username}</span>
                            </p>
                            {r.message && (
                              <p className="text-sm text-[#1f1f1f]/65 mt-2 whitespace-pre-wrap">{r.message}</p>
                            )}
                          </div>
                          <div className="flex gap-2">
                            <Button
                              type="button"
                              size="sm"
                              className="bg-[#3898ec] hover:bg-[#0082f3]"
                              onClick={() => void handleApprove(r.id)}
                            >
                              批准为编辑者
                            </Button>
                            <Button type="button" size="sm" variant="outline" onClick={() => void handleReject(r.id)}>
                              拒绝
                            </Button>
                          </div>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              )}

              {tab === 'users' && (
                <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead className="bg-gray-50 text-[#1f1f1f]/55">
                        <tr>
                          <th className="text-left px-4 py-3 font-medium">用户</th>
                          <th className="text-left px-4 py-3 font-medium">角色</th>
                          <th className="text-left px-4 py-3 font-medium">申请状态</th>
                        </tr>
                      </thead>
                      <tbody>
                        {users.map((u) => (
                          <tr key={u.id} className="border-t border-gray-100">
                            <td className="px-4 py-3">
                              <div className="font-medium text-[#1f1f1f]">{u.displayName}</div>
                              <div className="text-[#1f1f1f]/45">@{u.username}</div>
                            </td>
                            <td className="px-4 py-3">
                              <select
                                className="rounded-lg border border-gray-200 px-2 py-1.5 text-sm"
                                value={u.role}
                                onChange={(e) =>
                                  void handleRoleChange(u.id, e.target.value as UserRole)
                                }
                              >
                                {(['ADMIN', 'EDITOR', 'VIEWER'] as const).map((role) => (
                                  <option key={role} value={role}>
                                    {ROLE_LABELS[role]}
                                  </option>
                                ))}
                              </select>
                            </td>
                            <td className="px-4 py-3 text-[#1f1f1f]/60">
                              {u.editorAccessStatus === 'PENDING'
                                ? '待审批'
                                : u.editorAccessStatus === 'REJECTED'
                                  ? '已拒绝'
                                  : '—'}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}

              {tab === 'invites' && (
                <div className="space-y-6">
                  <div className="bg-white rounded-2xl p-5 shadow-sm border border-gray-100 space-y-3">
                    <p className="text-sm text-[#1f1f1f]/65">
                      生成邀请码后发给用户，注册时填写即可直接获得编辑者权限（无需审批）。
                    </p>
                    <Textarea
                      value={inviteNote}
                      onChange={(e) => setInviteNote(e.target.value)}
                      placeholder="备注（可选），如：课程小组 A"
                      rows={2}
                      className="text-sm"
                    />
                    <Button
                      type="button"
                      size="sm"
                      className="bg-[#3898ec] hover:bg-[#0082f3]"
                      disabled={creatingInvite}
                      onClick={() => void handleCreateInvite()}
                    >
                      {creatingInvite ? <Loader2 className="w-4 h-4 animate-spin mr-1" /> : null}
                      生成编辑者邀请码
                    </Button>
                  </div>

                  <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
                    <div className="overflow-x-auto">
                      <table className="w-full text-sm">
                        <thead className="bg-gray-50 text-[#1f1f1f]/55">
                          <tr>
                            <th className="text-left px-4 py-3 font-medium">邀请码</th>
                            <th className="text-left px-4 py-3 font-medium">角色</th>
                            <th className="text-left px-4 py-3 font-medium">使用</th>
                            <th className="text-left px-4 py-3 font-medium">状态</th>
                            <th className="px-4 py-3" />
                          </tr>
                        </thead>
                        <tbody>
                          {invites.map((inv) => (
                            <tr key={inv.id} className="border-t border-gray-100">
                              <td className="px-4 py-3 font-mono">{inv.code}</td>
                              <td className="px-4 py-3">{ROLE_LABELS[inv.role as UserRole] ?? inv.role}</td>
                              <td className="px-4 py-3">
                                {inv.usedCount}/{inv.maxUses}
                              </td>
                              <td className="px-4 py-3">{inv.active ? '有效' : '已停用'}</td>
                              <td className="px-4 py-3 text-right space-x-2">
                                <Button type="button" size="sm" variant="outline" onClick={() => void copyCode(inv.code)}>
                                  <Copy className="w-3.5 h-3.5 mr-1" />
                                  复制
                                </Button>
                                {inv.active && (
                                  <Button
                                    type="button"
                                    size="sm"
                                    variant="outline"
                                    onClick={() => void deactivateInviteCode(inv.id).then(() => reload())}
                                  >
                                    停用
                                  </Button>
                                )}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </section>
  );
}
