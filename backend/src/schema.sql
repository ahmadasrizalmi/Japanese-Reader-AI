-- Komorebi Reader Database Schema for Cloudflare D1 and SQLite

CREATE TABLE IF NOT EXISTS articles (
    id TEXT PRIMARY KEY NOT NULL,
    user_id TEXT NOT NULL,
    title TEXT NOT NULL,
    category TEXT NOT NULL,
    raw_text TEXT NOT NULL,
    difficulty_level TEXT NOT NULL,
    kanji_ratio REAL NOT NULL DEFAULT 0.0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS sentences (
    id TEXT PRIMARY KEY NOT NULL,
    article_id TEXT NOT NULL,
    original_text TEXT NOT NULL,
    translated_text TEXT NOT NULL,
    furigana_payload TEXT NOT NULL,
    grammar_analysis TEXT NOT NULL,
    sequence_order INTEGER NOT NULL,
    inspection_count INTEGER NOT NULL DEFAULT 0,
    audio_play_count INTEGER NOT NULL DEFAULT 0,
    needs_deep_study INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (article_id) REFERENCES articles(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS vocabularies (
    id TEXT PRIMARY KEY NOT NULL,
    user_id TEXT NOT NULL,
    sentence_id TEXT,
    kanji TEXT NOT NULL,
    reading TEXT NOT NULL,
    meaning TEXT NOT NULL,
    part_of_speech TEXT NOT NULL,
    jlpt_level TEXT NOT NULL,
    mastery_status INTEGER NOT NULL DEFAULT 0,
    review_count INTEGER NOT NULL DEFAULT 0,
    next_review_at INTEGER,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (sentence_id) REFERENCES sentences(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS study_logs (
    id TEXT PRIMARY KEY NOT NULL,
    user_id TEXT NOT NULL,
    sentence_id TEXT NOT NULL,
    action_type TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    FOREIGN KEY (sentence_id) REFERENCES sentences(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_settings (
    user_id TEXT PRIMARY KEY NOT NULL,
    target_language TEXT NOT NULL DEFAULT 'id-ID',
    deepseek_tone TEXT NOT NULL DEFAULT 'colloquial',
    tts_speed REAL NOT NULL DEFAULT 1.0,
    voice_model TEXT NOT NULL DEFAULT 'ja_JP-hira-medium',
    furigana_mode TEXT NOT NULL DEFAULT 'always',
    dynamic_pastel_highlights INTEGER NOT NULL DEFAULT 1,
    kanji_font_style TEXT NOT NULL DEFAULT 'mincho',
    deepseek_api_key TEXT,
    updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS audio_cache (
    text_hash TEXT PRIMARY KEY NOT NULL,
    voice_model TEXT NOT NULL,
    speed REAL NOT NULL,
    r2_url TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sentences_article ON sentences(article_id);
CREATE INDEX IF NOT EXISTS idx_vocab_user ON vocabularies(user_id);
CREATE INDEX IF NOT EXISTS idx_study_logs_sentence ON study_logs(sentence_id);
CREATE INDEX IF NOT EXISTS idx_audio_cache_hash ON audio_cache(text_hash);
