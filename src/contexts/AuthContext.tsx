import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { login as apiLogin, fetchCurrentUser, register as apiRegister } from '@/lib/backend';
import {
  clearAuthSession,
  getAuthToken,
  getStoredUser,
  saveAuthSession,
  type AuthUser,
} from '@/lib/authStorage';
import { canManageConfig, canWriteProjects } from '@/lib/permissions';
import type { UserRole } from '@/lib/permissions';

interface AuthContextValue {
  user: AuthUser | null;
  loading: boolean;
  isAuthenticated: boolean;
  canWrite: boolean;
  canConfig: boolean;
  login: (username: string, password: string) => Promise<void>;
  register: (username: string, password: string, displayName?: string) => Promise<void>;
  logout: () => void;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(() => getStoredUser());
  const [loading, setLoading] = useState(() => Boolean(getAuthToken()));

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
      const next: AuthUser = {
        id: profile.id,
        username: profile.username,
        displayName: profile.displayName,
        role: profile.role as UserRole,
      };
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

  const login = useCallback(async (username: string, password: string) => {
    const res = await apiLogin(username, password);
    const next: AuthUser = {
      id: res.user.id,
      username: res.user.username,
      displayName: res.user.displayName,
      role: res.user.role as UserRole,
    };
    saveAuthSession(res.token, next);
    setUser(next);
  }, []);

  const register = useCallback(async (username: string, password: string, displayName?: string) => {
    const res = await apiRegister(username, password, displayName);
    const next: AuthUser = {
      id: res.user.id,
      username: res.user.username,
      displayName: res.user.displayName,
      role: res.user.role as UserRole,
    };
    saveAuthSession(res.token, next);
    setUser(next);
  }, []);

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
    }),
    [user, loading, login, register, logout, refreshUser],
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
