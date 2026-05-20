import { ArrowLeft, ArrowRight } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  canGoToWorkflowStep,
  getNextWorkflowStep,
  getPrevWorkflowStep,
  WORKFLOW_STEP_LABELS,
  type WorkflowProgress,
  type WorkflowStep,
} from '@/lib/workflowSteps';

interface WorkflowStepActionsProps {
  currentStep: WorkflowStep;
  progress: WorkflowProgress;
  onGoToStep: (step: WorkflowStep) => void;
  busy?: boolean;
  /** 主操作（如「生成内容」「完成编辑」），放在上一步与下一步之间 */
  primaryAction?: React.ReactNode;
}

export function WorkflowStepActions({
  currentStep,
  progress,
  onGoToStep,
  busy = false,
  primaryAction,
}: WorkflowStepActionsProps) {
  const prev = getPrevWorkflowStep(currentStep);
  const next = getNextWorkflowStep(currentStep);
  const prevEnabled = prev != null && canGoToWorkflowStep(prev, progress);
  const nextEnabled = next != null && canGoToWorkflowStep(next, progress);

  return (
    <div className="flex flex-wrap items-center gap-2 sm:gap-3">
      <Button
        type="button"
        variant="outline"
        disabled={!prevEnabled || busy}
        onClick={() => prev && onGoToStep(prev)}
        className="border-gray-200 text-[#1f1f1f]"
      >
        <ArrowLeft className="w-4 h-4 mr-2 shrink-0" />
        {prev ? `上一步：${WORKFLOW_STEP_LABELS[prev]}` : '上一步'}
      </Button>
      {primaryAction}
      <Button
        type="button"
        variant="outline"
        disabled={!nextEnabled || busy}
        onClick={() => next && onGoToStep(next)}
        className="border-gray-200 text-[#1f1f1f]"
      >
        {next ? `下一步：${WORKFLOW_STEP_LABELS[next]}` : '下一步'}
        <ArrowRight className="w-4 h-4 ml-2 shrink-0" />
      </Button>
    </div>
  );
}
