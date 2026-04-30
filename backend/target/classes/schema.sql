CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS index_segments (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT,
    segment_id VARCHAR(255) NOT NULL,
    content TEXT,
    metadata JSONB DEFAULT '{}'::jsonb,
    embedding VECTOR(1536) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_index_segments_embedding ON index_segments USING ivfflat (embedding) WITH (lists = 100);

CREATE TABLE IF NOT EXISTS system_config (
    id BIGSERIAL PRIMARY KEY,
    llm_model VARCHAR(255) NOT NULL,
    temperature DOUBLE PRECISION NOT NULL,
    max_tokens INTEGER NOT NULL,
    top_p DOUBLE PRECISION NOT NULL,
    top_k INTEGER NOT NULL,
    retrieval_limit INTEGER NOT NULL,
    outline_prompt_template TEXT,
    slide_prompt_template TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS evaluation_reports (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    page_id BIGINT,
    outline_logic_score INTEGER NOT NULL,
    factual_accuracy_score INTEGER NOT NULL,
    info_density_score INTEGER NOT NULL,
    language_expression_score INTEGER NOT NULL,
    total_score DOUBLE PRECISION NOT NULL,
    recommendations TEXT,
    user_feedback TEXT,
    evaluation_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
