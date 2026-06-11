import type { UserRole } from './permissions';

const TOKEN_KEY = 'ppt_auth_token';
const USER_KEY = 'ppt_auth_user';

/** 登录态按标签页隔离，避免多标签页登录不同账号时互相覆盖 */
const storage = sessionStorage;

export interface AuthUser {
  id: number;
  username: string;
  displayName: string;
  role: UserRole;
  editorAccessStatus?: 'PENDING' | 'APPROVED' | 'REJECTED' | null;
}

function normalizeRole(role: unknown): UserRole {
  const upper = String(role ?? 'VIEWER').toUpperCase();
  if (upper === 'ADMIN' || upper === 'EDITOR' || upper === 'VIEWER') {
    return upper;
  }
  return 'VIEWER';
}

function clearLegacyLocalStorage(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

/** 从旧版 localStorage 迁移到当前标签页的 sessionStorage（仅执行一次） */
function migrateLegacyLocalStorage(): void {
  const legacyToken = localStorage.getItem(TOKEN_KEY);
  const legacyUserRaw = localStorage.getItem(USER_KEY);
  if (!legacyToken || !legacyUserRaw) {
    clearLegacyLocalStorage();
    return;
  }
  try {
    const parsed = JSON.parse(legacyUserRaw) as AuthUser;
    storage.setItem(TOKEN_KEY, legacyToken);
    storage.setItem(USER_KEY, JSON.stringify({ ...parsed, role: normalizeRole(parsed.role) }));
  } catch {
    /* 损坏的旧数据直接丢弃 */
  } finally {
    clearLegacyLocalStorage();
  }
}

function ensureMigrated(): void {
  if (storage.getItem(TOKEN_KEY)) {
    return;
  }
  if (localStorage.getItem(TOKEN_KEY)) {
    migrateLegacyLocalStorage();
  }
}

export function getAuthToken(): string | null {
  ensureMigrated();
  return storage.getItem(TOKEN_KEY);
}

export function getStoredUser(): AuthUser | null {
  ensureMigrated();
  const raw = storage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as AuthUser;
    return { ...parsed, role: normalizeRole(parsed.role) };
  } catch {
    return null;
  }
}

export function saveAuthSession(token: string, user: AuthUser): void {
  storage.setItem(TOKEN_KEY, token);
  storage.setItem(USER_KEY, JSON.stringify(user));
  clearLegacyLocalStorage();
}

export function clearAuthSession(): void {
  storage.removeItem(TOKEN_KEY);
  storage.removeItem(USER_KEY);
  clearLegacyLocalStorage();
}
