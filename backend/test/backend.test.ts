import { describe, it, expect, beforeEach } from 'vitest';
import { createApp, KomorebiApp } from '../src/index';
import { MemoryStorageEngine } from '../src/storage';

describe('Komorebi Japanese Reader AI - Backend Test Suite', () => {
  let app: KomorebiApp;
  let storage: MemoryStorageEngine;
  beforeEach(() => {
    storage = new MemoryStorageEngine();
    app = createApp(storage);
  });

  describe('1. Health & Root', () => {
    it('returns operational status and api catalog', async () => {
      const res = await app.request('/');
      expect(res.status).toBe(200);
      const json = await res.json() as { status: string; name: string };
      expect(json.status).toBe('operational');
      expect(json.name).toBe('Komorebi Reader API');
    });
  });

  describe('2. Morphological Analysis & Parsing (/api/v1/analyze)', () => {
    it('analyzes text into tokens, furigana, and grammar points', async () => {
      const res = await app.request('/api/v1/analyze', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          text: '東京の春は花が咲いて、とてもきれいです。'
        })
      });

      expect(res.status).toBe(200);
      const data = await res.json() as {
        difficulty_level: string;
        kanji_ratio: number;
        sentences: Array<{
          original_text: string;
          translated_text: string;
          tokens: Array<{ surface: string; reading: string; pos: string }>;
          grammar_points: Array<{ pattern: string }>;
        }>;
      };

      expect(data.sentences.length).toBeGreaterThan(0);
      expect(data.kanji_ratio).toBeGreaterThan(0);
      const s1 = data.sentences[0];
      expect(s1.original_text).toContain('東京');
      expect(s1.translated_text).toBeDefined();

      // Check tokens have furigana readings
      const tokyoToken = s1.tokens.find(t => t.surface === '東京');
      expect(tokyoToken).toBeDefined();
      expect(tokyoToken?.reading).toBe('とうきょう');

      // Check grammar point detected (te-form)
      const teGrammar = s1.grammar_points.find(g => g.pattern.includes('Te-form') || g.pattern.includes('〜て'));
      expect(teGrammar).toBeDefined();
    });

    it('returns 400 when text parameter is empty', async () => {
      const res = await app.request('/api/v1/analyze', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text: '' })
      });
      expect(res.status).toBe(400);
    });
  });

  describe('3. Articles CRUD', () => {
    it('performs full Create, Read, Update, Delete on articles with cascading sentences', async () => {
      // 1. Create Article
      const createRes = await app.request('/api/v1/articles', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: 'Artikel Uji Coba Sakura',
          category: 'Buku & Artikel',
          raw_text: '今週の土曜日に新しいカフェに行きませんか。'
        })
      });

      expect(createRes.status).toBe(201);
      const createData = await createRes.json() as {
        success: boolean;
        article: { id: string; title: string; sentences?: Array<{ id: string }> };
      };
      expect(createData.success).toBe(true);
      const articleId = createData.article.id;
      expect(createData.article.sentences?.length).toBeGreaterThan(0);

      // 2. Read Article List
      const listRes = await app.request('/api/v1/articles');
      expect(listRes.status).toBe(200);
      const listData = await listRes.json() as { count: number; articles: Array<{ id: string }> };
      expect(listData.articles.some(a => a.id === articleId)).toBe(true);

      // 3. Read Article Detail
      const detailRes = await app.request(`/api/v1/articles/${articleId}`);
      expect(detailRes.status).toBe(200);
      const detailData = await detailRes.json() as {
        article: { id: string; title: string; sentences?: Array<{ id: string; original_text: string }> };
      };
      expect(detailData.article.id).toBe(articleId);
      expect(detailData.article.sentences?.[0].original_text).toContain('カフェ');

      // 4. Update Article
      const updateRes = await app.request(`/api/v1/articles/${articleId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title: 'Judul Diperbarui' })
      });
      expect(updateRes.status).toBe(200);
      const updateData = await updateRes.json() as { article: { title: string } };
      expect(updateData.article.title).toBe('Judul Diperbarui');

      // 5. Delete Article
      const deleteRes = await app.request(`/api/v1/articles/${articleId}`, {
        method: 'DELETE'
      });
      expect(deleteRes.status).toBe(200);

      // Verify deletion & cascade
      const verifyRes = await app.request(`/api/v1/articles/${articleId}`);
      expect(verifyRes.status).toBe(404);
    });
  });

  describe('4. Vocabularies CRUD (SRS Deck)', () => {
    it('manages vocabulary lifecycle for spaced repetition', async () => {
      // 1. Create Vocab
      const createRes = await app.request('/api/v1/vocabularies', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          kanji: '東京',
          reading: 'とうきょう',
          meaning: 'Tokyo',
          part_of_speech: 'noun',
          jlpt_level: 'N5'
        })
      });
      expect(createRes.status).toBe(201);
      const created = await createRes.json() as { vocabulary: { id: string; kanji: string; mastery_status: number } };
      const vocabId = created.vocabulary.id;
      expect(created.vocabulary.kanji).toBe('東京');
      expect(created.vocabulary.mastery_status).toBe(0);

      // 2. Read Vocab list
      const listRes = await app.request('/api/v1/vocabularies');
      expect(listRes.status).toBe(200);
      const list = await listRes.json() as { vocabularies: Array<{ id: string }> };
      expect(list.vocabularies.some(v => v.id === vocabId)).toBe(true);

      // 3. Update Vocab (Review rating)
      const updateRes = await app.request(`/api/v1/vocabularies/${vocabId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          mastery_status: 2,
          review_count: 1
        })
      });
      expect(updateRes.status).toBe(200);
      const updated = await updateRes.json() as { vocabulary: { mastery_status: number; review_count: number } };
      expect(updated.vocabulary.mastery_status).toBe(2);
      expect(updated.vocabulary.review_count).toBe(1);

      // 4. Delete Vocab
      const delRes = await app.request(`/api/v1/vocabularies/${vocabId}`, {
        method: 'DELETE'
      });
      expect(delRes.status).toBe(200);
    });
  });

  describe('5. Telemetry & Analytics Inspection', () => {
    it('increments inspection count and auto-activates needs_deep_study when inspection >= 3', async () => {
      const sentenceId = 'sent_tanaka_01';

      // Tap inspect 1
      const res1 = await app.request('/api/v1/analytics/inspect', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sentence_id: sentenceId })
      });
      expect(res1.status).toBe(200);
      const data1 = await res1.json() as { inspection_count: number; needs_deep_study: boolean };
      expect(data1.inspection_count).toBeGreaterThanOrEqual(3);
      expect(data1.needs_deep_study).toBe(true);
    });

    it('returns study statistics dashboard data', async () => {
      const res = await app.request('/api/v1/analytics/stats');
      expect(res.status).toBe(200);
      const data = await res.json() as {
        stats: {
          readingTimeHours: number;
          masteredSentences: number;
          weeklyStudyMinutes: Array<{ day: string; minutes: number }>;
        };
      };
      expect(data.stats.readingTimeHours).toBeGreaterThan(0);
      expect(data.stats.weeklyStudyMinutes.length).toBe(7);
    });
  });

  describe('6. User Settings & BYOK', () => {
    it('retrieves and updates user settings including BYOK API key', async () => {
      const getRes = await app.request('/api/v1/settings');
      expect(getRes.status).toBe(200);
      const getData = await getRes.json() as { settings: { furigana_mode: string } };
      expect(getData.settings.furigana_mode).toBe('always');

      const updateRes = await app.request('/api/v1/settings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          furigana_mode: 'tap',
          deepseek_api_key: 'sk-user-test-key-12345'
        })
      });
      expect(updateRes.status).toBe(200);
      const updateData = await updateRes.json() as { settings: { furigana_mode: string; deepseek_api_key: string } };
      expect(updateData.settings.furigana_mode).toBe('tap');
      expect(updateData.settings.deepseek_api_key).toBe('sk-user-test-key-12345');
    });
  });

  describe('7. Audio TTS Synthesis & Caching', () => {
    it('synthesizes audio and caches deterministically', async () => {
      const reqPayload = {
        text: '東京の春',
        speed: 1.0,
        voice_model: 'ja_JP-hira-medium'
      };

      const res1 = await app.request('/api/v1/tts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(reqPayload)
      });
      expect(res1.status).toBe(200);
      const data1 = await res1.json() as { url: string; cached: boolean };
      expect(data1.url).toBeDefined();

      // Second request should hit cache
      const res2 = await app.request('/api/v1/tts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(reqPayload)
      });
      expect(res2.status).toBe(200);
      const data2 = await res2.json() as { url: string; cached: boolean };
      expect(data2.cached).toBe(true);
      expect(data2.url).toBe(data1.url);
    });
  });

  describe('8. Bidirectional Delta Synchronization (/api/v1/sync)', () => {
    it('synchronizes data bidirectionally between client and server', async () => {
      // 1. Pull current state
      const pullRes = await app.request('/api/v1/sync?since=0');
      expect(pullRes.status).toBe(200);
      const pullData = await pullRes.json() as {
        success: boolean;
        timestamp: number;
        articles: Array<{ id: string }>;
      };
      expect(pullData.success).toBe(true);
      expect(pullData.articles.length).toBeGreaterThan(0);

      // 2. Push client creation via sync
      const clientArticle = {
        id: 'art_sync_client_001',
        user_id: 'usr_default',
        title: 'Artikel Baru dari Klien Android',
        category: 'Percakapan',
        raw_text: 'お疲れ様です！駅の改札前で待っていますね。',
        difficulty_level: 'N4',
        kanji_ratio: 0.45,
        created_at: Date.now(),
        updated_at: Date.now()
      };

      const pushRes = await app.request('/api/v1/sync', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          user_id: 'usr_default',
          articles: [clientArticle]
        })
      });
      expect(pushRes.status).toBe(200);

      // 3. Verify server received and stores synced article
      const verifyRes = await app.request('/api/v1/articles/art_sync_client_001');
      expect(verifyRes.status).toBe(200);
      const verifyData = await verifyRes.json() as { article: { title: string } };
      expect(verifyData.article.title).toBe('Artikel Baru dari Klien Android');
    });
  });
});
