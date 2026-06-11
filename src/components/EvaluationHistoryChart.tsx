import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { EvaluationReport } from '@/lib/backend';
import { chartReports } from '@/lib/evaluationDisplay';

interface EvaluationHistoryChartProps {
  reports: EvaluationReport[];
}

export function EvaluationHistoryChart({ reports }: EvaluationHistoryChartProps) {
  const data = chartReports(reports).map((r) => ({
    id: `#${r.id}`,
    time: new Date(r.evaluationTime).toLocaleDateString(undefined, {
      month: 'numeric',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }),
    autoTotal: r.autoTotalScore ?? null,
    sourceCov: r.autoSourceCoverageScore ?? null,
    humanTotal: r.totalScore,
  }));

  if (data.length < 2) {
    return (
      <p className="text-sm text-[#1f1f1f]/50">
        至少 2 条评估记录后可展示历史折线图。
      </p>
    );
  }

  return (
    <div className="h-64 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
          <XAxis dataKey="time" tick={{ fontSize: 11 }} />
          <YAxis domain={[0, 100]} tick={{ fontSize: 11 }} />
          <Tooltip
            formatter={(value: number, name: string) => [
              typeof value === 'number' ? `${value.toFixed(1)} 分` : value,
              name,
            ]}
          />
          <Legend />
          <Line
            type="monotone"
            dataKey="autoTotal"
            name="自动总分（百分制）"
            stroke="#0f766e"
            strokeWidth={2}
            dot={{ r: 3 }}
            connectNulls
          />
          <Line
            type="monotone"
            dataKey="sourceCov"
            name="引用覆盖（百分制）"
            stroke="#3898ec"
            strokeWidth={2}
            dot={{ r: 3 }}
            connectNulls
          />
          <Line
            type="monotone"
            dataKey="humanTotal"
            name="人工加权（百分制）"
            stroke="#9333ea"
            strokeWidth={2}
            dot={{ r: 3 }}
            connectNulls
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
