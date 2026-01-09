-- ==========================================
-- 1. 사용자 테이블 (기존)
-- ==========================================
CREATE TABLE IF NOT EXISTS portal_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(200) NOT NULL,
    roles VARCHAR(200) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ==========================================
-- 2. AI 문서 메인 (신규 추가)
-- ==========================================
CREATE TABLE IF NOT EXISTS ai_document (
    id BIGSERIAL PRIMARY KEY,
    document_type VARCHAR(30) NOT NULL,
    source_file_name VARCHAR(255),
    file_path TEXT,  -- 파일 저장 경로 (신규 추가)
    analysis_json JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ==========================================
-- 3. AI 문서 필드 상세 (신규 추가)
-- ==========================================
CREATE TABLE IF NOT EXISTS ai_document_field (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    field_key VARCHAR(80) NOT NULL,
    field_value TEXT,
    confidence DOUBLE PRECISION,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 인덱스 설정 (검색 성능 향상)
CREATE INDEX IF NOT EXISTS idx_ai_doc_field_doc_id ON ai_document_field (document_id);
CREATE INDEX IF NOT EXISTS idx_ai_doc_field_key ON ai_document_field (field_key);

-- ==========================================
-- 4. AI 문서 이력 (신규 추가)
-- ==========================================
CREATE TABLE IF NOT EXISTS ai_document_history (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    snapshot_json JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 인덱스 설정
CREATE INDEX IF NOT EXISTS idx_ai_doc_hist_doc_id ON ai_document_history (document_id);

CREATE TABLE ai_learning_log (
    id BIGSERIAL PRIMARY KEY,           -- Auto Increment ID
    category VARCHAR(255) NOT NULL,     -- 학습 유형
    target_name VARCHAR(255),           -- 대상 명칭 (봇 이름 등)
    content_summary TEXT,               -- 내용 요약 (긴 텍스트)
    status VARCHAR(50),                 -- 성공/실패 상태
    performed_by VARCHAR(255),          -- 수행자
    learned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 생성 일시
);

-- (선택 사항) 조회 속도 향상을 위한 인덱스
CREATE INDEX idx_ai_log_learned_at ON ai_learning_log(learned_at DESC);

