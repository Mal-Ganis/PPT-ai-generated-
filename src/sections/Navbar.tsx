import { useState, useEffect } from 'react';
import { Menu, X, FileText, RotateCcw, Settings } from 'lucide-react';
import type { AppStep } from '../App';
import { canGoToWorkflowStep, type WorkflowProgress, type WorkflowStep } from '@/lib/workflowSteps';

interface NavbarProps {
  currentStep: AppStep;
  workflowProgress?: WorkflowProgress | null;
  onNavigate: (step: AppStep) => void;
  onReset: () => void;
  onOpenConfig: () => void;
}

const Navbar = ({ currentStep, workflowProgress, onNavigate, onReset, onOpenConfig }: NavbarProps) => {
  const [isScrolled, setIsScrolled] = useState(false);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);

  useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 50);
    };

    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const isInWorkflow = ['input', 'outline', 'content', 'preview'].includes(currentStep);

  const workflowSteps = [
    { key: 'input', label: '输入', number: 1 },
    { key: 'outline', label: '大纲', number: 2 },
    { key: 'content', label: '内容', number: 3 },
    { key: 'preview', label: '预览', number: 4 },
  ];

  const getStepStatus = (stepKey: string) => {
    const stepOrder = ['input', 'outline', 'content', 'preview'];
    const currentIndex = stepOrder.indexOf(currentStep);
    const stepIndex = stepOrder.indexOf(stepKey);

    if (stepKey === currentStep) return 'active';
    if (stepIndex < currentIndex) return 'completed';
    if (
      workflowProgress &&
      canGoToWorkflowStep(stepKey as WorkflowStep, workflowProgress)
    ) {
      return 'reachable';
    }
    return 'pending';
  };

  const handleWorkflowStepClick = (stepKey: string) => {
    const status = getStepStatus(stepKey);
    if (status === 'pending') return;
    onNavigate(stepKey as AppStep);
  };

  return (
    <nav
      className={`fixed top-0 left-0 right-0 z-50 transition-all duration-500 ${
        isScrolled || isInWorkflow
          ? 'bg-white/95 backdrop-blur-xl shadow-lg'
          : 'bg-transparent'
      }`}
    >
      <div className="section-container">
        <div className="section-inner">
          <div className="flex items-center justify-between h-16 lg:h-18">
            {/* Logo */}
            <div 
              className="flex items-center gap-2 cursor-pointer"
              onClick={() => {
                if (isInWorkflow) {
                  if (confirm('确定要返回首页？当前进度将丢失。')) {
                    onReset();
                  }
                } else {
                  onNavigate('home');
                }
              }}
            >
              <div className="w-8 h-8 bg-[#3898ec] rounded-lg flex items-center justify-center">
                <FileText className="w-5 h-5 text-white" />
              </div>
              <span className="font-semibold text-sm lg:text-base text-[#1f1f1f]">
                PPT 智能生成系统
              </span>
            </div>

            {/* Workflow Progress */}
            {isInWorkflow && (
              <div className="hidden md:flex items-center gap-2">
                {workflowSteps.map((step, index) => {
                  const status = getStepStatus(step.key);
                  const clickable = status !== 'pending';
                  return (
                    <div key={step.key} className="flex items-center">
                      <button
                        type="button"
                        disabled={!clickable}
                        onClick={() => handleWorkflowStepClick(step.key)}
                        className={`flex items-center gap-2 px-3 py-1.5 rounded-full text-sm font-medium transition-all duration-300 ${
                          status === 'active'
                            ? 'bg-[#3898ec] text-white'
                            : status === 'completed' || status === 'reachable'
                              ? 'bg-[#3898ec]/20 text-[#3898ec] hover:bg-[#3898ec]/30 cursor-pointer'
                              : 'bg-gray-100 text-gray-400 cursor-not-allowed'
                        }`}
                      >
                        <span
                          className={`w-5 h-5 rounded-full flex items-center justify-center text-xs ${
                            status === 'active'
                              ? 'bg-white text-[#3898ec]'
                              : status === 'completed' || status === 'reachable'
                                ? 'bg-[#3898ec] text-white'
                                : 'bg-gray-300 text-white'
                          }`}
                        >
                          {status === 'completed' ? '✓' : step.number}
                        </span>
                        <span>{step.label}</span>
                      </button>
                      {index < workflowSteps.length - 1 && (
                        <div
                          className={`w-8 h-0.5 mx-1 ${
                            status === 'completed' || status === 'active'
                              ? 'bg-[#3898ec]'
                              : 'bg-gray-200'
                          }`}
                        />
                      )}
                    </div>
                  );
                })}
              </div>
            )}

            {/* Actions */}
            <div className="flex items-center gap-2">
              <button
                onClick={onOpenConfig}
                className="hidden sm:flex items-center gap-1.5 px-3 py-1.5 text-sm text-[#1f1f1f]/60 hover:text-[#3898ec] transition-colors"
              >
                <Settings className="w-4 h-4" />
                系统配置
              </button>
              {isInWorkflow && (
                <button
                  onClick={() => {
                    if (confirm('确定要重新开始？当前进度将丢失。')) {
                      onReset();
                    }
                  }}
                  className="hidden sm:flex items-center gap-1.5 px-3 py-1.5 text-sm text-[#1f1f1f]/60 hover:text-[#e92222] transition-colors"
                >
                  <RotateCcw className="w-4 h-4" />
                  重新开始
                </button>
              )}

              {/* Mobile Menu Button */}
              <button
                className="md:hidden p-2 rounded-lg hover:bg-gray-100 transition-colors"
                onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
              >
                {isMobileMenuOpen ? (
                  <X className="w-6 h-6 text-[#1f1f1f]" />
                ) : (
                  <Menu className="w-6 h-6 text-[#1f1f1f]" />
                )}
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Mobile Menu */}
      <div
        className={`md:hidden absolute top-full left-0 right-0 bg-white/95 backdrop-blur-xl shadow-lg transition-all duration-300 ${
          isMobileMenuOpen
            ? 'opacity-100 translate-y-0'
            : 'opacity-0 -translate-y-4 pointer-events-none'
        }`}
      >
        <div className="section-container py-4">
          {isInWorkflow ? (
            <>
              {workflowSteps.map((step) => {
                const status = getStepStatus(step.key);
                return (
                  <button
                    key={step.key}
                    onClick={() => {
                      if (status !== 'pending') {
                        handleWorkflowStepClick(step.key);
                        setIsMobileMenuOpen(false);
                      }
                    }}
                    className={`block w-full text-left px-4 py-3 rounded-lg transition-colors ${
                      status === 'active'
                        ? 'bg-[#3898ec]/10 text-[#3898ec] font-medium'
                        : status === 'completed' || status === 'reachable'
                          ? 'text-[#1f1f1f] hover:bg-gray-50'
                          : 'text-gray-400 cursor-not-allowed'
                    }`}
                  >
                    {step.number}. {step.label}
                  </button>
                );
              })}
              <div className="border-t border-gray-200 mt-2 pt-2">
                <button
                  onClick={() => {
                    if (confirm('确定要重新开始？当前进度将丢失。')) {
                      onReset();
                      setIsMobileMenuOpen(false);
                    }
                  }}
                  className="block w-full text-left px-4 py-3 text-[#e92222] hover:bg-red-50 rounded-lg transition-colors"
                >
                  <RotateCcw className="w-4 h-4 inline mr-2" />
                  重新开始
                </button>
              </div>
            </>
          ) : (
            <button
              onClick={() => {
                onNavigate('input');
                setIsMobileMenuOpen(false);
              }}
              className="block w-full text-left px-4 py-3 text-[#1f1f1f] hover:bg-gray-50 rounded-lg transition-colors"
            >
              开始体验
            </button>
          )}
        </div>
      </div>
    </nav>
  );
};

export default Navbar;
