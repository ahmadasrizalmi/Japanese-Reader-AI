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
  needs_deep_study: number;
  updated_at: number;
}

export interface Article {
  id: string;
  user_id: string;
  title: string;
  category: string;
  raw_text: string;
  difficulty_level: string;
  kanji_ratio: number;
  created_at: number;
  updated_at: number;
  sentences?: Sentence[];
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

export interface UserSettings {
  user_id: string;
  target_language: string;
  deepseek_tone: 'colloquial' | 'formal' | 'literal';
  tts_speed: number;
  voice_model: string;
  furigana_mode: 'always' | 'tap' | 'off';
  dynamic_pastel_highlights: number;
  kanji_font_style: 'mincho' | 'gothic';
  deepseek_api_key?: string | null;
  updated_at: number;
}

export interface AnalyticsStats {
  readingTimeHours: number;
  masteredSentences: number;
  totalVocab: number;
  weeklyStudyMinutes: Array<{ day: string; minutes: number }>;
  jlptDistribution: Record<string, number>;
}

export interface AppState {
  currentView: 'splash' | 'quick-read' | 'koleksi' | 'reader' | 'vocab' | 'analytics' | 'settings';
  articles: Article[];
  vocabularies: Vocabulary[];
  activeArticle: Article | null;
  activeSentenceIndex: number;
  settings: UserSettings;
  stats: AnalyticsStats;
  syncStatus: 'synced' | 'syncing' | 'offline';
  lastSyncTimestamp: number;
  apiBaseUrl: string;
}

export const PASTEL_COLORS: string[] = [
  '#FEF3C7', // Soft Yellow
  '#D1FAE5', // Soft Green
  '#FCE7F3', // Soft Pink
  '#DBEAFE', // Soft Blue
  '#EDE9FE', // Soft Lavender
  '#D1F4E0'  // Soft Mint
];

export function getPastelHighlight(index: number): string {
  return PASTEL_COLORS[Math.abs(index) % PASTEL_COLORS.length];
}

export function parseTokens(furiganaPayload: string): Token[] {
  try {
    const parsed = JSON.parse(furiganaPayload);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function parseGrammarPoints(grammarAnalysis: string): GrammarPoint[] {
  try {
    const parsed = JSON.parse(grammarAnalysis);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function buildRubyHtml(tokens: Token[], furiganaMode: 'always' | 'tap' | 'off'): string {
  return tokens.map(token => {
    const hasReading = token.reading && token.reading !== token.surface && !token.reading.includes('、') && !token.reading.includes('。');
    if (!hasReading) {
      return `<span>${token.surface}</span>`;
    }

    if (furiganaMode === 'off') {
      return `<span>${token.surface}</span>`;
    }

    const rtClass = furiganaMode === 'tap'
      ? 'opacity-0 hover:opacity-100 focus:opacity-100 transition-opacity cursor-pointer font-bold text-primary text-[11px]'
      : 'font-bold text-primary text-[11px]';

    return `<ruby class="px-0.5">${token.surface}<rt class="${rtClass}">${token.reading}</rt></ruby>`;
  }).join('');
}

// Spaced Repetition (SuperMemo-2 simplified) calculator
export function calculateNextSrsInterval(currentReviewCount: number, quality: number): { nextReviewAt: number; nextMastery: number } {
  // quality: 0-5 (0=fail, 3=pass, 5=perfect)
  const now = Date.now();
  let days = 1;
  let mastery = 0;

  if (quality >= 3) {
    if (currentReviewCount === 0) {
      days = 1;
      mastery = 1;
    } else if (currentReviewCount === 1) {
      days = 6;
      mastery = 1;
    } else {
      days = Math.round(6 * Math.pow(1.8, currentReviewCount - 1));
      mastery = 2; // Dikuasai
    }
  } else {
    days = 1;
    mastery = 0; // Reset to Baru
  }

  return {
    nextReviewAt: now + days * 86400000,
    nextMastery: mastery
  };
}

export function createInitialState(apiBaseUrl: string = 'https://komorebi-reader-api.hannabi3108.workers.dev'): AppState {
  const now = Date.now();
  return {
    currentView: 'splash',
    articles: [
      {
        id: 'art_tanaka_line',
        user_id: 'usr_default',
        title: 'Pesan LINE Tanaka',
        category: 'Percakapan',
        raw_text: '今週末、渋谷のカフェで話しませんか？',
        difficulty_level: 'N4',
        kanji_ratio: 0.38,
        created_at: now - 7200000,
        updated_at: now - 7200000,
        sentences: [
          {
            id: 'sent_tanaka_01',
            article_id: 'art_tanaka_line',
            original_text: '今週末、渋谷のカフェで話しませんか？',
            translated_text: 'Maukah kamu berbincang di kafe Shibuya akhir pekan ini?',
            furigana_payload: JSON.stringify([
              { surface: '今週末', reading: 'こんしゅうまつ', romaji: 'konshuumatsu', pos: 'noun', jlpt: 'N4', meaning: 'Akhir pekan ini' },
              { surface: '、', reading: '', romaji: '', pos: 'punct', jlpt: '-' },
              { surface: '渋谷', reading: 'しぶや', romaji: 'shibuya', pos: 'noun', jlpt: 'N4', meaning: 'Shibuya' },
              { surface: 'の', reading: 'の', romaji: 'no', pos: 'particle', jlpt: 'N5', meaning: 'Partikel asosiasi' },
              { surface: 'カフェ', reading: 'カフェ', romaji: 'kafe', pos: 'noun', jlpt: 'N5', meaning: 'Kafe' },
              { surface: 'で', reading: 'で', romaji: 'de', pos: 'particle', jlpt: 'N5', meaning: 'Partikel lokasi' },
              { surface: '話しませんか', reading: 'はなしませんか', romaji: 'hanashimasenka', pos: 'verb', jlpt: 'N5', meaning: 'Maukah mengobrol?' },
              { surface: '？', reading: '', romaji: '', pos: 'punct', jlpt: '-' }
            ]),
            grammar_analysis: JSON.stringify([
              { pattern: '〜ませんか', explanation: 'Bentuk ajakan sopan untuk mengundang lawan bicara.' }
            ]),
            sequence_order: 1,
            inspection_count: 2,
            audio_play_count: 1,
            needs_deep_study: 0,
            updated_at: now - 7200000
          }
        ]
      },
      {
        id: 'art_konbini_ningen',
        user_id: 'usr_default',
        title: 'Kutipan Konbini Ningen',
        category: 'Buku & Artikel',
        raw_text: '普通の人という架空の生き物を演じる。',
        difficulty_level: 'N3',
        kanji_ratio: 0.52,
        created_at: now - 86400000,
        updated_at: now - 86400000,
        sentences: [
          {
            id: 'sent_konbini_01',
            article_id: 'art_konbini_ningen',
            original_text: '普通の人という架空の生き物を演じる。',
            translated_text: 'Memerankan sosok makhluk imajiner yang dinamakan manusia biasa.',
            furigana_payload: JSON.stringify([
              { surface: '普通', reading: 'ふつう', romaji: 'futsuu', pos: 'noun', jlpt: 'N4', meaning: 'Biasa / normal' },
              { surface: 'の', reading: 'の', romaji: 'no', pos: 'particle', jlpt: 'N5', meaning: 'Partikel asosiasi' },
              { surface: '人間', reading: 'にんげん', romaji: 'ningen', pos: 'noun', jlpt: 'N3', meaning: 'Manusia' },
              { surface: 'という', reading: 'という', romaji: 'toiu', pos: 'particle', jlpt: 'N4', meaning: 'Yang dinamakan' },
              { surface: '架空', reading: 'かくう', romaji: 'kakuu', pos: 'noun', jlpt: 'N1', meaning: 'Fiktif / imajiner' },
              { surface: 'の', reading: 'の', romaji: 'no', pos: 'particle', jlpt: 'N5', meaning: 'Partikel kepemilikan' },
              { surface: '生き物', reading: 'いきもの', romaji: 'ikimono', pos: 'noun', jlpt: 'N3', meaning: 'Makhluk hidup' },
              { surface: 'を', reading: 'を', romaji: 'wo', pos: 'particle', jlpt: 'N5', meaning: 'Partikel objek' },
              { surface: '演じる', reading: 'えんじる', romaji: 'enjiru', pos: 'verb', jlpt: 'N2', meaning: 'Memerankan' },
              { surface: '。', reading: '', romaji: '', pos: 'punct', jlpt: '-' }
            ]),
            grammar_analysis: JSON.stringify([
              { pattern: '〜という', explanation: 'Menyatakan sebutan atau definisi konsep.' }
            ]),
            sequence_order: 1,
            inspection_count: 1,
            audio_play_count: 1,
            needs_deep_study: 0,
            updated_at: now - 86400000
          }
        ]
      }
    ],
    vocabularies: [
      {
        id: 'voc_01',
        user_id: 'usr_default',
        sentence_id: 'sent_tanaka_01',
        kanji: '今週末',
        reading: 'こんしゅうまつ',
        meaning: 'Akhir pekan ini',
        part_of_speech: 'noun',
        jlpt_level: 'N4',
        mastery_status: 1,
        review_count: 2,
        next_review_at: now + 86400000,
        created_at: now - 7200000,
        updated_at: now - 7200000
      },
      {
        id: 'voc_02',
        user_id: 'usr_default',
        sentence_id: 'sent_tanaka_01',
        kanji: '咲く',
        reading: 'さく',
        meaning: 'Mekar (khusus bunga)',
        part_of_speech: 'verb',
        jlpt_level: 'N4',
        mastery_status: 2,
        review_count: 5,
        next_review_at: now + 500000000,
        created_at: now - 90000000,
        updated_at: now - 90000000
      }
    ],
    activeArticle: null,
    activeSentenceIndex: 0,
    settings: {
      user_id: 'usr_default',
      target_language: 'id-ID',
      deepseek_tone: 'colloquial',
      tts_speed: 1.0,
      voice_model: 'ja_JP-hira-medium',
      furigana_mode: 'always',
      dynamic_pastel_highlights: 1,
      kanji_font_style: 'mincho',
      deepseek_api_key: null,
      updated_at: now
    },
    stats: {
      readingTimeHours: 4.2,
      masteredSentences: 86,
      totalVocab: 144,
      weeklyStudyMinutes: [
        { day: 'Sen', minutes: 25 },
        { day: 'Sel', minutes: 40 },
        { day: 'Rab', minutes: 30 },
        { day: 'Kam', minutes: 55 },
        { day: 'Jum', minutes: 65 },
        { day: 'Sab', minutes: 45 },
        { day: 'Min', minutes: 50 }
      ],
      jlptDistribution: { N5: 45, N4: 60, N3: 28, N2: 8, N1: 3 }
    },
    syncStatus: 'synced',
    lastSyncTimestamp: now,
    apiBaseUrl: apiBaseUrl
  };
}

export class AppStore {
  private state: AppState;
  private listeners: Array<(state: AppState) => void> = [];

  constructor(initialState?: AppState) {
    this.state = initialState || createInitialState();
    this.loadFromLocalStorage();
  }

  public getState(): AppState {
    return this.state;
  }

  public subscribe(listener: (state: AppState) => void): () => void {
    this.listeners.push(listener);
    return () => {
      this.listeners = this.listeners.filter(l => l !== listener);
    };
  }

  private notify(): void {
    this.saveToLocalStorage();
    for (const listener of this.listeners) {
      listener(this.state);
    }
  }

  private saveToLocalStorage(): void {
    if (typeof localStorage !== 'undefined') {
      try {
        localStorage.setItem('komorebi_state', JSON.stringify({
          articles: this.state.articles,
          vocabularies: this.state.vocabularies,
          settings: this.state.settings,
          lastSyncTimestamp: this.state.lastSyncTimestamp
        }));
      } catch (e) {
        console.warn('Could not save to localStorage', e);
      }
    }
  }

  private loadFromLocalStorage(): void {
    if (typeof localStorage !== 'undefined') {
      try {
        const raw = localStorage.getItem('komorebi_state');
        if (raw) {
          const cached = JSON.parse(raw);
          if (cached.articles) this.state.articles = cached.articles;
          if (cached.vocabularies) this.state.vocabularies = cached.vocabularies;
          if (cached.settings) this.state.settings = cached.settings;
          if (cached.lastSyncTimestamp) this.state.lastSyncTimestamp = cached.lastSyncTimestamp;
        }
      } catch (e) {
        console.warn('Could not load from localStorage', e);
      }
    }
  }

  public setView(view: AppState['currentView']): void {
    this.state.currentView = view;
    this.notify();
  }

  public setActiveArticle(article: Article | null): void {
    this.state.activeArticle = article;
    this.state.activeSentenceIndex = 0;
    this.notify();
  }

  public setActiveSentenceIndex(index: number): void {
    this.state.activeSentenceIndex = index;
    this.notify();
  }

  // Articles CRUD
  public addArticle(article: Article): void {
    this.state.articles = [article, ...this.state.articles.filter(a => a.id !== article.id)];
    this.notify();
  }

  public updateArticle(id: string, updates: Partial<Article>): void {
    this.state.articles = this.state.articles.map(a => {
      if (a.id === id) {
        return { ...a, ...updates, updated_at: Date.now() };
      }
      return a;
    });
    if (this.state.activeArticle?.id === id) {
      this.state.activeArticle = { ...this.state.activeArticle, ...updates, updated_at: Date.now() };
    }
    this.notify();
  }

  public deleteArticle(id: string): void {
    this.state.articles = this.state.articles.filter(a => a.id !== id);
    if (this.state.activeArticle?.id === id) {
      this.state.activeArticle = null;
    }
    this.notify();
  }

  // Vocabularies CRUD
  public addVocabulary(vocab: Vocabulary): void {
    this.state.vocabularies = [vocab, ...this.state.vocabularies.filter(v => v.id !== vocab.id)];
    this.notify();
  }

  public updateVocabulary(id: string, updates: Partial<Vocabulary>): void {
    this.state.vocabularies = this.state.vocabularies.map(v => {
      if (v.id === id) {
        return { ...v, ...updates, updated_at: Date.now() };
      }
      return v;
    });
    this.notify();
  }

  public deleteVocabulary(id: string): void {
    this.state.vocabularies = this.state.vocabularies.filter(v => v.id !== id);
    this.notify();
  }

  // Sentence Inspection Telemetry
  public inspectSentence(sentenceId: string): void {
    if (!this.state.activeArticle || !this.state.activeArticle.sentences) return;

    this.state.activeArticle.sentences = this.state.activeArticle.sentences.map(s => {
      if (s.id === sentenceId) {
        const nextCount = s.inspection_count + 1;
        return {
          ...s,
          inspection_count: nextCount,
          needs_deep_study: nextCount >= 3 ? 1 : s.needs_deep_study,
          updated_at: Date.now()
        };
      }
      return s;
    });

    // Also update in articles list
    this.updateArticle(this.state.activeArticle.id, {
      sentences: this.state.activeArticle.sentences
    });
  }

  // Settings
  public updateSettings(updates: Partial<UserSettings>): void {
    this.state.settings = {
      ...this.state.settings,
      ...updates,
      updated_at: Date.now()
    };
    this.notify();
  }

  // Synchronization with Cloudflare backend
  public async syncWithBackend(): Promise<void> {
    this.state.syncStatus = 'syncing';
    this.notify();

    try {
      const payload = {
        user_id: this.state.settings.user_id,
        since_timestamp: this.state.lastSyncTimestamp,
        articles: this.state.articles,
        vocabularies: this.state.vocabularies,
        user_settings: this.state.settings
      };

      const res = await fetch(`${this.state.apiBaseUrl}/api/v1/sync`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (res.ok) {
        const data = await res.json() as {
          timestamp: number;
          articles: Article[];
          vocabularies: Vocabulary[];
          user_settings?: UserSettings;
        };

        if (data.articles && data.articles.length > 0) {
          const map = new Map<string, Article>();
          for (const a of this.state.articles) map.set(a.id, a);
          for (const a of data.articles) map.set(a.id, a);
          this.state.articles = Array.from(map.values()).sort((a, b) => b.created_at - a.created_at);
        }

        if (data.vocabularies && data.vocabularies.length > 0) {
          const map = new Map<string, Vocabulary>();
          for (const v of this.state.vocabularies) map.set(v.id, v);
          for (const v of data.vocabularies) map.set(v.id, v);
          this.state.vocabularies = Array.from(map.values()).sort((a, b) => b.created_at - a.created_at);
        }

        if (data.user_settings) {
          this.state.settings = { ...this.state.settings, ...data.user_settings };
        }

        this.state.lastSyncTimestamp = data.timestamp || Date.now();
        this.state.syncStatus = 'synced';
      } else {
        this.state.syncStatus = 'offline';
      }
    } catch {
      this.state.syncStatus = 'offline';
    }

    this.notify();
  }
}
