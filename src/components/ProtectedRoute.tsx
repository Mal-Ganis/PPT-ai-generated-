import { Navigate, useLocation } from 'react-router-dom';
import { useEffect, useRef } from 'react';
import { toast } from 'sonner';
import { AuthSplash } from '@/components/AuthSplash';
import { useAuth } from '@/contexts/AuthContext';
import { canWriteProjects, type UserRole } from '@/lib/permissions';

interface ProtectedRouteProps {
  children: React.ReactNode;
  /** 需要具备编辑能力（管理员或编辑者） */
  requireWrite?: boolean;
  roles?: UserRole[];
}

export function ProtectedRoute({ children, requireWrite, roles }: ProtectedRouteProps) {
  const { isAuthenticated, loading, user } = useAuth();
  const location = useLocation();
  const deniedToastRef = useRef(false);

  const lacksRole = Boolean(roles && user && !roles.includes(user.role));
  const lacksWrite = Boolean(requireWrite && user && !canWriteProjects(user.role));
  const denied = lacksRole || lacksWrite;

  useEffect(() => {
    if (loading || !isAuthenticated || !denied || deniedToastRef.current) {
      return;
    }
    deniedToastRef.current = true;
    const label = user?.role === 'VIEWER' ? '只读访客' : '当前账号';
    toast.error(`${label}无权访问该页面，请确认已用管理员/编辑者账号登录`);
  }, [loading, isAuthenticated, denied, user?.role]);

  if (loading) {
    return <AuthSplash />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  if (denied) {
    return <Navigate to="/" replace />;
  }

  return children;
}
