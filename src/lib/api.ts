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

export const generateOutline = async (request: GenerateOutlineRequest): Promise<GenerateOutlineResponse> => {
  const prompt = request.type === 'topic'
    ? `请根据以下主题生成一个专业的PPT大纲。主题：${request.content}

请以JSON格式返回，格式如下：
{
  "title": "大纲标题",
  "slides": [
    {
      "id": 1,
      "title": "幻灯片标题",
      "content": ["要点1", "要点2"],
      "notes": "演讲者备注"
    }
  ]
}

大纲应该包括封面、目录、主要内容章节和总结，至少5-8页。`
    : `请根据以下文档内容生成一个专业的PPT大纲。文档内容：${request.content}

请以JSON格式返回，格式如下：
{
  "title": "大纲标题",
  "slides": [
    {
      "id": 1,
      "title": "幻灯片标题",
      "content": ["要点1", "要点2"],
      "notes": "演讲者备注"
    }
  ]
}

大纲应该包括封面、目录、主要内容章节和总结，至少5-8页。`;

  const response = await api.post('/chat/completions', {
    model: 'deepseek-reasoner', // 使用DeepSeek Reasoner
    messages: [
      {
        role: 'user',
        content: prompt,
      },
    ],
    temperature: 0.7,
  });

  const content = response.data.choices[0].message.content;
  try {
    return JSON.parse(content);
  } catch (error) {
    throw new Error(`Failed to parse API response: ${String(error)}`);
  }
};

export const generateSlideContent = async (request: GenerateSlideContentRequest): Promise<GenerateSlideContentResponse> => {
  const prompt = `请为PPT幻灯片生成详细内容。

幻灯片标题：${request.slideTitle}
原始输入类型：${request.inputType === 'topic' ? '主题' : '文档'}
原始输入内容：${request.inputContent}

请生成：
1. 3-5个主要内容要点（数组）
2. 演讲者备注（字符串）

请以JSON格式返回：
{
  "content": ["要点1", "要点2", "要点3"],
  "notes": "演讲者备注内容"
}`;

  const response = await api.post('/chat/completions', {
    model: 'deepseek-reasoner',
    messages: [
      {
        role: 'user',
        content: prompt,
      },
    ],
    temperature: 0.7,
  });

  const content = response.data.choices[0].message.content;
  try {
    return JSON.parse(content);
  } catch (error) {
    throw new Error(`Failed to parse API response: ${String(error)}`);
  }
};