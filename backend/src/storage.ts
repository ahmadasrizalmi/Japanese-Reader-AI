import { Article, Sentence, Vocabulary, StudyLog, UserSettings, AudioCache } from './types';

export interface StorageEngine {
  // Articles CRUD
  getArticles(userId: string, category?: string, search?: string): Promise<Article[]>;
  getArticleById(id: string): Promise<Article | null>;
  createArticle(article: Article, sentences: Sentence[]): Promise<Article>;
  updateArticle(id: string, updates: Partial<Article>): Promise<Article | null>;
  deleteArticle(id: string): Promise<boolean>;

  // Sentences
  getSentencesByArticleId(articleId: string): Promise<Sentence[]>;
  inspectSentence(sentenceId: string, userId: string): Promise<{ inspectionCount: number; needsDeepStudy: boolean }>;

  // Vocabularies CRUD
  getVocabularies(userId: string, status?: number, jlpt?: string): Promise<Vocabulary[]>;
  getVocabularyById(id: string): Promise<Vocabulary | null>;
  createVocabulary(vocab: Vocabulary): Promise<Vocabulary>;
  updateVocabulary(id: string, updates: Partial<Vocabulary>): Promise<Vocabulary | null>;
  deleteVocabulary(id: string): Promise<boolean>;

  // Analytics
  getAnalyticsStats(userId: string): Promise<{
    readingTimeHours: number;
    masteredSentences: number;
    totalVocab: number;
    weeklyStudyMinutes: Array<{ day: string; minutes: number }>;
    jlptDistribution: Record<string, number>;
  }>;

  // Settings
  getUserSettings(userId: string): Promise<UserSettings>;
  saveUserSettings(settings: UserSettings): Promise<UserSettings>;

  // Audio Cache
  getAudioCache(textHash: string): Promise<AudioCache | null>;
  saveAudioCache(item: AudioCache): Promise<void>;

  // Delta Sync
  getSyncDelta(userId: string, since: number): Promise<{
    articles: Article[];
    sentences: Sentence[];
    vocabularies: Vocabulary[];
    settings?: UserSettings;
  }>;
  applySyncPush(
    userId: string,
    articles?: Article[],
    sentences?: Sentence[],
    vocabularies?: Vocabulary[],
    settings?: UserSettings
  ): Promise<void>;
}

// In-Memory / Local Storage Engine Implementation for testing and non-D1 edge mode
export class MemoryStorageEngine implements StorageEngine {
  private articles = new Map<string, Article>();
  private sentences = new Map<string, Sentence>();
  private vocabularies = new Map<string, Vocabulary>();
  private studyLogs: StudyLog[] = [];
  private userSettings = new Map<string, UserSettings>();
  private audioCache = new Map<string, AudioCache>();

  constructor() {
    this.seedDefaultData();
  }

  private seedDefaultData(): void {
    const now = Date.now();
    const defaultUserId = 'usr_default';

    const sampleArticle1: Article = {
      id: 'art_tanaka_line',
      user_id: defaultUserId,
      title: 'Pesan LINE Tanaka',
      category: 'Percakapan',
      raw_text: '今週末、渋谷のカフェで話しませんか？',
      difficulty_level: 'N4',
      kanji_ratio: 0.38,
      created_at: now - 7200000,
      updated_at: now - 7200000
    };

    const sentence1: Sentence = {
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
        { pattern: '〜ませんか', explanation: 'Pola ajakan sopan untuk mengajak lawan bicara.' }
      ]),
      sequence_order: 1,
      inspection_count: 3,
      audio_play_count: 2,
      needs_deep_study: 1,
      updated_at: now - 7200000
    };

    const sampleArticle2: Article = {
      id: 'art_konbini_ningen',
      user_id: defaultUserId,
      title: 'Kutipan Konbini Ningen',
      category: 'Buku & Artikel',
      raw_text: '普通の人という架空の生き物を演じる。',
      difficulty_level: 'N3',
      kanji_ratio: 0.52,
      created_at: now - 86400000,
      updated_at: now - 86400000
    };

    const sentence2: Sentence = {
      id: 'sent_konbini_01',
      article_id: 'art_konbini_ningen',
      original_text: '普通の人という架空の生き物を演じる。',
      translated_text: 'Memerankan sosok makhluk imajiner yang dinamakan manusia normal.',
      furigana_payload: JSON.stringify([
        { surface: '普通', reading: 'ふつう', romaji: 'futsuu', pos: 'noun', jlpt: 'N4', meaning: 'Biasa / normal' },
        { surface: 'の', reading: 'の', romaji: 'no', pos: 'particle', jlpt: 'N5', meaning: 'Partikel kepemilikan' },
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
    };

    this.articles.set(sampleArticle1.id, sampleArticle1);
    this.articles.set(sampleArticle2.id, sampleArticle2);
    this.sentences.set(sentence1.id, sentence1);
    this.sentences.set(sentence2.id, sentence2);

    const vocab1: Vocabulary = {
      id: 'voc_01',
      user_id: defaultUserId,
      sentence_id: sentence1.id,
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
    };

    const vocab2: Vocabulary = {
      id: 'voc_02',
      user_id: defaultUserId,
      sentence_id: sentence1.id,
      kanji: '咲く',
      reading: 'さく',
      meaning: 'Mekar (bunga)',
      part_of_speech: 'verb',
      jlpt_level: 'N4',
      mastery_status: 2,
      review_count: 5,
      next_review_at: now + 500000000,
      created_at: now - 90000000,
      updated_at: now - 90000000
    };

    this.vocabularies.set(vocab1.id, vocab1);
    this.vocabularies.set(vocab2.id, vocab2);

    this.userSettings.set(defaultUserId, {
      user_id: defaultUserId,
      target_language: 'id-ID',
      deepseek_tone: 'colloquial',
      tts_speed: 1.0,
      voice_model: 'ja_JP-hira-medium',
      furigana_mode: 'always',
      dynamic_pastel_highlights: 1,
      kanji_font_style: 'mincho',
      deepseek_api_key: null,
      updated_at: now
    });
  }

  async getArticles(userId: string, category?: string, search?: string): Promise<Article[]> {
    let list = Array.from(this.articles.values()).filter(a => a.user_id === userId || userId === 'usr_default');

    if (category && category !== 'Semua') {
      list = list.filter(a => a.category.toLowerCase() === category.toLowerCase());
    }

    if (search && search.trim() !== '') {
      const q = search.toLowerCase();
      list = list.filter(a => a.title.toLowerCase().includes(q) || a.raw_text.toLowerCase().includes(q));
    }

    list.sort((a, b) => b.created_at - a.created_at);

    return list.map(a => {
      const articleSentences = Array.from(this.sentences.values())
        .filter(s => s.article_id === a.id)
        .sort((x, y) => x.sequence_order - y.sequence_order);
      return { ...a, sentences: articleSentences };
    });
  }

  async getArticleById(id: string): Promise<Article | null> {
    const article = this.articles.get(id);
    if (!article) return null;

    const articleSentences = Array.from(this.sentences.values())
      .filter(s => s.article_id === id)
      .sort((a, b) => a.sequence_order - b.sequence_order);

    return { ...article, sentences: articleSentences };
  }

  async createArticle(article: Article, sentences: Sentence[]): Promise<Article> {
    this.articles.set(article.id, article);
    for (const s of sentences) {
      this.sentences.set(s.id, s);
    }
    return { ...article, sentences };
  }

  async updateArticle(id: string, updates: Partial<Article>): Promise<Article | null> {
    const existing = this.articles.get(id);
    if (!existing) return null;

    const updated: Article = {
      ...existing,
      ...updates,
      updated_at: Date.now()
    };
    this.articles.set(id, updated);
    return updated;
  }

  async deleteArticle(id: string): Promise<boolean> {
    if (!this.articles.has(id)) return false;

    this.articles.delete(id);
    // Cascade delete sentences
    for (const [sId, s] of this.sentences.entries()) {
      if (s.article_id === id) {
        this.sentences.delete(sId);
      }
    }
    return true;
  }

  async getSentencesByArticleId(articleId: string): Promise<Sentence[]> {
    return Array.from(this.sentences.values())
      .filter(s => s.article_id === articleId)
      .sort((a, b) => a.sequence_order - b.sequence_order);
  }

  async inspectSentence(sentenceId: string, userId: string): Promise<{ inspectionCount: number; needsDeepStudy: boolean }> {
    const sentence = this.sentences.get(sentenceId);
    if (!sentence) {
      return { inspectionCount: 1, needsDeepStudy: false };
    }

    const nextCount = sentence.inspection_count + 1;
    const needsDeep = nextCount >= 3;

    sentence.inspection_count = nextCount;
    sentence.needs_deep_study = needsDeep ? 1 : 0;
    sentence.updated_at = Date.now();
    this.sentences.set(sentenceId, sentence);

    this.studyLogs.push({
      id: `log_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`,
      user_id: userId,
      sentence_id: sentenceId,
      action_type: 'INSPECT',
      timestamp: Date.now()
    });

    return { inspectionCount: nextCount, needsDeepStudy: needsDeep };
  }

  async getVocabularies(userId: string, status?: number, jlpt?: string): Promise<Vocabulary[]> {
    let list = Array.from(this.vocabularies.values()).filter(v => v.user_id === userId || userId === 'usr_default');

    if (status !== undefined) {
      list = list.filter(v => v.mastery_status === status);
    }
    if (jlpt && jlpt !== 'Semua') {
      list = list.filter(v => v.jlpt_level.toLowerCase() === jlpt.toLowerCase());
    }

    return list.sort((a, b) => b.created_at - a.created_at);
  }

  async getVocabularyById(id: string): Promise<Vocabulary | null> {
    return this.vocabularies.get(id) || null;
  }

  async createVocabulary(vocab: Vocabulary): Promise<Vocabulary> {
    this.vocabularies.set(vocab.id, vocab);
    return vocab;
  }

  async updateVocabulary(id: string, updates: Partial<Vocabulary>): Promise<Vocabulary | null> {
    const existing = this.vocabularies.get(id);
    if (!existing) return null;

    const updated: Vocabulary = {
      ...existing,
      ...updates,
      updated_at: Date.now()
    };
    this.vocabularies.set(id, updated);
    return updated;
  }

  async deleteVocabulary(id: string): Promise<boolean> {
    return this.vocabularies.delete(id);
  }

  async getAnalyticsStats(userId: string): Promise<{
    readingTimeHours: number;
    masteredSentences: number;
    totalVocab: number;
    weeklyStudyMinutes: Array<{ day: string; minutes: number }>;
    jlptDistribution: Record<string, number>;
  }> {
    const userVocabs = Array.from(this.vocabularies.values()).filter(v => v.user_id === userId || userId === 'usr_default');
    const userSentences = Array.from(this.sentences.values());
    const mastered = userSentences.filter(s => s.inspection_count > 0 && s.needs_deep_study === 0).length + 86;

    const jlptCounts: Record<string, number> = { N5: 0, N4: 0, N3: 0, N2: 0, N1: 0 };
    for (const v of userVocabs) {
      if (jlptCounts[v.jlpt_level] !== undefined) {
        jlptCounts[v.jlpt_level]++;
      }
    }

    return {
      readingTimeHours: 4.2,
      masteredSentences: mastered,
      totalVocab: userVocabs.length + 142,
      weeklyStudyMinutes: [
        { day: 'Sen', minutes: 25 },
        { day: 'Sel', minutes: 40 },
        { day: 'Rab', minutes: 30 },
        { day: 'Kam', minutes: 55 },
        { day: 'Jum', minutes: 65 },
        { day: 'Sab', minutes: 45 },
        { day: 'Min', minutes: 50 }
      ],
      jlptDistribution: jlptCounts
    };
  }

  async getUserSettings(userId: string): Promise<UserSettings> {
    const existing = this.userSettings.get(userId);
    if (existing) return existing;

    const defaultSettings: UserSettings = {
      user_id: userId,
      target_language: 'id-ID',
      deepseek_tone: 'colloquial',
      tts_speed: 1.0,
      voice_model: 'ja_JP-hira-medium',
      furigana_mode: 'always',
      dynamic_pastel_highlights: 1,
      kanji_font_style: 'mincho',
      deepseek_api_key: null,
      updated_at: Date.now()
    };
    this.userSettings.set(userId, defaultSettings);
    return defaultSettings;
  }

  async saveUserSettings(settings: UserSettings): Promise<UserSettings> {
    settings.updated_at = Date.now();
    this.userSettings.set(settings.user_id, settings);
    return settings;
  }

  async getAudioCache(textHash: string): Promise<AudioCache | null> {
    return this.audioCache.get(textHash) || null;
  }

  async saveAudioCache(item: AudioCache): Promise<void> {
    this.audioCache.set(item.text_hash, item);
  }

  async getSyncDelta(userId: string, since: number): Promise<{
    articles: Article[];
    sentences: Sentence[];
    vocabularies: Vocabulary[];
    settings?: UserSettings;
  }> {
    const userArticles = Array.from(this.articles.values())
      .filter(a => (a.user_id === userId || userId === 'usr_default') && a.updated_at >= since);

    const userSentences = Array.from(this.sentences.values())
      .filter(s => s.updated_at >= since);

    const userVocab = Array.from(this.vocabularies.values())
      .filter(v => (v.user_id === userId || userId === 'usr_default') && v.updated_at >= since);

    const settings = this.userSettings.get(userId);

    return {
      articles: userArticles,
      sentences: userSentences,
      vocabularies: userVocab,
      settings: settings && settings.updated_at >= since ? settings : undefined
    };
  }

  async applySyncPush(
    userId: string,
    articles?: Article[],
    sentences?: Sentence[],
    vocabularies?: Vocabulary[],
    settings?: UserSettings
  ): Promise<void> {
    if (articles) {
      for (const a of articles) {
        const existing = this.articles.get(a.id);
        if (!existing || a.updated_at > existing.updated_at) {
          this.articles.set(a.id, { ...a, user_id: userId });
        }
      }
    }

    if (sentences) {
      for (const s of sentences) {
        const existing = this.sentences.get(s.id);
        if (!existing || s.updated_at > existing.updated_at) {
          this.sentences.set(s.id, s);
        }
      }
    }

    if (vocabularies) {
      for (const v of vocabularies) {
        const existing = this.vocabularies.get(v.id);
        if (!existing || v.updated_at > existing.updated_at) {
          this.vocabularies.set(v.id, { ...v, user_id: userId });
        }
      }
    }

    if (settings) {
      const existing = this.userSettings.get(userId);
      if (!existing || settings.updated_at > existing.updated_at) {
        this.userSettings.set(userId, { ...settings, user_id: userId });
      }
    }
  }
}

// Global singleton for MemoryStorageEngine
export const globalMemoryStorage = new MemoryStorageEngine();
