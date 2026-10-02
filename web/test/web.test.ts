import { describe, it, expect, beforeEach } from 'vitest';
import {
  AppStore,
  createInitialState,
  buildRubyHtml,
  calculateNextSrsInterval,
  parseTokens,
  parseGrammarPoints,
  getPastelHighlight,
  Token,
  Article,
  Vocabulary
} from '../src/state';

describe('Komorebi Japanese Reader AI - Web App Logic Suite', () => {
  let store: AppStore;

  beforeEach(() => {
    store = new AppStore(createInitialState('http://test-api:8787'));
  });

  describe('1. State Initialization & Navigation', () => {
    it('initializes with default views, articles, and settings', () => {
      const state = store.getState();
      expect(state.currentView).toBe('splash');
      expect(state.articles.length).toBeGreaterThan(0);
      expect(state.vocabularies.length).toBeGreaterThan(0);
      expect(state.settings.target_language).toBe('id-ID');
    });

    it('changes view correctly', () => {
      store.setView('quick-read');
      expect(store.getState().currentView).toBe('quick-read');

      store.setView('koleksi');
      expect(store.getState().currentView).toBe('koleksi');

      store.setView('reader');
      expect(store.getState().currentView).toBe('reader');
    });
  });

  describe('2. Ruby HTML & Furigana Rendering', () => {
    const sampleTokens: Token[] = [
      { surface: '東京', reading: 'とうきょう', romaji: 'toukyou', pos: 'noun' },
      { surface: 'の', reading: 'の', romaji: 'no', pos: 'particle' },
      { surface: '春', reading: 'はる', romaji: 'haru', pos: 'noun' }
    ];

    it('generates ruby elements for kanji when mode is "always"', () => {
      const html = buildRubyHtml(sampleTokens, 'always');
      expect(html).toContain('<ruby');
      expect(html).toContain('<rt');
      expect(html).toContain('とうきょう');
      expect(html).toContain('はる');
    });

    it('omits rt ruby annotations when mode is "off"', () => {
      const html = buildRubyHtml(sampleTokens, 'off');
      expect(html).not.toContain('<rt');
      expect(html).toContain('東京');
    });

    it('applies hidden hover class when mode is "tap"', () => {
      const html = buildRubyHtml(sampleTokens, 'tap');
      expect(html).toContain('opacity-0');
      expect(html).toContain('hover:opacity-100');
    });
  });

  describe('3. Spaced Repetition (SuperMemo-2) Engine', () => {
    it('advances intervals on high quality review ratings', () => {
      // First review (count = 0, quality = 4)
      const rev1 = calculateNextSrsInterval(0, 4);
      expect(rev1.nextMastery).toBe(1); // Dipelajari
      expect(rev1.nextReviewAt).toBeGreaterThan(Date.now());

      // Second review (count = 1, quality = 5)
      const rev2 = calculateNextSrsInterval(1, 5);
      expect(rev2.nextMastery).toBe(1);

      // Third review (count = 2, quality = 5)
      const rev3 = calculateNextSrsInterval(2, 5);
      expect(rev3.nextMastery).toBe(2); // Dikuasai
    });

    it('resets interval when quality rating is failing (< 3)', () => {
      const failedRev = calculateNextSrsInterval(4, 1);
      expect(failedRev.nextMastery).toBe(0); // Reset to Baru
    });
  });

  describe('4. Articles CRUD in AppStore', () => {
    it('adds, updates, and deletes articles', () => {
      const newArticle: Article = {
        id: 'art_test_999',
        user_id: 'usr_default',
        title: 'Buku Cerita Baru',
        category: 'Buku & Artikel',
        raw_text: '昔々ある所におじいさんとおばあさんがいました。',
        difficulty_level: 'N4',
        kanji_ratio: 0.32,
        created_at: Date.now(),
        updated_at: Date.now(),
        sentences: []
      };

      // 1. Add
      store.addArticle(newArticle);
      expect(store.getState().articles.some(a => a.id === 'art_test_999')).toBe(true);

      // 2. Update
      store.updateArticle('art_test_999', { title: 'Judul Diubah' });
      const updated = store.getState().articles.find(a => a.id === 'art_test_999');
      expect(updated?.title).toBe('Judul Diubah');

      // 3. Delete
      store.deleteArticle('art_test_999');
      expect(store.getState().articles.some(a => a.id === 'art_test_999')).toBe(false);
    });
  });

  describe('5. Vocabularies CRUD in AppStore', () => {
    it('adds, updates, and deletes vocabulary items', () => {
      const vocab: Vocabulary = {
        id: 'voc_test_101',
        user_id: 'usr_default',
        kanji: '学校',
        reading: 'がっこう',
        meaning: 'Sekolah',
        part_of_speech: 'noun',
        jlpt_level: 'N5',
        mastery_status: 0,
        review_count: 0,
        created_at: Date.now(),
        updated_at: Date.now()
      };

      // 1. Add
      store.addVocabulary(vocab);
      expect(store.getState().vocabularies.some(v => v.id === 'voc_test_101')).toBe(true);

      // 2. Update
      store.updateVocabulary('voc_test_101', { mastery_status: 2, review_count: 4 });
      const updated = store.getState().vocabularies.find(v => v.id === 'voc_test_101');
      expect(updated?.mastery_status).toBe(2);
      expect(updated?.review_count).toBe(4);

      // 3. Delete
      store.deleteVocabulary('voc_test_101');
      expect(store.getState().vocabularies.some(v => v.id === 'voc_test_101')).toBe(false);
    });
  });

  describe('6. Sentence Inspection & Adaptive Learning Flag', () => {
    it('increments inspection count and auto-flags needs_deep_study when inspection >= 3', () => {
      const article = store.getState().articles[0];
      store.setActiveArticle(article);

      const sentenceId = article.sentences![0].id;

      // Inspection 1
      store.inspectSentence(sentenceId);
      // Inspection 2
      store.inspectSentence(sentenceId);

      const activeArt = store.getState().activeArticle;
      const targetSentence = activeArt?.sentences?.find(s => s.id === sentenceId);

      expect(targetSentence?.inspection_count).toBeGreaterThanOrEqual(3);
      expect(targetSentence?.needs_deep_study).toBe(1);
    });
  });

  describe('7. Utility Helpers', () => {
    it('returns pastel watercolor highlight for indices', () => {
      const c1 = getPastelHighlight(0);
      const c2 = getPastelHighlight(1);
      expect(c1).toBe('#FEF3C7');
      expect(c2).toBe('#D1FAE5');
    });

    it('safely parses JSON tokens and grammar points', () => {
      const validJson = JSON.stringify([{ surface: '雨', reading: 'あめ', pos: 'noun' }]);
      const tokens = parseTokens(validJson);
      expect(tokens.length).toBe(1);
      expect(tokens[0].surface).toBe('雨');

      const invalidTokens = parseTokens('invalid json string');
      expect(invalidTokens).toEqual([]);

      const grammarPoints = parseGrammarPoints('invalid');
      expect(grammarPoints).toEqual([]);
    });
  });
});
