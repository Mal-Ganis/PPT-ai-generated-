import { useEffect, useState } from 'react';
import { ArrowLeft, CircleCheck, Cpu } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { fetchSystemConfig, saveSystemConfig } from '@/lib/backend';
import type { SystemConfig } from '@/lib/backend';

interface SystemConfigSectionProps {
  onBack: () => void;
  onSave: (config: SystemConfig) => void;
}

const SystemConfigSection = ({ onBack, onSave }: SystemConfigSectionProps) => {
  const [config, setConfig] = useState<SystemConfig | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    const loadConfig = async () => {
      try {
        const data = await fetchSystemConfig();
        setConfig(data);
      } catch (err) {
        setError(err instanceof Error ? err.message : '读取系统配置失败');
      } finally {
        setIsLoading(false);
      }
    };

    loadConfig();
  }, []);

  const handleSave = async () => {
    if (!config) return;
    setError('');
    setMessage('');
    setIsSaving(true);

    try {
      const saved = await saveSystemConfig(config);
      setConfig(saved);
      onSave(saved);
      setMessage('系统配置已保存并立即生效');
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存失败');
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
    return (
      <section className="min-h-screen pt-24 pb-16 bg-[#f3f3f3]">
        <div className="section-container">
          <div className="section-inner max-w-4xl">
            <div className="rounded-3xl border border-gray-200 bg-white p-10 text-center text-[#1f1f1f]/70">
              正在加载系统配置...
            </div>
          </div>
        </div>
      </section>
    );
  }

  if (!config) {
    return (
      <section className="min-h-screen pt-24 pb-16 bg-[#f3f3f3]">
        <div className="section-container">
          <div className="section-inner max-w-4xl">
            <div className="rounded-3xl border border-red-200 bg-red-50 p-10 text-center text-red-700">
              无法读取配置，请稍后重试。
            </div>
          </div>
        </div>
      </section>
    );
  }

  return (
    <section className="min-h-screen pt-24 pb-16 bg-[#f3f3f3]">
      <div className="section-container">
        <div className="section-inner max-w-5xl">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-8">
            <div>
              <h1 className="text-3xl font-bold text-[#1f1f1f]">系统配置</h1>
              <p className="text-[#1f1f1f]/60 mt-2">
                修改 LLM 参数、Prompt 模板和检索策略，保存后立即生效。
              </p>
            </div>
            <div className="flex items-center gap-3">
              <Cpu className="w-5 h-5 text-[#3898ec]" />
              <span className="text-sm text-[#1f1f1f]/60">持久化到后端</span>
            </div>
          </div>

          <div className="rounded-3xl border border-gray-200 bg-white p-8 shadow-sm space-y-6">
            {error && (
              <div className="rounded-2xl bg-red-50 border border-red-200 p-4 text-sm text-red-700">
                {error}
              </div>
            )}

            {message && (
              <div className="rounded-2xl bg-emerald-50 border border-emerald-200 p-4 text-sm text-emerald-700">
                {message}
              </div>
            )}

            <div className="grid gap-4 lg:grid-cols-2">
              <label className="space-y-2">
                <span className="text-sm font-medium text-[#1f1f1f]">LLM 模型名称</span>
                <input
                  value={config.llmModel}
                  onChange={(event) => setConfig({ ...config, llmModel: event.target.value })}
                  className="w-full rounded-2xl border border-gray-200 px-4 py-3 text-sm outline-none focus:border-[#3898ec] focus:ring-2 focus:ring-[#3898ec]/20"
                />
              </label>
              <label className="space-y-2">
                <span className="text-sm font-medium text-[#1f1f1f]">temperature</span>
                <input
                  type="number"
                  step="0.05"
                  min="0"
                  max="2"
                  value={config.temperature}
                  onChange={(event) => setConfig({ ...config, temperature: Number(event.target.value) })}
                  className="w-full rounded-2xl border border-gray-200 px-4 py-3 text-sm outline-none focus:border-[#3898ec] focus:ring-2 focus:ring-[#3898ec]/20"
                />
              </label>
              <label className="space-y-2">
                <span className="text-sm font-medium text-[#1f1f1f]">max tokens</span>
                <input
                  type="number"
                  min="1"
                  value={config.maxTokens}
                  onChange={(event) => setConfig({ ...config, maxTokens: Number(event.target.value) })}
                  className="w-full rounded-2xl border border-gray-200 px-4 py-3 text-sm outline-none focus:border-[#3898ec] focus:ring-2 focus:ring-[#3898ec]/20"
                />
              </label>
              <label className="space-y-2">
                <span className="text-sm font-medium text-[#1f1f1f]">top_p</span>
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  max="1"
                  value={config.topP}
                  onChange={(event) => setConfig({ ...config, topP: Number(event.target.value) })}
                  className="w-full rounded-2xl border border-gray-200 px-4 py-3 text-sm outline-none focus:border-[#3898ec] focus:ring-2 focus:ring-[#3898ec]/20"
                />
              </label>
              <label className="space-y-2">
                <span className="text-sm font-medium text-[#1f1f1f]">top_k</span>
                <input
                  type="number"
                  min="1"
                  value={config.topK}
                  onChange={(event) => setConfig({ ...config, topK: Number(event.target.value) })}
                  className="w-full rounded-2xl border border-gray-200 px-4 py-3 text-sm outline-none focus:border-[#3898ec] focus:ring-2 focus:ring-[#3898ec]/20"
                />
              </label>
              <label className="space-y-2">
                <span className="text-sm font-medium text-[#1f1f1f]">检索条数限制</span>
                <input
                  type="number"
                  min="1"
                  value={config.retrievalLimit}
                  onChange={(event) => setConfig({ ...config, retrievalLimit: Number(event.target.value) })}
                  className="w-full rounded-2xl border border-gray-200 px-4 py-3 text-sm outline-none focus:border-[#3898ec] focus:ring-2 focus:ring-[#3898ec]/20"
                />
              </label>
            </div>

            <div className="grid gap-4">
              <label className="space-y-2">
                <span className="text-sm font-medium text-[#1f1f1f]">大纲生成 Prompt 模板</span>
                <textarea
                  rows={6}
                  value={config.outlinePromptTemplate}
                  onChange={(event) => setConfig({ ...config, outlinePromptTemplate: event.target.value })}
                  className="w-full rounded-3xl border border-gray-200 px-4 py-3 text-sm outline-none focus:border-[#3898ec] focus:ring-2 focus:ring-[#3898ec]/20"
                />
              </label>
              <label className="space-y-2">
                <span className="text-sm font-medium text-[#1f1f1f]">内容补全 Prompt 模板</span>
                <textarea
                  rows={6}
                  value={config.slidePromptTemplate}
                  onChange={(event) => setConfig({ ...config, slidePromptTemplate: event.target.value })}
                  className="w-full rounded-3xl border border-gray-200 px-4 py-3 text-sm outline-none focus:border-[#3898ec] focus:ring-2 focus:ring-[#3898ec]/20"
                />
              </label>
            </div>

            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <Button onClick={onBack} variant="outline" className="border-gray-200 text-[#1f1f1f] gap-2">
                <ArrowLeft className="w-4 h-4" /> 返回
              </Button>
              <Button onClick={handleSave} disabled={isSaving} className="gap-2">
                <CircleCheck className="w-4 h-4" /> 保存配置
              </Button>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};

export default SystemConfigSection;
