import { Hono } from 'hono';
import { cors } from 'hono/cors';
import {
  Article,
  Sentence,
  Vocabulary,
  UserSettings,
  AnalyzeRequest,
  SyncPayload
} from './types';
import { globalMemoryStorage, StorageEngine } from './storage';
import { analyzeWithDeepSeek } from './deepseek';
import { analyzeJapaneseTextFallback } from './tokenizer';
import { handleTtsSynthesis } from './tts';

export interface EnvBindings {
  DB?: D1Database;
  KV_STORE?: KVNamespace;
  R2_AUDIO?: R2Bucket;
  DEEPSEEK_API_KEY?: string;
  PIPER_TTS_ENDPOINT?: string;
}
export type KomorebiApp = Hono<{ Bindings: EnvBindings }>;

export function createApp(customStorage?: StorageEngine): KomorebiApp {
  const app: KomorebiApp = new Hono<{ Bindings: EnvBindings }>();

  // Helper to obtain storage engine
  const getStorage = (c: { env: EnvBindings }): StorageEngine => {
    return customStorage || globalMemoryStorage;
  };

  // CORS middleware allowing cross-origin requests from web client and mobile clients
  app.use('*', cors({
    origin: '*',
    allowMethods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
    allowHeaders: ['Content-Type', 'Authorization', 'X-Requested-With', 'X-User-Id']
  }));

  // Helper for resolving User ID
  const getUserId = (c: { req: { header: (name: string) => string | undefined } }): string => {
    return c.req.header('X-User-Id') || 'usr_default';
  };

  // 1. Health check & Root info
  app.get('/', (c) => {
    return c.json({
      name: 'Komorebi Reader API',
      version: '1.0.0',
      status: 'operational',
      environment: 'Cloudflare Edge',
      endpoints: {
        analyze: 'POST /api/v1/analyze',
        articles: 'GET, POST /api/v1/articles',
        vocabularies: 'GET, POST /api/v1/vocabularies',
        inspect: 'POST /api/v1/analytics/inspect',
        stats: 'GET /api/v1/analytics/stats',
        tts: 'POST /api/v1/tts',
        sync: 'GET, POST /api/v1/sync',
        settings: 'GET, POST /api/v1/settings'
      }
    });
  });

  // 2. Morphological Analysis & Parsing (DeepSeek LLM + Deterministic Fallback)
  app.post('/api/v1/analyze', async (c) => {
    try {
      const body = await c.req.json<AnalyzeRequest>();
      if (!body.text || body.text.trim() === '') {
        return c.json({ error: 'Parameter text tidak boleh kosong' }, 400);
      }

      // Check header BYOK key or body api_key or env key
      const headerKey = c.req.header('Authorization')?.replace('Bearer ', '');
      const apiKey = body.api_key || headerKey || c.env?.DEEPSEEK_API_KEY;

      const result = await analyzeWithDeepSeek(body, apiKey);
      return c.json(result);
    } catch (err) {
      console.error('Analysis error:', err);
      // Fail-safe to deterministic tokenizer
      const body = await c.req.json<AnalyzeRequest>().catch(() => ({ text: '' }));
      if (body.text) {
        return c.json(analyzeJapaneseTextFallback(body.text));
      }
      return c.json({ error: 'Gagal memproses analisis teks' }, 500);
    }
  });

  // 3. Articles CRUD
  // GET /api/v1/articles
  app.get('/api/v1/articles', async (c) => {
    const storage = getStorage(c);
    const userId = getUserId(c);
    const category = c.req.query('category');
    const search = c.req.query('search');

    const articles = await storage.getArticles(userId, category, search);
    return c.json({ success: true, count: articles.length, articles });
  });

  // GET /api/v1/articles/:id
  app.get('/api/v1/articles/:id', async (c) => {
    const storage = getStorage(c);
    const id = c.req.param('id');
    const article = await storage.getArticleById(id);

    if (!article) {
      return c.json({ error: 'Artikel tidak ditemukan' }, 404);
    }
    return c.json({ success: true, article });
  });

  // POST /api/v1/articles
  app.post('/api/v1/articles', async (c) => {
    const storage = getStorage(c);
    const userId = getUserId(c);
    const body = await c.req.json<{
      title: string;
      category?: string;
      raw_text: string;
      difficulty_level?: string;
    }>();

    if (!body.title || !body.raw_text) {
      return c.json({ error: 'title dan raw_text wajib diisi' }, 400);
    }

    const now = Date.now();
    const articleId = `art_${now}_${Math.random().toString(36).substring(2, 7)}`;

    // Check header BYOK key or env key
    const headerKey = c.req.header('Authorization')?.replace('Bearer ', '');
    const apiKey = headerKey || c.env?.DEEPSEEK_API_KEY;

    // Analyze text to create structured sentences
    const analysis = apiKey
      ? await analyzeWithDeepSeek({ text: body.raw_text }, apiKey)
      : analyzeJapaneseTextFallback(body.raw_text);
    const sentences: Sentence[] = analysis.sentences.map((s, idx) => ({
      id: `sent_${articleId}_${idx + 1}`,
      article_id: articleId,
      original_text: s.original_text,
      translated_text: s.translated_text,
      furigana_payload: JSON.stringify(s.tokens),
      grammar_analysis: JSON.stringify(s.grammar_points),
      sequence_order: s.sequence_order,
      inspection_count: 0,
      audio_play_count: 0,
      needs_deep_study: 0,
      updated_at: now
    }));

    const article: Article = {
      id: articleId,
      user_id: userId,
      title: body.title,
      category: body.category || 'Umum',
      raw_text: body.raw_text,
      difficulty_level: body.difficulty_level || analysis.difficulty_level,
      kanji_ratio: analysis.kanji_ratio,
      created_at: now,
      updated_at: now
    };

    const created = await storage.createArticle(article, sentences);
    return c.json({ success: true, article: created }, 201);
  });

  // PUT /api/v1/articles/:id
  app.put('/api/v1/articles/:id', async (c) => {
    const storage = getStorage(c);
    const id = c.req.param('id');
    const body = await c.req.json<Partial<Article>>();

    const updated = await storage.updateArticle(id, body);
    if (!updated) {
      return c.json({ error: 'Artikel tidak ditemukan' }, 404);
    }
    return c.json({ success: true, article: updated });
  });

  // DELETE /api/v1/articles/:id
  app.delete('/api/v1/articles/:id', async (c) => {
    const storage = getStorage(c);
    const id = c.req.param('id');
    const deleted = await storage.deleteArticle(id);

    if (!deleted) {
      return c.json({ error: 'Artikel tidak ditemukan atau gagal dihapus' }, 404);
    }
    return c.json({ success: true, message: 'Artikel berhasil dihapus' });
  });

  // 4. Vocabularies CRUD
  // GET /api/v1/vocabularies
  app.get('/api/v1/vocabularies', async (c) => {
    const storage = getStorage(c);
    const userId = getUserId(c);
    const statusParam = c.req.query('status');
    const jlptParam = c.req.query('jlpt');

    const status = statusParam !== undefined ? parseInt(statusParam, 10) : undefined;
    const vocabularies = await storage.getVocabularies(userId, status, jlptParam);

    return c.json({ success: true, count: vocabularies.length, vocabularies });
  });

  // POST /api/v1/vocabularies
  app.post('/api/v1/vocabularies', async (c) => {
    const storage = getStorage(c);
    const userId = getUserId(c);
    const body = await c.req.json<{
      kanji: string;
      reading: string;
      meaning: string;
      part_of_speech?: string;
      jlpt_level?: string;
      sentence_id?: string;
    }>();

    if (!body.kanji || !body.reading || !body.meaning) {
      return c.json({ error: 'kanji, reading, dan meaning wajib diisi' }, 400);
    }

    const now = Date.now();
    const vocab: Vocabulary = {
      id: `voc_${now}_${Math.random().toString(36).substring(2, 7)}`,
      user_id: userId,
      sentence_id: body.sentence_id || null,
      kanji: body.kanji,
      reading: body.reading,
      meaning: body.meaning,
      part_of_speech: body.part_of_speech || 'noun',
      jlpt_level: body.jlpt_level || 'N5',
      mastery_status: 0,
      review_count: 0,
      next_review_at: now + 86400000, // 1 day ahead
      created_at: now,
      updated_at: now
    };

    const created = await storage.createVocabulary(vocab);
    return c.json({ success: true, vocabulary: created }, 201);
  });

  // PUT /api/v1/vocabularies/:id
  app.put('/api/v1/vocabularies/:id', async (c) => {
    const storage = getStorage(c);
    const id = c.req.param('id');
    const body = await c.req.json<Partial<Vocabulary>>();

    const updated = await storage.updateVocabulary(id, body);
    if (!updated) {
      return c.json({ error: 'Kosakata tidak ditemukan' }, 404);
    }
    return c.json({ success: true, vocabulary: updated });
  });

  // DELETE /api/v1/vocabularies/:id
  app.delete('/api/v1/vocabularies/:id', async (c) => {
    const storage = getStorage(c);
    const id = c.req.param('id');
    const deleted = await storage.deleteVocabulary(id);

    if (!deleted) {
      return c.json({ error: 'Kosakata tidak ditemukan' }, 404);
    }
    return c.json({ success: true, message: 'Kosakata berhasil dihapus' });
  });

  // 5. Telemetry & Analytics
  // POST /api/v1/analytics/inspect
  app.post('/api/v1/analytics/inspect', async (c) => {
    const storage = getStorage(c);
    const userId = getUserId(c);
    const body = await c.req.json<{ sentence_id: string }>();

    if (!body.sentence_id) {
      return c.json({ error: 'sentence_id diperlukan' }, 400);
    }

    const result = await storage.inspectSentence(body.sentence_id, userId);
    return c.json({
      success: true,
      inspection_count: result.inspectionCount,
      needs_deep_study: result.needsDeepStudy
    });
  });

  // GET /api/v1/analytics/stats
  app.get('/api/v1/analytics/stats', async (c) => {
    const storage = getStorage(c);
    const userId = getUserId(c);
    const stats = await storage.getAnalyticsStats(userId);
    return c.json({ success: true, stats });
  });

  // 6. Audio Synthesis (Piper TTS + Cache)
  // POST /api/v1/tts
  app.post('/api/v1/tts', async (c) => {
    const storage = getStorage(c);
    const body = await c.req.json<{
      text: string;
      speed?: number;
      voice_model?: string;
    }>();

    if (!body.text || body.text.trim() === '') {
      return c.json({ error: 'text diperlukan' }, 400);
    }

    const speed = body.speed || 1.0;
    const voiceModel = body.voice_model || 'ja_JP-hira-medium';

    const result = await handleTtsSynthesis(
      body.text,
      speed,
      voiceModel,
      storage,
      c.env?.PIPER_TTS_ENDPOINT,
      c.env?.R2_AUDIO
    );

    return c.json(result);
  });

  // 7. User Settings & BYOK API Keys
  // GET /api/v1/settings
  app.get('/api/v1/settings', async (c) => {
    const storage = getStorage(c);
    const userId = getUserId(c);
    const settings = await storage.getUserSettings(userId);
    return c.json({ success: true, settings });
  });

  // POST /api/v1/settings
  app.post('/api/v1/settings', async (c) => {
    const storage = getStorage(c);
    const userId = getUserId(c);
    const body = await c.req.json<Partial<UserSettings>>();

    const current = await storage.getUserSettings(userId);
    const updated: UserSettings = {
      ...current,
      ...body,
      user_id: userId,
      updated_at: Date.now()
    };

    const saved = await storage.saveUserSettings(updated);
    return c.json({ success: true, settings: saved });
  });

  // 8. Bidirectional Synchronization
  // GET /api/v1/sync?since=timestamp
  app.get('/api/v1/sync', async (c) => {
    const storage = getStorage(c);
    const userId = getUserId(c);
    const since = parseInt(c.req.query('since') || '0', 10);

    const delta = await storage.getSyncDelta(userId, since);
    return c.json({
      success: true,
      timestamp: Date.now(),
      articles: delta.articles,
      sentences: delta.sentences,
      vocabularies: delta.vocabularies,
      user_settings: delta.settings
    });
  });

  // POST /api/v1/sync
  app.post('/api/v1/sync', async (c) => {
    const storage = getStorage(c);
    const userId = getUserId(c);
    const body = await c.req.json<SyncPayload>();

    await storage.applySyncPush(
      userId,
      body.articles,
      body.sentences,
      body.vocabularies,
      body.user_settings
    );

    const delta = await storage.getSyncDelta(userId, body.since_timestamp || 0);

    return c.json({
      success: true,
      timestamp: Date.now(),
      articles: delta.articles,
      sentences: delta.sentences,
      vocabularies: delta.vocabularies,
      user_settings: delta.settings
    });
  });

  return app;
}

const defaultApp = createApp();
export default defaultApp;
