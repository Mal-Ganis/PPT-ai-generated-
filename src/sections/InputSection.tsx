import { useState, useRef, useEffect } from 'react';
import { Type, FileUp, ArrowRight, Sparkles, Loader2, X, FileText, Clock } from 'lucide-react';
import {
  PRESENTATION_DURATION_OPTIONS,
  DEFAULT_PRESENTATION_DURATION_MINUTES,
} from '@/lib/backend';
import { FlowExitNav } from '@/components/FlowExitNav';
import { WorkflowStepActions } from '@/components/WorkflowStepActions';
import type { WorkflowProgress, WorkflowStep } from '@/lib/workflowSteps';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import * as mammoth from 'mammoth';

interface InputSectionProps {
  onSubmit: (
    type: 'topic' | 'document',
    content: string,
    meta?: { fileName?: string; formData?: FormData; presentationDurationMinutes?: number },
  ) => Promise<void>;
  workflowProgress: WorkflowProgress;
  onGoToStep: (step: WorkflowStep) => void;
  initialTopic?: string;
  initialInputType?: 'topic' | 'document';
  initialPresentationMinutes?: number;
}

const InputSection = ({
  onSubmit,
  workflowProgress,
  onGoToStep,
  initialTopic,
  initialInputType,
  initialPresentationMinutes,
}: InputSectionProps) => {
  const [inputType, setInputType] = useState<'topic' | 'document'>('topic');
  const [topic, setTopic] = useState('');
  const [uploadedFile, setUploadedFile] = useState<File | null>(null);
  const [documentContent, setDocumentContent] = useState<string>('');
  const [isLoading, setIsLoading] = useState(false);
  const [statusMessage, setStatusMessage] = useState('');
  const [presentationMinutes, setPresentationMinutes] = useState<number>(
    DEFAULT_PRESENTATION_DURATION_MINUTES,
  );
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (initialTopic != null) setTopic(initialTopic);
    if (initialInputType) setInputType(initialInputType);
    if (initialPresentationMinutes != null) setPresentationMinutes(initialPresentationMinutes);
  }, [initialTopic, initialInputType, initialPresentationMinutes]);

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
      const durationMeta = { presentationDurationMinutes: presentationMinutes };
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
    inputType === 'topic'
      ? topic.trim().length > 0
      : uploadedFile !== null || documentContent.trim().length > 0;

  const exampleTopics = [
    '人工智能在教育领域的应用',
    '新能源汽车市场分析报告',
    '数字化转型战略规划',
    '产品发布会演示方案',
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
