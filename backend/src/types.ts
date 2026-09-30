export interface Article {
  id: string;
  user_id: string;
  title: string;
  category: string;
  raw_text: string;
  difficulty_level: string; // 'N5' | 'N4' | 'N3' | 'N2' | 'N1'
  kanji_ratio: number;
  created_at: number;
  updated_at: number;
  sentences?: Sentence[];
}

export interface Token {
  surface: string;
  reading: string;
  romaji: string;
  pos: string;
  meaning?: string;
  base_form?: string;
  form?: string;
  jlpt?: string;
}

export interface GrammarPoint {
  pattern: string;
  explanation: string;
}

export interface Sentence {
  id: string;
  article_id: string;
  original_text: string;
  translated_text: string;
  furigana_payload: string; // JSON string of Token[]
  grammar_analysis: string; // JSON string of GrammarPoint[]
  sequence_order: number;
  inspection_count: number;
  audio_play_count: number;
  needs_deep_study: number; // 0 or 1
  updated_at: number;
}

export interface Vocabulary {
  id: string;
  user_id: string;
  sentence_id?: string | null;
  kanji: string;
  reading: string;
  meaning: string;
  part_of_speech: string;
  jlpt_level: string;
  mastery_status: number; // 0: Baru, 1: Dipelajari, 2: Dikuasai
  review_count: number;
  next_review_at?: number | null;
  created_at: number;
  updated_at: number;
}

export interface StudyLog {
  id: string;
  user_id: string;
  sentence_id: string;
  action_type: 'INSPECT' | 'AUDIO_PLAY' | 'TRANSLATE_SELECT';
  timestamp: number;
}

export interface UserSettings {
  user_id: string;
  target_language: string;
  deepseek_tone: 'colloquial' | 'formal' | 'literal';
  tts_speed: number;
  voice_model: string;
  furigana_mode: 'always' | 'tap' | 'off';
  dynamic_pastel_highlights: number; // 1 or 0
  kanji_font_style: 'mincho' | 'gothic';
  deepseek_api_key?: string | null;
  updated_at: number;
}

export interface AudioCache {
  text_hash: string;
  voice_model: string;
  speed: number;
  r2_url: string;
  created_at: number;
}

export interface AnalyzeRequest {
  text: string;
  api_key?: string;
  tone?: 'colloquial' | 'formal' | 'literal';
  target_language?: string;
}

export interface SentenceAnalysis {
  sequence_order: number;
  original_text: string;
  translated_text: string;
  tokens: Token[];
  grammar_points: GrammarPoint[];
}

export interface AnalyzeResponse {
  difficulty_level: string;
  kanji_ratio: number;
  sentences: SentenceAnalysis[];
}

export interface SyncPayload {
  user_id: string;
  since_timestamp?: number;
  articles?: Article[];
  sentences?: Sentence[];
  vocabularies?: Vocabulary[];
  user_settings?: UserSettings;
}

export interface SyncResponse {
  success: boolean;
  timestamp: number;
  articles: Article[];
  sentences: Sentence[];
  vocabularies: Vocabulary[];
  user_settings?: UserSettings;
}
