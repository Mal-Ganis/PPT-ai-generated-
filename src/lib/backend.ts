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
  /** 目标演讲时长（分钟），5–60 */
  presentationDurationMinutes?: number;
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
  /** 讲稿要点 */
  bullets?: string[];
  /** 适合投影的短要点 */
  pptBullets?: string[];
  sources?: string[];
  notes?: string;
}

export interface ProjectDetailResponse {
  id: number;
  title: string;
  theme: string;
  presentationDurationMinutes?: number;
  createdAt: string;
  updatedAt: string;
  slides: ProjectDetailSlide[];
  evaluations: EvaluationReport[];
}

export const PRESENTATION_DURATION_OPTIONS = [5, 10, 15, 20, 30] as const;
export const DEFAULT_PRESENTATION_DURATION_MINUTES = 15;

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

export interface FetchProjectOptions {
  /** 默认 false，减轻正文生成完成后的拉取体积与写回时间 */
  includeEvaluations?: boolean;
}

export const fetchProject = async (
  projectId: number,
  options?: FetchProjectOptions,
): Promise<ProjectDetailResponse> => {
  const includeEvaluations = options?.includeEvaluations ?? false;
  const response = await backendApi.get<ProjectDetailResponse>(`/api/projects/${projectId}`, {
    params: { includeEvaluations },
  });
  return response.data;
};

const FETCH_PROJECT_RETRY_ATTEMPTS = 3;
const FETCH_PROJECT_RETRY_DELAY_MS = 800;

/** 生成完成后拉取项目：跳过评估列表，失败时短暂重试 */
export const fetchProjectForSlides = async (projectId: number): Promise<ProjectDetailResponse> => {
  let lastErr: unknown;
  for (let i = 0; i < FETCH_PROJECT_RETRY_ATTEMPTS; i++) {
    try {
      return await fetchProject(projectId, { includeEvaluations: false });
    } catch (e) {
      lastErr = e;
      if (i < FETCH_PROJECT_RETRY_ATTEMPTS - 1) {
        await sleep(FETCH_PROJECT_RETRY_DELAY_MS);
      }
    }
  }
  throw lastErr;
};

export const createProjectFromTopic = async (
  topic: string,
  presentationDurationMinutes: number = DEFAULT_PRESENTATION_DURATION_MINUTES,
): Promise<ProjectOutlineResponse> => {
  const response = await backendApi.post<ProjectOutlineResponse>('/api/projects/topic', {
    topic,
    presentationDurationMinutes,
  });
  return response.data;
};

export const createProjectFromDocument = async (
  title: string,
  text: string,
  presentationDurationMinutes: number = DEFAULT_PRESENTATION_DURATION_MINUTES,
): Promise<ProjectOutlineResponse> => {
  const response = await backendApi.post<ProjectOutlineResponse>('/api/projects/document', {
    title,
    text,
    presentationDurationMinutes,
  });
  return response.data;
};

/** PDF/DOCX/TXT 服务端解析后生成项目（multipart/form-data） */
export const uploadDocumentFile = async (
  formData: FormData,
  presentationDurationMinutes: number = DEFAULT_PRESENTATION_DURATION_MINUTES,
): Promise<ProjectOutlineResponse> => {
  formData.append('presentationDurationMinutes', String(presentationDurationMinutes));
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
  pptBullets?: string[];
  sources?: string[];
  notes?: string;
}

export type PptDisplayExtractionPhase = 'IDLE' | 'RUNNING' | 'COMPLETED' | 'FAILED';

export interface PptDisplayExtractionStatus {
  projectId: number;
  phase: PptDisplayExtractionPhase;
  totalSlides: number;
  completedSlides: number;
  message: string;
}

const PPT_EXTRACT_POLL_INTERVAL_MS = 2000;
const PPT_EXTRACT_POLL_MAX_ATTEMPTS = 300;

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

export type SlideGenerationPhase = 'IDLE' | 'RUNNING' | 'COMPLETED' | 'FAILED';

export interface SlideGenerationStatus {
  projectId: number;
  phase: SlideGenerationPhase;
  totalSlides: number;
  completedSlides: number;
  message: string;
}

const SLIDE_GENERATION_POLL_INTERVAL_MS = 3000;
const SLIDE_GENERATION_POLL_MAX_ATTEMPTS = 600;

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

export const startSlideGeneration = async (
  projectId: number,
  payload: GenerateSlidesPayload,
): Promise<SlideGenerationStatus> => {
  const response = await backendApi.post<SlideGenerationStatus>(
    `/api/projects/${projectId}/slides/generate`,
    payload,
  );
  return response.data;
};

export const fetchSlideGenerationStatus = async (projectId: number): Promise<SlideGenerationStatus> => {
  const response = await backendApi.get<SlideGenerationStatus>(
    `/api/projects/${projectId}/slides/generate/status`,
  );
  return response.data;
};

function countSlidesWithBullets(detail: ProjectDetailResponse): number {
  return detail.slides.filter((s) => (s.bullets?.length ?? 0) > 0).length;
}

/** 任务失败或超时时，若库中已有正文则仍进入编辑流程 */
async function tryRecoverGeneratedProject(
  projectId: number,
  expectedTotal: number,
): Promise<ProjectDetailResponse | null> {
  const detail = await fetchProjectForSlides(projectId);
  const ready = countSlidesWithBullets(detail);
  if (ready === 0) {
    return null;
  }
  if (expectedTotal <= 0 || ready >= expectedTotal) {
    return detail;
  }
  return null;
}

/**
 * 异步正文生成：POST 立即返回，轮询 status 直至完成，再拉取项目详情。
 */
export const generateSlideContents = async (
  projectId: number,
  payload: GenerateSlidesPayload,
  onProgress?: (status: SlideGenerationStatus) => void,
): Promise<ProjectDetailResponse> => {
  let status = await startSlideGeneration(projectId, payload);
  onProgress?.(status);

  if (status.phase === 'COMPLETED') {
    return fetchProjectForSlides(projectId);
  }

  for (let attempt = 0; attempt < SLIDE_GENERATION_POLL_MAX_ATTEMPTS; attempt++) {
    if (status.phase === 'COMPLETED') {
      return fetchProjectForSlides(projectId);
    }
    if (status.phase === 'FAILED') {
      const recovered =
        (await tryRecoverGeneratedProject(projectId, status.totalSlides)) ??
        (await tryRecoverGeneratedProject(projectId, 0));
      if (recovered) {
        return recovered;
      }
      throw new Error(status.message || '正文生成失败');
    }
    await sleep(SLIDE_GENERATION_POLL_INTERVAL_MS);
    status = await fetchSlideGenerationStatus(projectId);
    onProgress?.(status);
  }

  const recovered = await tryRecoverGeneratedProject(projectId, status.totalSlides);
  if (recovered) {
    return recovered;
  }
  throw new Error('正文生成超时，请稍后在「我的项目」中打开该项目查看是否已生成。');
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

/** 将各页讲稿与 PPT 要点写入后端 */
export const saveProjectSlides = async (
  projectId: number,
  slides: Array<{ slideId: number; bullets: string[]; pptBullets?: string[] }>,
): Promise<void> => {
  for (const s of slides) {
    await updateProjectSlide(projectId, s.slideId, {
      bullets: s.bullets,
      ...(s.pptBullets != null ? { pptBullets: s.pptBullets } : {}),
    });
  }
};

export const startPptDisplayExtraction = async (
  projectId: number,
  force = false,
): Promise<PptDisplayExtractionStatus> => {
  const response = await backendApi.post<PptDisplayExtractionStatus>(
    `/api/projects/${projectId}/slides/extract-ppt-display`,
    null,
    { params: { force } },
  );
  return response.data;
};

export const fetchPptDisplayExtractionStatus = async (
  projectId: number,
): Promise<PptDisplayExtractionStatus> => {
  const response = await backendApi.get<PptDisplayExtractionStatus>(
    `/api/projects/${projectId}/slides/extract-ppt-display/status`,
  );
  return response.data;
};

export const extractPptDisplayForSlide = async (
  projectId: number,
  slideId: number,
): Promise<string[]> => {
  const response = await backendApi.post<string[]>(
    `/api/projects/${projectId}/slides/${slideId}/extract-ppt-display`,
    {},
    { timeout: SLIDE_PIPELINE_AXIOS_TIMEOUT_MS },
  );
  return response.data;
};

/**
 * 异步提炼 PPT 投影文案，完成后返回最新项目详情。
 */
export const extractPptDisplayContents = async (
  projectId: number,
  options?: { force?: boolean; onProgress?: (status: PptDisplayExtractionStatus) => void },
): Promise<ProjectDetailResponse> => {
  let status = await startPptDisplayExtraction(projectId, options?.force ?? false);
  options?.onProgress?.(status);

  if (status.phase === 'COMPLETED') {
    return fetchProjectForSlides(projectId);
  }

  for (let attempt = 0; attempt < PPT_EXTRACT_POLL_MAX_ATTEMPTS; attempt++) {
    if (status.phase === 'COMPLETED') {
      return fetchProjectForSlides(projectId);
    }
    if (status.phase === 'FAILED') {
      throw new Error(status.message || 'PPT 文案提炼失败');
    }
    await sleep(PPT_EXTRACT_POLL_INTERVAL_MS);
    status = await fetchPptDisplayExtractionStatus(projectId);
    options?.onProgress?.(status);
  }
  throw new Error('PPT 文案提炼超时，请稍后刷新或单页重新提炼。');
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
