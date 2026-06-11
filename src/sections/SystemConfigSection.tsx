import { useEffect, useState } from 'react';
import { ArrowLeft, CircleCheck, Cpu, KeyRound, Plus, RotateCcw, Trash2 } from 'lucide-react';
import { FlowExitNav } from '@/components/FlowExitNav';
import { Button } from '@/components/ui/button';
import { fetchSystemConfig, resetSystemConfigToDefaults, saveSystemConfig } from '@/lib/backend';
import type { LlmApiKeyPreset, SystemConfig } from '@/lib/backend';

interface SystemConfigSectionProps {
  onBack: () => void;
  onSave: (config: SystemConfig) => void;
}

const SystemConfigSection = ({ onBack, onSave }: SystemConfigSectionProps) => {
  const [config, setConfig] = useState<SystemConfig | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isResetting, setIsResetting] = useState(false);
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

  const updatePresets = (next: LlmApiKeyPreset[]) => {
    if (!config) return;
    setConfig({ ...config, llmApiKeyPresets: next });
  };

  const handleAddPreset = () => {
    const presets = config?.llmApiKeyPresets ?? [];
    if (presets.length >= 20) return;
    updatePresets([...presets, { id: crypto.randomUUID(), label: '', apiKey: '', baseUrl: '', model: '' }]);
  };

  const handleRemovePreset = (index: number) => {
    const presets = config?.llmApiKeyPresets ?? [];
    updatePresets(presets.filter((_, i) => i !== index));
  };

  const handleResetDefaults = async () => {
    if (
      !confirm(
        '确定将 LLM 参数与两份 Prompt 模板重置为后端内置默认值吗？会立即写入数据库；当前表单里未保存的修改将被覆盖。',
      )
    ) {
      return;
    }
    setError('');
    setMessage('');
    setIsResetting(true);
    try {
      const restored = await resetSystemConfigToDefaults();
      setConfig(restored);
      onSave(restored);
      setMessage('已重置为内置默认配置并保存到后端（含新版大纲模板）。');
    } catch (err) {
      setError(err instanceof Error ? err.message : '重置失败');
    } finally {
      setIsResetting(false);
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
          <FlowExitNav className="mb-4" />
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
              <label className="space-y-2 lg:col-span-2">
                <span className="text-sm font-medium text-[#1f1f1f]">默认 LLM 接口 Base URL</span>
                <input
                  value={config.llmBaseUrl ?? ''}
                  onChange={(event) => setConfig({ ...config, llmBaseUrl: event.target.value })}
                  placeholder="https://api.deepseek.com 或 https://api.openai.com/v1"
                  className="w-full rounded-2xl border border-gray-200 px-4 py-3 text-sm font-mono outline-none focus:border-[#3898ec] focus:ring-2 focus:ring-[#3898ec]/20"
                />
              </label>
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
              <label className="flex items-center gap-3 lg:col-span-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={config.outlineIncludeQaSlide !== false}
                  onChange={(event) =>
                    setConfig({ ...config, outlineIncludeQaSlide: event.target.checked })
                  }
                  className="h-4 w-4 rounded border-gray-300 text-[#3898ec] focus:ring-[#3898ec]"
                />
                <span className="text-sm text-[#1f1f1f]">
                  大纲统一包含 Q&A 页（开启后每份大纲必有「Q&A/问答」页，模型未生成时自动补页）
                </span>
              </label>
            </div>

            <div className="rounded-3xl border border-violet-100 bg-violet-50/40 p-6 space-y-4">
              <div>
                <h2 className="text-sm font-semibold text-[#1f1f1f]">正文质量自纠错（Tier1 / Tier2）</h2>
                <p className="text-xs text-[#1f1f1f]/55 mt-1">
                  正文生成完成后写入自动评估；若分数低于阈值，系统将重生成薄弱页或全量重跑。关闭时不影响评估报告写入。
                </p>
              </div>
              <label className="flex items-center gap-3 cursor-pointer">
                <input
                  type="checkbox"
                  checked={config.selfCorrectionEnabled === true}
                  onChange={(event) =>
                    setConfig({ ...config, selfCorrectionEnabled: event.target.checked })
                  }
                  className="h-4 w-4 rounded border-gray-300 text-[#3898ec] focus:ring-[#3898ec]"
                />
                <span className="text-sm text-[#1f1f1f]">启用正文自纠错闭环</span>
              </label>
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                <label className="space-y-1">
                  <span className="text-xs font-medium text-[#1f1f1f]/70">Tier1 自动分下限</span>
                  <input
                    type="number"
                    min="0"
                    max="100"
                    value={config.selfCorrectionTier1AutoBelow ?? 76}
                    onChange={(event) =>
                      setConfig({ ...config, selfCorrectionTier1AutoBelow: Number(event.target.value) })
                    }
                    className="w-full rounded-xl border border-gray-200 px-3 py-2 text-sm"
                  />
                </label>
                <label className="space-y-1">
                  <span className="text-xs font-medium text-[#1f1f1f]/70">Tier1 事实率下限</span>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    max="1"
                    value={config.selfCorrectionTier1FactBelow ?? 0.58}
                    onChange={(event) =>
                      setConfig({ ...config, selfCorrectionTier1FactBelow: Number(event.target.value) })
                    }
                    className="w-full rounded-xl border border-gray-200 px-3 py-2 text-sm"
                  />
                </label>
                <label className="space-y-1">
                  <span className="text-xs font-medium text-[#1f1f1f]/70">Tier2 自动分下限</span>
                  <input
                    type="number"
                    min="0"
                    max="100"
                    value={config.selfCorrectionTier2AutoBelow ?? 70}
                    onChange={(event) =>
                      setConfig({ ...config, selfCorrectionTier2AutoBelow: Number(event.target.value) })
                    }
                    className="w-full rounded-xl border border-gray-200 px-3 py-2 text-sm"
                  />
                </label>
                <label className="space-y-1">
                  <span className="text-xs font-medium text-[#1f1f1f]/70">Tier2 事实率下限</span>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    max="1"
                    value={config.selfCorrectionTier2FactBelow ?? 0.48}
                    onChange={(event) =>
                      setConfig({ ...config, selfCorrectionTier2FactBelow: Number(event.target.value) })
                    }
                    className="w-full rounded-xl border border-gray-200 px-3 py-2 text-sm"
                  />
                </label>
              </div>
            </div>

            <div className="rounded-3xl border border-gray-100 bg-[#f9fafb] p-6 space-y-4">
              <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
                <div className="flex items-center gap-2">
                  <KeyRound className="w-5 h-5 text-[#3898ec]" />
                  <div>
                    <h2 className="text-sm font-semibold text-[#1f1f1f]">LLM API Key 预设池</h2>
                    <p className="text-xs text-[#1f1f1f]/55 mt-1">
                      供生成页下拉选择；适用于各类兼容 OpenAI 格式的 LLM 服务。完整密钥仅保存在服务器，列表接口对普通用户脱敏展示。
                    </p>
                  </div>
                </div>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={handleAddPreset}
                  disabled={(config.llmApiKeyPresets?.length ?? 0) >= 20}
                  className="gap-1 border-gray-200"
                >
                  <Plus className="w-4 h-4" /> 添加预设
                </Button>
              </div>

              {(config.llmApiKeyPresets?.length ?? 0) === 0 ? (
                <p className="text-sm text-[#1f1f1f]/50">暂无预设，点击「添加预设」维护密钥池。</p>
              ) : (
                <div className="space-y-3">
                  {(config.llmApiKeyPresets ?? []).map((preset, index) => (
                    <div
                      key={preset.id || index}
                      className="grid gap-3 lg:grid-cols-[1fr_1.2fr_1.2fr_1fr_auto] items-start rounded-2xl border border-gray-200 bg-white p-4"
                    >
                      <label className="space-y-1">
                        <span className="text-xs font-medium text-[#1f1f1f]/70">显示名称</span>
                        <input
                          value={preset.label}
                          onChange={(event) => {
                            const next = [...(config.llmApiKeyPresets ?? [])];
                            next[index] = { ...preset, label: event.target.value };
                            updatePresets(next);
                          }}
                          placeholder="例如：团队共享密钥"
                          className="w-full rounded-xl border border-gray-200 px-3 py-2 text-sm outline-none focus:border-[#3898ec] focus:ring-2 focus:ring-[#3898ec]/20"
                        />
                      </label>
                      <label className="space-y-1">
                        <span className="text-xs font-medium text-[#1f1f1f]/70">Base URL</span>
                        <input
                          value={preset.baseUrl ?? ''}
                          onChange={(event) => {
                            const next = [...(config.llmApiKeyPresets ?? [])];
                            next[index] = { ...preset, baseUrl: event.target.value };
                            updatePresets(next);
                          }}
                          placeholder="留空则用系统默认"
                          className="w-full rounded-xl border border-gray-200 px-3 py-2 text-sm font-mono outline-none focus:border-[#3898ec] focus:ring-2 focus:ring-[#3898ec]/20"
                        />
                      </label>
                      <label className="space-y-1">
                        <span className="text-xs font-medium text-[#1f1f1f]/70">模型</span>
                        <input
                          value={preset.model ?? ''}
                          onChange={(event) => {
                            const next = [...(config.llmApiKeyPresets ?? [])];
                            next[index] = { ...preset, model: event.target.value };
                            updatePresets(next);
                          }}
                          placeholder="如 gpt-4o-mini"
                          className="w-full rounded-xl border border-gray-200 px-3 py-2 text-sm font-mono outline-none focus:border-[#3898ec] focus:ring-2 focus:ring-[#3898ec]/20"
                        />
                      </label>
                      <label className="space-y-1">
                        <span className="text-xs font-medium text-[#1f1f1f]/70">API Key</span>
                        <input
                          type="password"
                          value={preset.apiKey ?? ''}
                          onChange={(event) => {
                            const next = [...(config.llmApiKeyPresets ?? [])];
                            next[index] = { ...preset, apiKey: event.target.value };
                            updatePresets(next);
                          }}
                          placeholder="粘贴 API Key"
                          autoComplete="off"
                          className="w-full rounded-xl border border-gray-200 px-3 py-2 text-sm font-mono outline-none focus:border-[#3898ec] focus:ring-2 focus:ring-[#3898ec]/20"
                        />
                        {preset.maskedKey && !preset.apiKey && (
                          <span className="text-xs text-[#1f1f1f]/45">已保存：{preset.maskedKey}</span>
                        )}
                      </label>
                      <Button
                        type="button"
                        variant="outline"
                        size="icon"
                        onClick={() => handleRemovePreset(index)}
                        className="mt-6 border-red-200 text-red-600 hover:bg-red-50 shrink-0"
                        aria-label="删除预设"
                      >
                        <Trash2 className="w-4 h-4" />
                      </Button>
                    </div>
                  ))}
                </div>
              )}
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
              <div className="flex flex-wrap gap-3 sm:justify-end">
                <Button
                  type="button"
                  variant="outline"
                  onClick={handleResetDefaults}
                  disabled={isSaving || isResetting}
                  className="gap-2 border-amber-200 text-amber-900 hover:bg-amber-50"
                >
                  <RotateCcw className={`w-4 h-4 ${isResetting ? 'animate-spin' : ''}`} />
                  {isResetting ? '重置中…' : '重置为默认配置'}
                </Button>
                <Button onClick={handleSave} disabled={isSaving || isResetting} className="gap-2">
                  <CircleCheck className="w-4 h-4" /> {isSaving ? '保存中…' : '保存配置'}
                </Button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};

export default SystemConfigSection;
