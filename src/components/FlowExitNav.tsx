import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ArrowLeft, FolderOpen } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface FlowExitNavProps {
  className?: string;
}

/**
 * 主流程在 `/` 的 state 在切到子路由（如单页详情）时会随 MainFlow 卸载而丢失；
 * 流程进度由 sessionStorage 持久化，子路由应使用「返回流程」而非裸 navigate('/')。
 */
export function FlowExitNav({ className }: FlowExitNavProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const isProjectsRoute = location.pathname === '/projects';
  const isMainFlowRoute = location.pathname === '/';

  const goHome = () => {
    const msg = '确定返回首页？将结束当前编辑流程（已保存到服务器的项目仍可在「项目列表」中打开）。';
    if (!window.confirm(msg)) return;
    if (isMainFlowRoute) {
      navigate('.', {
        replace: true,
        state: { resetMainFlow: Date.now() },
      });
      return;
    }
    navigate('/', { replace: true, state: { resetMainFlow: Date.now() } });
  };

  const resumeFlow = () => {
    navigate('/', { replace: true, state: { resumeMainFlow: Date.now() } });
  };

  return (
    <div className={`flex flex-wrap items-center gap-2 ${className ?? ''}`}>
      {!isMainFlowRoute ? (
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={resumeFlow}
          className="border-[#3898ec]/40 text-[#3898ec] h-9"
        >
          <ArrowLeft className="w-4 h-4 mr-1.5 shrink-0" />
          返回流程
        </Button>
      ) : (
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
      )}
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
