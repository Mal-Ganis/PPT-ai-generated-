import { useState, useRef, useEffect } from 'react';
import { Type, FileUp, ArrowRight, Sparkles, Loader2, X, FileText, Clock, UserCog, KeyRound } from 'lucide-react';
import {
  PRESENTATION_DURATION_OPTIONS,
  DEFAULT_PRESENTATION_DURATION_MINUTES,
  fetchLlmApiKeyPresets,
  type LlmApiKeyPreset,
} from '@/lib/backend';
import { LLM_PROVIDER_TEMPLATES, selectionFromStored, type LlmApiKeyMode } from '@/lib/llmApiKey';
import { FlowExitNav } from '@/components/FlowExitNav';
import { WorkflowStepActions } from '@/components/WorkflowStepActions';
import type { WorkflowProgress, WorkflowStep } from '@/lib/workflowSteps';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import * as mammoth from 'mammoth';

interface InputSectionProps {
  onSubmit: (
    type: 'topic' | 'document',
    content: string,
    meta?: {
      fileName?: string;
      formData?: FormData;
      presentationDurationMinutes?: number;
      presenterRole?: string;
      llmApiKeyPresetId?: string | null;
      llmApiKeyOverride?: string;
      llmBaseUrlOverride?: string;
      llmModelOverride?: string;
    },
  ) => Promise<void>;
  workflowProgress: WorkflowProgress;
  onGoToStep: (step: WorkflowStep) => void;
  initialTopic?: string;
  initialInputType?: 'topic' | 'document';
  initialPresentationMinutes?: number;
  initialPresenterRole?: string;
  initialLlmApiKeyPresetId?: string | null;
  initialLlmApiKeyOverride?: string;
  initialLlmBaseUrlOverride?: string;
  initialLlmModelOverride?: string;
  /** 管理员/编辑者可自定义演示角色；只读用户不可进入此页 */
  canCustomizeRole?: boolean;
}

const InputSection = ({
  onSubmit,
  workflowProgress,
  onGoToStep,
  initialTopic,
  initialInputType,
  initialPresentationMinutes,
  initialPresenterRole,
  initialLlmApiKeyPresetId,
  initialLlmApiKeyOverride,
  initialLlmBaseUrlOverride,
  initialLlmModelOverride,
  canCustomizeRole = true,
}: InputSectionProps) => {
  const [inputType, setInputType] = useState<'topic' | 'document'>('topic');
  const [topic, setTopic] = useState('');
  const [presenterRole, setPresenterRole] = useState('');
  const [uploadedFile, setUploadedFile] = useState<File | null>(null);
  const [documentContent, setDocumentContent] = useState<string>('');
  const [isLoading, setIsLoading] = useState(false);
  const [statusMessage, setStatusMessage] = useState('');
  const [presentationMinutes, setPresentationMinutes] = useState<number>(
    DEFAULT_PRESENTATION_DURATION_MINUTES,
  );
  const [llmKeyMode, setLlmKeyMode] = useState<LlmApiKeyMode>('default');
  const [selectedPresetId, setSelectedPresetId] = useState('');
  const [customApiKey, setCustomApiKey] = useState('');
  const [customBaseUrl, setCustomBaseUrl] = useState('');
  const [customModel, setCustomModel] = useState('');
  const [keyPresets, setKeyPresets] = useState<LlmApiKeyPreset[]>([]);
  const [presetsLoading, setPresetsLoading] = useState(true);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (initialTopic != null) setTopic(initialTopic);
    if (initialInputType) setInputType(initialInputType);
    if (initialPresentationMinutes != null) setPresentationMinutes(initialPresentationMinutes);
    if (initialPresenterRole != null) setPresenterRole(initialPresenterRole);
  }, [initialTopic, initialInputType, initialPresentationMinutes, initialPresenterRole]);

  useEffect(() => {
    const stored = selectionFromStored(
      initialLlmApiKeyPresetId,
      initialLlmApiKeyOverride,
      initialLlmBaseUrlOverride,
      initialLlmModelOverride,
    );
    setLlmKeyMode(stored.mode);
    setSelectedPresetId(stored.presetId ?? '');
    setCustomApiKey(stored.override ?? '');
    setCustomBaseUrl(stored.baseUrlOverride ?? '');
    setCustomModel(stored.modelOverride ?? '');
  }, [
    initialLlmApiKeyPresetId,
    initialLlmApiKeyOverride,
    initialLlmBaseUrlOverride,
    initialLlmModelOverride,
  ]);

  useEffect(() => {
    let cancelled = false;
    const loadPresets = async () => {
      try {
        const list = await fetchLlmApiKeyPresets();
        if (!cancelled) setKeyPresets(list);
      } catch {
        if (!cancelled) setKeyPresets([]);
      } finally {
        if (!cancelled) setPresetsLoading(false);
      }
    };
    loadPresets();
    return () => {
      cancelled = true;
    };
  }, []);

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setUploadedFile(file);
      const lower = file.name.toLowerCase();
      if (lower.endsWith('.pdf')) {
        setDocumentContent('');
        return;
      }
      setIsLoading(true);
      try {
        if (lower.endsWith('.docx') || lower.endsWith('.doc')) {
          const arrayBuffer = await file.arrayBuffer();
          const result = await mammoth.extractRawText({ arrayBuffer });
          setDocumentContent(result.value);
        } else if (lower.endsWith('.txt')) {
          setDocumentContent(await file.text());
        } else {
          setDocumentContent('');
        }
      } catch (error) {
        console.error('Error reading document:', error);
        alert('文档读取失败；若为 PDF/DOCX，可直接生成大纲（服务端解析）。');
      } finally {
        setIsLoading(false);
      }
    }
  };

  const handleSubmit = async () => {
    setIsLoading(true);
    setStatusMessage('AI 正在全力工作，请稍候...');

    try {
      const llmMeta =
        llmKeyMode === 'preset' && selectedPresetId
          ? {
              llmApiKeyPresetId: selectedPresetId,
              llmApiKeyOverride: undefined,
              llmBaseUrlOverride: undefined,
              llmModelOverride: undefined,
            }
          : llmKeyMode === 'custom' && customApiKey.trim()
            ? {
                llmApiKeyPresetId: null,
                llmApiKeyOverride: customApiKey.trim(),
                llmBaseUrlOverride: customBaseUrl.trim() || undefined,
                llmModelOverride: customModel.trim() || undefined,
              }
            : {
                llmApiKeyPresetId: null,
                llmApiKeyOverride: undefined,
                llmBaseUrlOverride: undefined,
                llmModelOverride: undefined,
              };
      const durationMeta = {
        presentationDurationMinutes: presentationMinutes,
        presenterRole: canCustomizeRole ? presenterRole.trim() || undefined : undefined,
        ...llmMeta,
      };
      if (inputType === 'topic') {
        await onSubmit('topic', topic, durationMeta);
      } else if (uploadedFile) {
        const fd = new FormData();
        fd.append('file', uploadedFile);
        await onSubmit('document', '', { formData: fd, fileName: uploadedFile.name, ...durationMeta });
      } else {
        await onSubmit('document', documentContent || '', durationMeta);
      }
    } finally {
      setIsLoading(false);
      setStatusMessage('');
    }
  };

  const isValid =
    (inputType === 'topic'
      ? topic.trim().length > 0
      : uploadedFile !== null || documentContent.trim().length > 0) &&
    (llmKeyMode !== 'preset' || selectedPresetId !== '') &&
    (llmKeyMode !== 'custom' || customApiKey.trim().length > 0);

  const exampleTopics = [
    '人工智能在教育领域的应用',
    '新能源汽车市场分析报告',
    '数字化转型战略规划',
    '产品发布会演示方案',
  ];

  const exampleRoles = [
    '高校课程讲师',
    '技术方案架构师',
    '投融资分析师',
    '科普讲解者',
  ];

  return (
    <section className="min-h-screen pt-24 pb-16 bg-[#f3f3f3]">
      <div className="section-container">
        <div className="section-inner max-w-4xl">
          <FlowExitNav className="mb-6" />
          {workflowProgress.hasOutline && (
            <div className="mb-6 flex justify-center">
              <WorkflowStepActions
                currentStep="input"
                progress={workflowProgress}
                onGoToStep={onGoToStep}
                busy={isLoading}
              />
            </div>
          )}
          {/* Header */}
          <div className="text-center mb-10">
            <h1 className="text-3xl sm:text-4xl font-bold text-[#1f1f1f] mb-3">
              开始创建您的 PPT
            </h1>
            <p className="text-[#1f1f1f]/60">
              选择输入方式；文档/PDF 将由服务端解析（PDFBox / POI）并写入向量索引
            </p>
          </div>

          {/* Input Type Selection */}
          <div className="bg-white rounded-2xl shadow-lg p-2 mb-8">
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setInputType('topic')}
                className={`flex-1 flex items-center justify-center gap-2 py-4 px-6 rounded-xl transition-all duration-300 ${
                  inputType === 'topic'
                    ? 'bg-[#3898ec] text-white shadow-md'
                    : 'text-[#1f1f1f]/60 hover:bg-gray-50'
                }`}
              >
                <Type className="w-5 h-5" />
                <span className="font-medium">输入主题</span>
              </button>
              <button
                type="button"
                onClick={() => setInputType('document')}
                className={`flex-1 flex items-center justify-center gap-2 py-4 px-6 rounded-xl transition-all duration-300 ${
                  inputType === 'document'
                    ? 'bg-[#3898ec] text-white shadow-md'
                    : 'text-[#1f1f1f]/60 hover:bg-gray-50'
                }`}
              >
                <FileUp className="w-5 h-5" />
                <span className="font-medium">上传文档</span>
              </button>
            </div>
          </div>

          <div className="bg-white rounded-2xl shadow-lg p-6 mb-8">
            <div className="flex items-center gap-2 mb-3">
              <Clock className="w-5 h-5 text-[#3898ec]" />
              <span className="text-sm font-medium text-[#1f1f1f]">目标演讲时长</span>
            </div>
            <p className="text-sm text-[#1f1f1f]/55 mb-4">
              AI 将据此控制大纲页数与正文要点密度，避免内容过多难以口头讲解（默认 15 分钟）。
            </p>
            <div className="flex flex-wrap gap-2">
              {PRESENTATION_DURATION_OPTIONS.map((m) => (
                <button
                  key={m}
                  type="button"
                  onClick={() => setPresentationMinutes(m)}
                  className={`px-4 py-2 rounded-full text-sm font-medium transition-all ${
                    presentationMinutes === m
                      ? 'bg-[#3898ec] text-white shadow-md'
                      : 'bg-[#f3f3f3] text-[#1f1f1f]/70 hover:bg-[#3898ec]/10 hover:text-[#3898ec]'
                  }`}
                >
                  {m} 分钟
                </button>
              ))}
            </div>
          </div>

          <div className="bg-white rounded-2xl shadow-lg p-6 mb-8">
            <div className="flex items-center gap-2 mb-3">
              <KeyRound className="w-5 h-5 text-[#3898ec]" />
              <span className="text-sm font-medium text-[#1f1f1f]">LLM API Key</span>
            </div>
            <p className="text-sm text-[#1f1f1f]/55 mb-4">
              默认使用服务器配置的 LLM 接口与密钥；也可选择管理员预设（可绑定不同服务商），或仅本次生成使用自定义配置。
            </p>
            <div className="flex flex-wrap gap-2 mb-4">
              {[
                { mode: 'default' as const, label: '服务器默认' },
                { mode: 'preset' as const, label: '管理员预设' },
                { mode: 'custom' as const, label: '自定义输入' },
              ].map(({ mode, label }) => (
                <button
                  key={mode}
                  type="button"
                  disabled={isLoading || (mode === 'preset' && !presetsLoading && keyPresets.length === 0)}
                  onClick={() => setLlmKeyMode(mode)}
                  className={`px-4 py-2 rounded-full text-sm font-medium transition-all ${
                    llmKeyMode === mode
                      ? 'bg-[#3898ec] text-white shadow-md'
                      : 'bg-[#f3f3f3] text-[#1f1f1f]/70 hover:bg-[#3898ec]/10 hover:text-[#3898ec] disabled:opacity-40 disabled:cursor-not-allowed'
                  }`}
                >
                  {label}
                </button>
              ))}
            </div>
            {llmKeyMode === 'default' && (
              <p className="text-sm text-[#1f1f1f]/55">将使用系统配置中的默认接口地址、模型与环境变量密钥。</p>
            )}
            {llmKeyMode === 'preset' && (
              <div className="space-y-2">
                {presetsLoading ? (
                  <p className="text-sm text-[#1f1f1f]/55">正在加载预设列表…</p>
                ) : keyPresets.length === 0 ? (
                  <p className="text-sm text-amber-700 bg-amber-50 border border-amber-200 rounded-xl px-4 py-3">
                    暂无管理员预设，请联系管理员在「系统配置」中添加，或改用自定义输入。
                  </p>
                ) : (
                  <select
                    value={selectedPresetId}
                    onChange={(e) => setSelectedPresetId(e.target.value)}
                    disabled={isLoading}
                    className="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm outline-none focus:border-[#3898ec] focus:ring-2 focus:ring-[#3898ec]/20"
                  >
                    <option value="">请选择预设</option>
                    {keyPresets.map((preset) => (
                      <option key={preset.id} value={preset.id}>
                        {preset.label}
                        {preset.model ? ` · ${preset.model}` : ''}
                        {preset.maskedKey ? ` (${preset.maskedKey})` : ''}
                      </option>
                    ))}
                  </select>
                )}
              </div>
            )}
            {llmKeyMode === 'custom' && (
              <div className="space-y-3">
                <div className="flex flex-wrap gap-2">
                  {LLM_PROVIDER_TEMPLATES.map((tpl) => (
                    <button
                      key={tpl.id}
                      type="button"
                      onClick={() => {
                        setCustomBaseUrl(tpl.baseUrl);
                        setCustomModel(tpl.model);
                      }}
                      className="px-3 py-1.5 text-sm bg-[#f3f3f3] hover:bg-[#3898ec]/10 hover:text-[#3898ec] rounded-full transition-colors"
                    >
                      {tpl.label}
                    </button>
                  ))}
                </div>
                <Input
                  type="password"
                  value={customApiKey}
                  onChange={(e) => setCustomApiKey(e.target.value)}
                  placeholder="输入 LLM API Key"
                  className="border-gray-200 focus-visible:ring-[#3898ec]/20"
                  disabled={isLoading}
                  autoComplete="off"
                />
                <Input
                  value={customBaseUrl}
                  onChange={(e) => setCustomBaseUrl(e.target.value)}
                  placeholder="接口 Base URL（如 https://api.openai.com/v1）"
                  className="border-gray-200 focus-visible:ring-[#3898ec]/20 font-mono text-sm"
                  disabled={isLoading}
                />
                <Input
                  value={customModel}
                  onChange={(e) => setCustomModel(e.target.value)}
                  placeholder="模型名称（如 gpt-4o-mini、deepseek-chat）"
                  className="border-gray-200 focus-visible:ring-[#3898ec]/20 font-mono text-sm"
                  disabled={isLoading}
                />
                <p className="text-xs text-[#1f1f1f]/45">
                  自定义配置仅保存在当前浏览器会话，不会写入服务器数据库。
                </p>
              </div>
            )}
          </div>

          {canCustomizeRole && (
            <div className="bg-white rounded-2xl shadow-lg p-6 mb-8">
              <div className="flex items-center gap-2 mb-3">
                <UserCog className="w-5 h-5 text-[#3898ec]" />
                <span className="text-sm font-medium text-[#1f1f1f]">演示角色（可选）</span>
              </div>
              <p className="text-sm text-[#1f1f1f]/55 mb-4">
                留空时 AI 会根据主题或文档内容自动选择合适身份（如学术汇报、技术培训、产品发布等）。
              </p>
              <Input
                value={presenterRole}
                onChange={(e) => setPresenterRole(e.target.value.slice(0, 200))}
                placeholder="例如：医学科普讲师、企业内部培训导师、论文答辩汇报人…"
                className="border-gray-200 focus-visible:ring-[#3898ec]/20"
                disabled={isLoading}
              />
              <div className="mt-3 flex flex-wrap gap-2">
                {exampleRoles.map((role) => (
                  <button
                    key={role}
                    type="button"
                    onClick={() => setPresenterRole(role)}
                    className="px-3 py-1.5 text-sm bg-[#f3f3f3] hover:bg-[#3898ec]/10 hover:text-[#3898ec] rounded-full transition-colors"
                  >
                    {role}
                  </button>
                ))}
              </div>
              <p className="mt-2 text-xs text-[#1f1f1f]/45">{presenterRole.length} / 200 字</p>
            </div>
          )}

          {/* Input Area */}
          <div className="bg-white rounded-2xl shadow-lg p-6 lg:p-8 mb-8">
            {inputType === 'topic' ? (
              <div>
                <label className="block text-sm font-medium text-[#1f1f1f] mb-3">
                  输入 PPT 主题
                </label>
                <Textarea
                  value={topic}
                  onChange={(e) => setTopic(e.target.value)}
                  placeholder="请输入您想要制作的 PPT 主题，例如：人工智能在教育领域的应用..."
                  className="min-h-[160px] resize-none border-gray-200 focus:border-[#3898ec] focus:ring-[#3898ec]/20 text-base"
                />
                <div className="mt-4 text-sm text-[#1f1f1f]/50">
                  {topic.length} / 500 字
                </div>

                <div className="mt-6">
                  <p className="text-sm text-[#1f1f1f]/60 mb-3">推荐主题：</p>
                  <div className="flex flex-wrap gap-2">
                    {exampleTopics.map((example, index) => (
                      <button
                        type="button"
                        key={index}
                        onClick={() => setTopic(example)}
                        className="px-4 py-2 bg-[#f3f3f3] hover:bg-[#3898ec]/10 text-[#1f1f1f]/70 hover:text-[#3898ec] rounded-full text-sm transition-all duration-300"
                      >
                        {example}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            ) : (
              <div>
                <label className="block text-sm font-medium text-[#1f1f1f] mb-3">
                  上传文档
                </label>

                {!uploadedFile ? (
                  <div
                    role="button"
                    tabIndex={0}
                    onClick={() => fileInputRef.current?.click()}
                    onKeyDown={(ev) => ev.key === 'Enter' && fileInputRef.current?.click()}
                    className="border-2 border-dashed border-gray-200 hover:border-[#3898ec] rounded-xl p-12 text-center cursor-pointer transition-all duration-300 hover:bg-[#3898ec]/5"
                  >
                    <div className="w-16 h-16 bg-[#3898ec]/10 rounded-full flex items-center justify-center mx-auto mb-4">
                      <FileUp className="w-8 h-8 text-[#3898ec]" />
                    </div>
                    <p className="text-[#1f1f1f] font-medium mb-2">
                      点击或拖拽上传文档
                    </p>
                    <p className="text-sm text-[#1f1f1f]/50">
                      PDF / Word / TXT；PDF、DOCX 推荐直接上传由服务端解析
                    </p>
                    <input
                      ref={fileInputRef}
                      type="file"
                      accept=".pdf,.doc,.docx,.txt"
                      onChange={handleFileUpload}
                      className="hidden"
                    />
                  </div>
                ) : (
                  <div className="border border-gray-200 rounded-xl p-6">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-4">
                        <div className="w-12 h-12 bg-[#3898ec]/10 rounded-lg flex items-center justify-center">
                          <FileText className="w-6 h-6 text-[#3898ec]" />
                        </div>
                        <div>
                          <p className="font-medium text-[#1f1f1f]">{uploadedFile.name}</p>
                          <p className="text-sm text-[#1f1f1f]/50">
                            {(uploadedFile.size / 1024 / 1024).toFixed(2)} MB
                          </p>
                        </div>
                      </div>
                      <button
                        type="button"
                        onClick={() => setUploadedFile(null)}
                        className="p-2 hover:bg-gray-100 rounded-lg transition-colors"
                      >
                        <X className="w-5 h-5 text-[#1f1f1f]/50" />
                      </button>
                    </div>
                    {uploadedFile.name.toLowerCase().endsWith('.pdf') && (
                      <p className="mt-4 text-sm text-[#1f1f1f]/60">
                        已选择 PDF，将在服务端使用 PDFBox 抽取正文并索引。
                      </p>
                    )}
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Submit Button */}
          {statusMessage && (
            <div className="mb-4 rounded-2xl border border-[#3898ec]/20 bg-[#3898ec]/10 p-4 text-center text-sm text-[#1f1f1f]">
              {statusMessage}
            </div>
          )}
          <div className="flex justify-center">
            <Button
              size="lg"
              disabled={!isValid || isLoading}
              onClick={handleSubmit}
              className="bg-[#3898ec] hover:bg-[#0082f3] text-white px-10 py-6 text-base font-semibold rounded-xl shadow-lg shadow-[#3898ec]/30 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isLoading ? (
                <>
                  <Loader2 className="w-5 h-5 mr-2 animate-spin" />
                  AI 正在全力工作...
                </>
              ) : (
                <>
                  <Sparkles className="w-5 h-5 mr-2" />
                  生成大纲
                  <ArrowRight className="w-5 h-5 ml-2" />
                </>
              )}
            </Button>
          </div>

          <div className="mt-10 text-center">
            <p className="text-sm text-[#1f1f1f]/50">
              💡 提示：主题描述越详细，生成的大纲质量越高
            </p>
          </div>
        </div>
      </div>
    </section>
  );
};

export default InputSection;
