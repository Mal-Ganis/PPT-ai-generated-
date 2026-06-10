export type UserRole = 'ADMIN' | 'EDITOR' | 'VIEWER';

export const ROLE_LABELS: Record<UserRole, string> = {
  ADMIN: '管理员',
  EDITOR: '编辑者',
  VIEWER: '只读访客',
};

export function canWriteProjects(role: UserRole | undefined): boolean {
  return role === 'ADMIN' || role === 'EDITOR';
}

export function canManageConfig(role: UserRole | undefined): boolean {
  return role === 'ADMIN';
}

export function canDeleteProjects(role: UserRole | undefined): boolean {
  return role === 'ADMIN' || role === 'EDITOR';
}

export function canManageKnowledgeIndex(role: UserRole | undefined): boolean {
  return role === 'ADMIN' || role === 'EDITOR';
}
