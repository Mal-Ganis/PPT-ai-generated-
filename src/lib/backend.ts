import axios from 'axios';
import { toast } from 'sonner';

/**
 * VITE_API_BASE 应为「协议 + 主机 + 端口」，不要带 `/api`。
 * 若误写为 `http://localhost:8080/api`，axios 会与路径 `/api/...` 拼成 `/api/api/...` 导致 404。
 */
function normalizeBackendOrigin(base: string): string {
  let b = base.trim().replace(/\/+$/, '');
  if (b.endsWith('/api')) {
    b = b.slice(0, -4).replace(/\/+$/, '');
  }
  return b || 'http://localhost:8080';
}

/** 一般 API：大纲创建、配置等 */
const DEFAULT_AXIOS_TIMEOUT_MS = 360_000;

/**
 * 多页顺序调用大模型 + 自动评估 + 自评修订（tier1/tier2 可能整批重跑），总时长常达十余分钟。
 * 须大于：页数 ×（单次补全上限 + 重试）× 修订轮次。
 */
const SLIDE_PIPELINE_AXIOS_TIMEOUT_MS = 1_800_000;

/** 从 Spring 返回的 JSON 或纯文本 body 中取出 message */
function extractAxiosErrorMessage(err: unknown): string {
  const ax = err as {
    response?: { status?: number; data?: unknown };
    message?: string;
  };
  const data = ax.response?.data;
  if (typeof data === 'string' && data.trim()) {
    return data.trim();
  }
  if (data && typeof data === 'object' && data !== null && 'message' in data) {
    const m = (data as { message?: unknown }).message;
    if (m != null && String(m).trim()) {
      return String(m).trim();
    }
  }
  return ax.message ?? '请求失败';
}

const backendApi = axios.create({
  baseURL: normalizeBackendOrigin(import.meta.env.VITE_API_BASE ?? 'http://localhost:8080'),
  timeout: DEFAULT_AXIOS_TIMEOUT_MS,
});

backendApi.interceptors.response.use(
  (r) => r,
  (err: unknown) => {
    const ax = err as { response?: { status?: number } };
    const msg = extractAxiosErrorMessage(err);
    if (ax.response) {
      toast.error(msg);
    }
    return Promise.reject(err);
  },
);

export interface EvaluationReport {
  id: number;
  projectId: number;
  pageId?: number;
  outlineLogicScore: number;
  factualAccuracyScore: number;
  infoDensityScore: number;
  languageExpressionScore: number;
  totalScore: number;
  autoOutlineLogicScore?: number;
  autoInfoDensityScore?: number;
  autoFactualAccuracyScore?: number;
  autoLanguageExpressionScore?: number;
  autoSourceCoverageScore?: number;
  autoTotalScore?: number;
  factVerificationRate?: number;
  recommendations?: string;
  userFeedback?: string;
  evaluationTime: string;
}

export interface SystemConfig {
  id?: number;
  llmModel: string;
  temperature: number;
  maxTokens: number;
  topP: number;
  topK: number;
  retrievalLimit: number;
  outlinePromptTemplate: string;
  slidePromptTemplate: string;
}

export interface ExternalSourceDocument {
  title: string;
  source: string;
  author: string;
  publishedAt: string;
  summary: string;
  url: string;
  trustScore: number;
}

export interface OutlineSlideDto {
  slideId?: number;
  id: number;
  chapter?: string;
  title: string;
  content: string[];
  /** 大纲级备注（可选）；正文阶段不再生成幻灯片演讲备注 */
  notes?: string;
}

export interface ProjectOutlineResponse {
  projectId: number;
  title: string;
  slides: OutlineSlideDto[];
}

export interface ProjectSummary {
  id: number;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectDetailSlide {
  id: number;
  position: number;
  chapter?: string | null;
  title: string;
  body?: string;
  bullets?: string[];
  sources?: string[];
  notes?: string;
}

export interface ProjectDetailResponse {
  id: number;
  title: string;
  theme: string;
  createdAt: string;
  updatedAt: string;
  slides: ProjectDetailSlide[];
  evaluations: EvaluationReport[];
}

export interface UpsertOutlinePayload {
  title?: string;
  theme?: string;
  slides: Array<{
    position: number;
    chapter?: string | null;
    title: string;
    body?: string;
    bullets?: string[];
    sources?: string[];
    notes?: string;
  }>;
}

export interface GenerateSlidesPayload {
  inputType?: string;
  inputContent?: string;
}

export interface SlideContentResponse {
  content: string[];
  notes?: string;
  sources?: string[];
}

export interface IndexSearchResult {
  id: number;
  projectId: number;
  segmentId: string;
  content: string;
  metadata?: string;
  distance: number;
}

export interface SearchResponse {
  results: IndexSearchResult[];
}

export interface CreateEvaluationPayload {
  pageId?: number;
  outlineLogicScore: number;
  factualAccuracyScore: number;
  infoDensityScore: number;
  languageExpressionScore: number;
  recommendations?: string;
  userFeedback?: string;
}

export const fetchEvaluationReports = async (projectId: number): Promise<EvaluationReport[]> => {
  const response = await backendApi.get<EvaluationReport[]>(`/api/projects/${projectId}/evaluations`);
  return response.data;
};

export const submitEvaluationReport = async (
  projectId: number,
  payload: CreateEvaluationPayload,
): Promise<number> => {
  const response = await backendApi.post<number>(`/api/projects/${projectId}/evaluations`, payload);
  return response.data;
};

export interface EvaluationCalibrationPayload {
  agreeWithAuto: boolean;
  note?: string;
}

/** 拇指校准：认同则将人工分项对齐最新自动启发式分 */
export const submitEvaluationCalibration = async (
  projectId: number,
  payload: EvaluationCalibrationPayload,
): Promise<number> => {
  const response = await backendApi.post<number>(
    `/api/projects/${projectId}/evaluations/calibrate`,
    payload,
  );
  return response.data;
};

export const fetchSystemConfig = async (): Promise<SystemConfig> => {
  const response = await backendApi.get<SystemConfig>('/api/config');
  return response.data;
};

export const saveSystemConfig = async (config: SystemConfig): Promise<SystemConfig> => {
  const response = await backendApi.put<SystemConfig>('/api/config', config);
  return response.data;
};

/** 将数据库中的配置重置为后端内置默认值（含新版大纲/幻灯片模板），立即持久化 */
export const resetSystemConfigToDefaults = async (): Promise<SystemConfig> => {
  const response = await backendApi.post<SystemConfig>('/api/config/reset-defaults');
  return response.data;
};

export const listProjects = async (): Promise<ProjectSummary[]> => {
  const response = await backendApi.get<ProjectSummary[]>('/api/projects');
  return response.data;
};

export const fetchProject = async (projectId: number): Promise<ProjectDetailResponse> => {
  const response = await backendApi.get<ProjectDetailResponse>(`/api/projects/${projectId}`);
  return response.data;
};

export const createProjectFromTopic = async (topic: string): Promise<ProjectOutlineResponse> => {
  const response = await backendApi.post<ProjectOutlineResponse>('/api/projects/topic', { topic });
  return response.data;
};

export const createProjectFromDocument = async (
  title: string,
  text: string,
): Promise<ProjectOutlineResponse> => {
  const response = await backendApi.post<ProjectOutlineResponse>('/api/projects/document', {
    title,
    text,
  });
  return response.data;
};

/** PDF/DOCX/TXT 服务端解析后生成项目（multipart/form-data） */
export const uploadDocumentFile = async (formData: FormData): Promise<ProjectOutlineResponse> => {
  const response = await backendApi.post<ProjectOutlineResponse>('/api/projects/document/upload', formData, {
    timeout: SLIDE_PIPELINE_AXIOS_TIMEOUT_MS,
  });
  return response.data;
};

export const syncProjectOutline = async (
  projectId: number,
  payload: UpsertOutlinePayload,
): Promise<void> => {
  await backendApi.put(`/api/projects/${projectId}/outline`, payload);
};

/** PATCH 单页：不影响项目中其它幻灯片 */
export interface UpdateSlidePayload {
  title?: string;
  chapter?: string;
  body?: string;
  bullets?: string[];
  sources?: string[];
  notes?: string;
}

export const updateProjectSlide = async (
  projectId: number,
  slideId: number,
  payload: UpdateSlidePayload,
): Promise<ProjectDetailResponse> => {
  const response = await backendApi.patch<ProjectDetailResponse>(
    `/api/projects/${projectId}/slides/${slideId}`,
    payload,
  );
  return response.data;
};

export const generateSlideContents = async (
  projectId: number,
  payload: GenerateSlidesPayload,
): Promise<ProjectDetailResponse> => {
  const response = await backendApi.post<ProjectDetailResponse>(
    `/api/projects/${projectId}/slides/generate`,
    payload,
    { timeout: SLIDE_PIPELINE_AXIOS_TIMEOUT_MS },
  );
  return response.data;
};

export const regenerateSlide = async (
  projectId: number,
  slideId: number,
  payload: GenerateSlidesPayload,
): Promise<SlideContentResponse> => {
  const response = await backendApi.post<SlideContentResponse>(
    `/api/projects/${projectId}/slides/${slideId}/regenerate`,
    payload,
    { timeout: SLIDE_PIPELINE_AXIOS_TIMEOUT_MS },
  );
  return response.data;
};

export const searchExternalSources = async (query: string, limit = 3): Promise<ExternalSourceDocument[]> => {
  const response = await backendApi.get<ExternalSourceDocument[]>('/api/external-sources/search', {
    params: { query, limit },
  });
  return response.data;
};

export const loadExternalSources = async (
  query: string,
  projectId: number,
  limit = 3,
): Promise<number> => {
  const response = await backendApi.post<{ loadedCount: number }>('/api/external-sources/load', {
    query,
    limit,
    projectId,
  });
  return response.data.loadedCount;
};

export const searchIndexByText = async (
  query: string,
  projectId?: number,
  topK = 5,
): Promise<SearchResponse> => {
  const response = await backendApi.post<SearchResponse>('/api/index/search-text', {
    query,
    projectId: projectId ?? null,
    topK,
  });
  return response.data;
};
