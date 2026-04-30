import axios from 'axios';

export interface EvaluationReport {
  id: number;
  projectId: number;
  pageId?: number;
  outlineLogicScore: number;
  factualAccuracyScore: number;
  infoDensityScore: number;
  languageExpressionScore: number;
  totalScore: number;
  recommendations?: string;
  userFeedback?: string;
  evaluationTime: string;
}

const backendApi = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 30000,
});

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

export interface ProjectOutlineResponse {
  projectId: number;
  title: string;
  slides: {
    id: number;
    title: string;
    content: string[];
    notes: string;
  }[];
}

export const fetchEvaluationReports = async (projectId: number): Promise<EvaluationReport[]> => {
  const response = await backendApi.get<EvaluationReport[]>(`/api/projects/${projectId}/evaluations`);
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

export const createProjectFromTopic = async (
  topic: string,
): Promise<ProjectOutlineResponse> => {
  const response = await backendApi.post<ProjectOutlineResponse>('/api/projects/topic', {
    topic,
  });
  return response.data;
};

export const searchExternalSources = async (
  query: string,
  limit = 3,
): Promise<ExternalSourceDocument[]> => {
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
