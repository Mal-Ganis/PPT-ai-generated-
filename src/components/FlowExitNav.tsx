import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ArrowLeft, FolderOpen } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface FlowExitNavProps {
  className?: string;
}

/**
 * 主流程挂在 `/` 时，点击普通 Link `to="/"` 不会改变路由，React 不会重新挂载，步骤状态不变。
 * 因此在 pathname 为 `/` 时用 location state 触发 MainFlow 内 reset。
 */
export function FlowExitNav({ className }: FlowExitNavProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const isProjectsRoute = location.pathname === '/projects';
  const isMainFlowRoute = location.pathname === '/';

  const goHome = () => {
    if (isMainFlowRoute) {
      navigate('.', {
        replace: true,
        state: { resetMainFlow: Date.now() },
      });
      return;
    }
    navigate('/');
  };

  return (
    <div className={`flex flex-wrap items-center gap-2 ${className ?? ''}`}>
      <Button
        type="button"
        variant="outline"
        size="sm"
        onClick={goHome}
        className="border-gray-200 h-9"
      >
        <ArrowLeft className="w-4 h-4 mr-1.5 shrink-0" />
        返回首页
      </Button>
      {!isProjectsRoute && (
        <Button variant="outline" size="sm" asChild className="border-gray-200 h-9">
          <Link to="/projects">
            <FolderOpen className="w-4 h-4 mr-1.5 shrink-0" />
            项目列表
          </Link>
        </Button>
      )}
    </div>
  );
}
