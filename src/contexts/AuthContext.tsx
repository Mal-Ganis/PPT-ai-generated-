import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  fetchCurrentUser,
  login as apiLogin,
  register as apiRegister,
  submitEditorAccessRequest as apiSubmitEditorAccessRequest,
} from '@/lib/backend';
import {
  clearAuthSession,
  getAuthToken,
  getStoredUser,
  saveAuthSession,
  type AuthUser,
} from '@/lib/authStorage';
import { markPostLoginEntrance } from '@/lib/authEntrance';
import { canManageConfig, canWriteProjects } from '@/lib/permissions';
import type { UserRole } from '@/lib/permissions';

interface AuthContextValue {
  user: AuthUser | null;
  loading: boolean;
  isAuthenticated: boolean;
  canWrite: boolean;
  canConfig: boolean;
  login: (username: string, password: string) => Promise<AuthUser>;
  register: (username: string, password: string, displayName?: string, inviteCode?: string) => Promise<void>;
  logout: () => void;
  refreshUser: () => Promise<void>;
  submitEditorAccessRequest: (message?: string) => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function mapProfileToAuthUser(profile: {
  id: number;
  username: string;
  displayName: string;
  role: string;
  editorAccessStatus?: 'PENDING' | 'APPROVED' | 'REJECTED' | null;
}): AuthUser {
  const normalizedRole = (profile.role?.toUpperCase() ?? 'VIEWER') as UserRole;
  return {
    id: profile.id,
    username: profile.username,
    displayName: profile.displayName,
    role: normalizedRole,
    editorAccessStatus: profile.editorAccessStatus ?? null,
  };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(() => getStoredUser());
  /** 仅有 token 但尚无缓存用户信息时才阻塞界面，避免登录后闪一下加载屏 */
  const [loading, setLoading] = useState(() => Boolean(getAuthToken()) && !getStoredUser());

  const logout = useCallback(() => {
    clearAuthSession();
    setUser(null);
  }, []);

  const refreshUser = useCallback(async () => {
    const token = getAuthToken();
    if (!token) {
      setUser(null);
      setLoading(false);
      return;
    }
    try {
      const profile = await fetchCurrentUser();
      const next = mapProfileToAuthUser(profile);
      saveAuthSession(token, next);
      setUser(next);
    } catch {
      logout();
    } finally {
      setLoading(false);
    }
  }, [logout]);

  useEffect(() => {
    void refreshUser();
  }, [refreshUser]);

  /** 标签页重新获得焦点时，与 sessionStorage 对齐（防止极少数情况下内存态与存储不一致） */
  useEffect(() => {
    const syncFromStorage = () => {
      const stored = getStoredUser();
      setUser((prev) => {
        if (!stored && !prev) return prev;
        if (!stored) return null;
        if (!prev) return stored;
        if (prev.id === stored.id && prev.role === stored.role && prev.username === stored.username) {
          return prev;
        }
        return stored;
      });
    };
    window.addEventListener('focus', syncFromStorage);
    return () => window.removeEventListener('focus', syncFromStorage);
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const res = await apiLogin(username, password);
    const next = mapProfileToAuthUser(res.user);
    saveAuthSession(res.token, next);
    markPostLoginEntrance();
    setUser(next);
    setLoading(false);
    return next;
  }, []);

  const register = useCallback(
    async (username: string, password: string, displayName?: string, inviteCode?: string) => {
      const res = await apiRegister(username, password, displayName, inviteCode);
      const next = mapProfileToAuthUser(res.user);
      saveAuthSession(res.token, next);
      markPostLoginEntrance();
      setUser(next);
      setLoading(false);
    },
    [],
  );

  const submitEditorAccessRequest = useCallback(
    async (message?: string) => {
      const status = await apiSubmitEditorAccessRequest(message);
      const token = getAuthToken();
      if (token && user) {
        const next: AuthUser = {
          ...user,
          editorAccessStatus: status.status ?? 'PENDING',
        };
        saveAuthSession(token, next);
        setUser(next);
      }
      await refreshUser();
    },
    [refreshUser, user],
  );

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      loading,
      isAuthenticated: user != null,
      canWrite: canWriteProjects(user?.role),
      canConfig: canManageConfig(user?.role),
      login,
      register,
      logout,
      refreshUser,
      submitEditorAccessRequest,
    }),
    [user, loading, login, register, logout, refreshUser, submitEditorAccessRequest],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return ctx;
}
