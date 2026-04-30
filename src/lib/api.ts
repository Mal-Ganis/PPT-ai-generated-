import axios from 'axios';

const DEEPSEEK_API_KEY = 'sk-f1da75d5e90945daa3de76ad9791c8a4';
const DEEPSEEK_BASE_URL = 'https://api.deepseek.com';

const api = axios.create({
  baseURL: DEEPSEEK_BASE_URL,
  timeout: 30000,
  headers: {
    'Authorization': `Bearer ${DEEPSEEK_API_KEY}`,
    'Content-Type': 'application/json',
  },
});

export interface SystemConfig {
  llmModel: string;
  temperature: number;
  maxTokens: number;
  topP: number;
  topK: number;
  retrievalLimit: number;
  outlinePromptTemplate: string;
  slidePromptTemplate: string;
}

const defaultSystemConfig: SystemConfig = {
  llmModel: 'deepseek-reasoner',
  temperature: 0.7,
  maxTokens: 1024,
  topP: 0.95,
  topK: 1,
  retrievalLimit: 5,
  outlinePromptTemplate: `请根据以下主题生成一个专业的PPT大纲。主题：{content}\n\n请以JSON格式返回，格式如下：{\n  \"title\": \"大纲标题\",\n  \"slides\": [\n    {\n      \"id\": 1,\n      \"title\": \"幻灯片标题\",\n      \"content\": [\"要点1\", \"要点2\"],\n      \"notes\": \"演讲者备注\"\n    }\n  ]\n}\n\n大纲应该包括封面、目录、主要内容章节和总结，至少5-8页。`,
  slidePromptTemplate: `请为PPT幻灯片生成详细内容。\n\n幻灯片标题：{slideTitle}\n原始输入类型：{inputType}\n原始输入内容：{inputContent}\n\n请生成：\n1. 3-5个主要内容要点（数组）\n2. 演讲者备注（字符串）\n\n请以JSON格式返回：{\n  \"content\": [\"要点1\", \"要点2\", \"要点3\"],\n  \"notes\": \"演讲者备注内容\"\n}`,
};

export interface GenerateOutlineRequest {
  type: 'topic' | 'document';
  content: string;
}

export interface GenerateOutlineResponse {
  title: string;
  slides: {
    id: number;
    title: string;
    content: string[];
    notes: string;
  }[];
}

export interface GenerateSlideContentRequest {
  slideTitle: string;
  inputType: 'topic' | 'document';
  inputContent: string;
}

export interface GenerateSlideContentResponse {
  content: string[];
  notes: string;
}

function formatPrompt(template: string, variables: Record<string, string>) {
  return Object.entries(variables).reduce(
    (result, [key, value]) => result.replaceAll(`{${key}}`, value),
    template,
  );
}

export const generateOutline = async (
  request: GenerateOutlineRequest,
  config: SystemConfig = defaultSystemConfig,
): Promise<GenerateOutlineResponse> => {
  const prompt = formatPrompt(config.outlinePromptTemplate, {
    content: request.content,
  });

  const response = await api.post('/chat/completions', {
    model: config.llmModel,
    messages: [
      {
        role: 'user',
        content: prompt,
      },
    ],
    temperature: config.temperature,
    max_tokens: config.maxTokens,
    top_p: config.topP,
    top_k: config.topK,
  });

  const content = response.data.choices[0].message.content;
  try {
    return JSON.parse(content);
  } catch (error) {
    throw new Error(`Failed to parse API response: ${String(error)}`);
  }
};

export const generateSlideContent = async (
  request: GenerateSlideContentRequest,
  config: SystemConfig = defaultSystemConfig,
): Promise<GenerateSlideContentResponse> => {
  const prompt = formatPrompt(config.slidePromptTemplate, {
    slideTitle: request.slideTitle,
    inputType: request.inputType === 'topic' ? '主题' : '文档',
    inputContent: request.inputContent,
  });

  const response = await api.post('/chat/completions', {
    model: config.llmModel,
    messages: [
      {
        role: 'user',
        content: prompt,
      },
    ],
    temperature: config.temperature,
    max_tokens: config.maxTokens,
    top_p: config.topP,
    top_k: config.topK,
  });

  const content = response.data.choices[0].message.content;
  try {
    return JSON.parse(content);
  } catch (error) {
    throw new Error(`Failed to parse API response: ${String(error)}`);
  }
};