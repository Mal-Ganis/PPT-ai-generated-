export type LlmApiKeyMode = 'default' | 'preset' | 'custom';



export interface LlmApiKeySelection {

  mode: LlmApiKeyMode;

  presetId?: string;

  override?: string;

  baseUrlOverride?: string;

  modelOverride?: string;

}



export interface LlmRequestPayload {

  llmApiKeyPresetId?: string;

  llmApiKeyOverride?: string;

  llmBaseUrlOverride?: string;

  llmModelOverride?: string;

}



/** 自定义输入时快速填充常见服务商 */

export const LLM_PROVIDER_TEMPLATES = [

  {

    id: 'deepseek',

    label: 'DeepSeek',

    baseUrl: 'https://api.deepseek.com',

    model: 'deepseek-chat',

  },

  {

    id: 'openai',

    label: 'OpenAI',

    baseUrl: 'https://api.openai.com/v1',

    model: 'gpt-4o-mini',

  },

] as const;



export function buildLlmApiKeyRequest(selection: LlmApiKeySelection): LlmRequestPayload {

  if (selection.mode === 'preset' && selection.presetId) {

    return { llmApiKeyPresetId: selection.presetId };

  }

  if (selection.mode === 'custom' && selection.override?.trim()) {

    return {

      llmApiKeyOverride: selection.override.trim(),

      ...(selection.baseUrlOverride?.trim()

        ? { llmBaseUrlOverride: selection.baseUrlOverride.trim() }

        : {}),

      ...(selection.modelOverride?.trim()

        ? { llmModelOverride: selection.modelOverride.trim() }

        : {}),

    };

  }

  return {};

}



export function selectionFromStored(

  presetId?: string | null,

  override?: string,

  baseUrlOverride?: string,

  modelOverride?: string,

): LlmApiKeySelection {

  if (override?.trim()) {

    return {

      mode: 'custom',

      override: override.trim(),

      baseUrlOverride: baseUrlOverride?.trim() || undefined,

      modelOverride: modelOverride?.trim() || undefined,

    };

  }

  if (presetId) {

    return { mode: 'preset', presetId };

  }

  return { mode: 'default' };

}



export function appendLlmApiKeyToFormData(

  formData: FormData,

  selection: LlmApiKeySelection,

): void {

  const payload = buildLlmApiKeyRequest(selection);

  if (payload.llmApiKeyPresetId) {

    formData.append('llmApiKeyPresetId', payload.llmApiKeyPresetId);

  }

  if (payload.llmApiKeyOverride) {

    formData.append('llmApiKeyOverride', payload.llmApiKeyOverride);

  }

  if (payload.llmBaseUrlOverride) {

    formData.append('llmBaseUrlOverride', payload.llmBaseUrlOverride);

  }

  if (payload.llmModelOverride) {

    formData.append('llmModelOverride', payload.llmModelOverride);

  }

}

