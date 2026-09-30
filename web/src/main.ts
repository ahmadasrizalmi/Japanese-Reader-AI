import {
  AppStore,
  AppState,
  Article,
  Sentence,
  Vocabulary,
  Token,
  GrammarPoint,
  buildRubyHtml,
  parseTokens,
  parseGrammarPoints,
  getPastelHighlight,
  calculateNextSrsInterval
} from './state';

const store = new AppStore();

// Synthesize audio using Web Audio API or fallback speech
function playAudioPronunciation(text: string, speed: number = 1.0): void {
  if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = 'ja-JP';
    utterance.rate = speed;
    window.speechSynthesis.speak(utterance);
  }
}

// Dom helper
function getEl<T extends HTMLElement>(id: string): T | null {
  return document.getElementById(id) as T | null;
}

// Active category filter for Koleksi
let selectedCategory: string = 'Semua';
let searchQuery: string = '';

// Active filter for Vocabulary
let selectedVocabFilter: string = 'Semua';

// -------------------------------------------------------------
// 1. RENDER SPLASH / ONBOARDING
// -------------------------------------------------------------
function renderSplash(state: AppState): string {
  return `
    <div class="relative overflow-hidden rounded-3xl bg-primary text-white p-6 shadow-xl flex flex-col items-center text-center mt-2">
      <!-- Decorative Background Calligraphy -->
      <div class="absolute inset-0 pointer-events-none opacity-10 overflow-hidden select-none">
        <div class="absolute -top-6 -right-6 text-4xl font-bold rotate-12">日本語読解</div>
        <div class="absolute top-1/2 -left-6 text-3xl font-bold -rotate-90">物語・文法</div>
        <div class="absolute -bottom-6 right-6 text-4xl font-bold">読書倶楽部</div>
      </div>

      <div class="relative z-10 flex items-center gap-2 mb-4">
        <div class="w-10 h-10 rounded-full bg-white flex items-center justify-center p-1 shadow-md">
          <img src="assets/logo.png" alt="Logo" class="w-full h-full object-contain">
        </div>
        <div class="flex flex-col text-left">
          <span class="text-[10px] uppercase font-bold tracking-wider text-on-primary-container">Reader AI</span>
          <span class="text-sm font-semibold tracking-tight">日本語リーダー</span>
        </div>
      </div>

      <div class="relative z-10 my-4">
        <div class="w-48 h-48 rounded-3xl bg-white/10 p-2 shadow-2xl backdrop-blur-sm mx-auto overflow-hidden">
          <img src="assets/logo.png" alt="Japanese Reader YOMU" class="w-full h-full object-cover rounded-2xl shadow-inner">
        </div>
      </div>

      <div class="relative z-10 max-w-sm">
        <h2 class="text-2xl font-bold tracking-tight">Read Japanese Naturally.</h2>
        <p class="text-base text-primary-fixed mt-2 font-medium tracking-wide">自然な日本語を、心地よく読む。</p>
        <p class="text-xs text-white/80 mt-2 leading-relaxed">
          Tingkatkan kemampuan membaca bahasa Jepang dengan furigana dinamis, analisis tata bahasa bertenaga AI, dan sistem repetisi berjarak (SRS).
        </p>
      </div>

      <div class="relative z-10 w-full max-w-xs mt-6 flex flex-col gap-2.5">
        <button id="splash-start-btn" class="w-full py-3.5 px-6 rounded-full bg-surface text-crimson-deep font-bold text-sm shadow-xl active:scale-95 transition-all flex items-center justify-center gap-2">
          <span>Mulai Membaca</span>
          <span class="material-symbols-outlined text-[18px]">arrow_forward</span>
        </button>
        <button id="splash-library-btn" class="w-full py-2 px-4 rounded-full text-xs font-semibold text-white/90 hover:text-white transition-colors">
          Buka Arsip Koleksi &rarr;
        </button>
      </div>
    </div>
  `;
}

// -------------------------------------------------------------
// 2. RENDER QUICK READ (INSTANT PARSER)
// -------------------------------------------------------------
let quickReadResult: {
  text: string;
  difficulty_level: string;
  kanji_ratio: number;
  sentences: Array<{
    sequence_order: number;
    original_text: string;
    translated_text: string;
    tokens: Token[];
    grammar_points: GrammarPoint[];
  }>;
} | null = null;

let isAnalyzing: boolean = false;

function renderQuickRead(state: AppState): string {
  const defaultText = quickReadResult ? quickReadResult.text : '今週の土曜日に新しいカフェに行きませんか。';

  return `
    <div class="flex flex-col gap-4">
      <!-- Minimalist Scratchpad -->
      <section class="bg-surface-card rounded-2xl p-4 shadow-sm border border-surface-container flex flex-col gap-3">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-1.5 text-xs font-bold text-primary uppercase tracking-wider">
            <span class="material-symbols-outlined text-[16px]">edit_note</span>
            <span>Scratchpad Teks Jepang</span>
          </div>
          <button id="qr-clear-btn" class="flex items-center gap-1 text-[11px] text-text-muted hover:text-primary transition-colors">
            <span class="material-symbols-outlined text-[14px]">clear</span>
            <span>Bersihkan</span>
          </button>
        </div>

        <textarea id="qr-input" rows="3" class="w-full bg-transparent resize-none border-none outline-none text-lg text-text-primary placeholder:text-text-muted leading-relaxed font-mincho" placeholder="Tempel atau ketik teks Jepang di sini...">${defaultText}</textarea>

        <div class="flex items-center justify-between pt-1 border-t border-surface-container text-xs text-text-muted">
          <span id="qr-char-count">${defaultText.length} Karakter</span>
          <div class="flex items-center gap-2">
            <button id="qr-paste-btn" class="flex items-center gap-1 px-3 py-1.5 rounded-full bg-canvas-secondary text-primary font-semibold text-xs active:scale-95 transition-all">
              <span class="material-symbols-outlined text-[14px]">content_paste</span>
              <span>Tempel</span>
            </button>
            <button id="qr-analyze-btn" class="flex items-center gap-1 px-4 py-1.5 rounded-full bg-primary text-white font-bold text-xs shadow-sm active:scale-95 transition-all ${isAnalyzing ? 'opacity-70 cursor-not-allowed' : ''}">
              <span class="material-symbols-outlined text-[14px]">${isAnalyzing ? 'hourglass_top' : 'auto_fix_high'}</span>
              <span>${isAnalyzing ? 'Menganalisis...' : 'Analisis Teks'}</span>
            </button>
          </div>
        </div>
      </section>

      <!-- Minimal Inspiration Quick Chips -->
      <div class="flex items-center gap-2 overflow-x-auto no-scrollbar py-1">
        <span class="text-[11px] font-bold text-text-muted uppercase tracking-wider shrink-0">Inspirasi:</span>
        <button class="qr-chip shrink-0 px-3 py-1 rounded-full bg-surface-card border border-surface-container text-xs font-medium hover:border-primary text-text-secondary active:scale-95 transition-all" data-text="今週の土曜日に新しいカフェに行きませんか。">
          Kalimat Kafe
        </button>
        <button class="qr-chip shrink-0 px-3 py-1 rounded-full bg-surface-card border border-surface-container text-xs font-medium hover:border-primary text-text-secondary active:scale-95 transition-all" data-text="明日は午後から雨が降る予報です。傘を持っていきましょう。">
          Status Cuaca
        </button>
        <button class="qr-chip shrink-0 px-3 py-1 rounded-full bg-surface-card border border-surface-container text-xs font-medium hover:border-primary text-text-secondary active:scale-95 transition-all" data-text="お疲れ様です！駅の改札前で待っていますね。">
          Pesan Singkat
        </button>
      </div>

      <!-- Instant AI Analysis Card -->
      ${renderQuickReadResult(state)}
    </div>
  `;
}

function renderQuickReadResult(state: AppState): string {
  if (!quickReadResult) {
    // Generate initial sample parsing for immediate delight
    const tokens: Token[] = [
      { surface: '今週', reading: 'こんしゅう', romaji: 'konshuu', pos: 'noun', meaning: 'Minggu ini', jlpt: 'N5' },
      { surface: 'の', reading: 'の', romaji: 'no', pos: 'particle', meaning: 'Partikel kepemilikan', jlpt: 'N5' },
      { surface: '土曜日', reading: 'どようび', romaji: 'doyoubi', pos: 'noun', meaning: 'Hari Sabtu', jlpt: 'N5' },
      { surface: 'に', reading: 'に', romaji: 'ni', pos: 'particle', meaning: 'Partikel waktu', jlpt: 'N5' },
      { surface: '新しい', reading: 'あたらしい', romaji: 'atarashii', pos: 'i-adj', meaning: 'Baru', jlpt: 'N5' },
      { surface: 'カフェ', reading: 'カフェ', romaji: 'kafe', pos: 'noun', meaning: 'Kafe', jlpt: 'N5' },
      { surface: 'に', reading: 'に', romaji: 'ni', pos: 'particle', meaning: 'Partikel arah/tujuan', jlpt: 'N5' },
      { surface: '行きませんか', reading: 'いきませんか', romaji: 'ikimasenka', pos: 'verb', meaning: 'Maukah pergi? (ajakan)', jlpt: 'N5' },
      { surface: '。', reading: '', romaji: '', pos: 'punct', jlpt: '-' }
    ];

    return `
      <section class="bg-surface-card rounded-2xl p-4 shadow-sm border border-surface-container flex flex-col gap-3">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-1.5 px-3 py-1 rounded-full bg-highlight-mint text-primary font-bold text-xs">
            <span class="material-symbols-outlined text-[14px]">verified</span>
            <span>N4 Analisis Instan</span>
          </div>
          <div class="flex items-center gap-1">
            <button id="qr-play-audio-btn" class="w-8 h-8 rounded-full bg-canvas-secondary text-primary flex items-center justify-center hover:bg-crimson-surface transition-colors" title="Dengarkan Audio">
              <span class="material-symbols-outlined text-[18px]">volume_up</span>
            </button>
            <button id="qr-copy-btn" class="w-8 h-8 rounded-full bg-canvas-secondary text-primary flex items-center justify-center hover:bg-crimson-surface transition-colors" title="Salin Teks">
              <span class="material-symbols-outlined text-[18px]">content_copy</span>
            </button>
          </div>
        </div>

        <div class="p-3.5 rounded-xl bg-highlight-yellow/40">
          <p class="text-xl text-text-primary font-mincho leading-[3rem]">
            ${buildRubyHtml(tokens, state.settings.furigana_mode)}
          </p>
        </div>

        <div class="flex flex-col gap-1 px-1">
          <span class="text-[10px] font-bold text-text-muted uppercase tracking-wider">Terjemahan Langsung</span>
          <p class="text-sm font-medium text-text-primary">
            "Maukah kamu pergi ke kafe baru pada hari Sabtu pekan ini?"
          </p>
        </div>

        <!-- Token Pills -->
        <div class="flex flex-col gap-1.5">
          <span class="text-[10px] font-bold text-text-muted uppercase tracking-wider px-1">Token Kata & Partikel</span>
          <div class="flex gap-2 overflow-x-auto pb-1 no-scrollbar font-mincho">
            ${tokens.filter(t => t.pos !== 'punct').map(t => `
              <div class="shrink-0 flex flex-col items-center bg-canvas-secondary px-3 py-1.5 rounded-xl">
                <span class="text-sm font-bold text-text-primary">${t.surface}</span>
                <span class="text-[10px] text-text-secondary">${t.meaning || t.reading}</span>
              </div>
            `).join('')}
          </div>
        </div>

        <button id="qr-save-to-collection-btn" class="w-full flex items-center justify-center gap-2 py-3 rounded-full bg-crimson-surface text-primary font-bold text-xs active:scale-95 transition-all mt-1">
          <span class="material-symbols-outlined text-[18px]">bookmark_add</span>
          <span>Simpan ke Koleksi Bacaan</span>
        </button>
      </section>
    `;
  }

  // Display actual result
  const s0 = quickReadResult.sentences[0];
  const tokens = s0 ? s0.tokens : [];

  return `
    <section class="bg-surface-card rounded-2xl p-4 shadow-sm border border-surface-container flex flex-col gap-3">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-1.5 px-3 py-1 rounded-full bg-highlight-mint text-primary font-bold text-xs">
          <span class="material-symbols-outlined text-[14px]">verified</span>
          <span>${quickReadResult.difficulty_level} Analisis Instan</span>
        </div>
        <div class="flex items-center gap-1">
          <button id="qr-play-audio-btn" class="w-8 h-8 rounded-full bg-canvas-secondary text-primary flex items-center justify-center hover:bg-crimson-surface transition-colors" title="Dengarkan Audio">
            <span class="material-symbols-outlined text-[18px]">volume_up</span>
          </button>
          <button id="qr-copy-btn" class="w-8 h-8 rounded-full bg-canvas-secondary text-primary flex items-center justify-center hover:bg-crimson-surface transition-colors" title="Salin Teks">
            <span class="material-symbols-outlined text-[18px]">content_copy</span>
          </button>
        </div>
      </div>

      <div class="p-3.5 rounded-xl bg-highlight-yellow/40">
        <p class="text-xl text-text-primary font-mincho leading-[3rem]">
          ${buildRubyHtml(tokens, state.settings.furigana_mode)}
        </p>
      </div>

      <div class="flex flex-col gap-1 px-1">
        <span class="text-[10px] font-bold text-text-muted uppercase tracking-wider">Terjemahan Langsung</span>
        <p class="text-sm font-medium text-text-primary">
          "${s0 ? s0.translated_text : ''}"
        </p>
      </div>

      <!-- Token Pills -->
      <div class="flex flex-col gap-1.5">
        <span class="text-[10px] font-bold text-text-muted uppercase tracking-wider px-1">Token Kata & Partikel</span>
        <div class="flex gap-2 overflow-x-auto pb-1 no-scrollbar font-mincho">
          ${tokens.filter(t => t.pos !== 'punct').map(t => `
            <div class="shrink-0 flex flex-col items-center bg-canvas-secondary px-3 py-1.5 rounded-xl">
              <span class="text-sm font-bold text-text-primary">${t.surface}</span>
              <span class="text-[10px] text-text-secondary">${t.meaning || t.reading}</span>
            </div>
          `).join('')}
        </div>
      </div>

      <button id="qr-save-to-collection-btn" class="w-full flex items-center justify-center gap-2 py-3 rounded-full bg-crimson-surface text-primary font-bold text-xs active:scale-95 transition-all mt-1">
        <span class="material-symbols-outlined text-[18px]">bookmark_add</span>
        <span>Simpan ke Koleksi Bacaan</span>
      </button>
    </section>
  `;
}

// -------------------------------------------------------------
// 3. RENDER KOLEKSI (ARTICLES LIBRARY)
// -------------------------------------------------------------
function renderKoleksi(state: AppState): string {
  const categories = ['Semua', 'Percakapan', 'Buku & Artikel', 'Lirik Lagu', 'Menu & Tempat'];

  let filtered = state.articles;
  if (selectedCategory !== 'Semua') {
    filtered = filtered.filter(a => a.category.toLowerCase() === selectedCategory.toLowerCase());
  }
  if (searchQuery.trim() !== '') {
    const q = searchQuery.toLowerCase();
    filtered = filtered.filter(a => a.title.toLowerCase().includes(q) || a.raw_text.toLowerCase().includes(q));
  }

  return `
    <div class="flex flex-col gap-4">
      <!-- Action Deck -->
      <div class="flex items-center gap-2.5">
        <button id="btn-add-article-modal" class="flex-1 flex items-center justify-center gap-2 py-3 px-4 rounded-full bg-primary text-white shadow-sm font-bold text-xs active:scale-95 transition-all">
          <span class="material-symbols-outlined text-[18px]">add</span>
          <span>Tambah Bacaan Baru</span>
        </button>
        <button id="btn-paste-clipboard" class="flex items-center justify-center gap-2 py-3 px-4 rounded-full bg-crimson-surface text-primary shadow-sm font-bold text-xs active:scale-95 transition-all">
          <span class="material-symbols-outlined text-[18px]">content_paste</span>
          <span>Tempel Teks</span>
        </button>
      </div>

      <!-- Search Bar -->
      <div class="relative">
        <span class="material-symbols-outlined absolute left-3.5 top-1/2 -translate-y-1/2 text-text-muted text-[18px]">search</span>
        <input id="article-search-input" type="text" placeholder="Cari judul atau isi bacaan..." value="${searchQuery}" class="w-full pl-10 pr-4 py-2.5 bg-surface-card border border-surface-container rounded-full text-xs text-text-primary focus:outline-none focus:border-primary transition-colors">
      </div>

      <!-- Category Filter Pills -->
      <div class="flex items-center gap-2 overflow-x-auto no-scrollbar py-0.5">
        ${categories.map(cat => {
          const isActive = selectedCategory === cat;
          const count = cat === 'Semua' ? state.articles.length : state.articles.filter(a => a.category === cat).length;
          return `
            <button class="category-pill shrink-0 flex items-center gap-1.5 py-1.5 px-3.5 rounded-full text-xs font-semibold shadow-sm transition-all ${isActive ? 'bg-primary text-white' : 'bg-surface-card border border-surface-container text-text-secondary'}" data-category="${cat}">
              <span>${cat}</span>
              <span class="px-1.5 py-0.2 rounded-full text-[10px] ${isActive ? 'bg-white/20 text-white' : 'bg-canvas-secondary text-text-muted'}">${count}</span>
            </button>
          `;
        }).join('')}
      </div>

      <!-- Section Title -->
      <div class="flex items-center justify-between pt-1">
        <div class="flex items-center gap-2">
          <span class="font-bold text-base text-text-primary">Arsip Catatan</span>
          <span class="text-[10px] px-2 py-0.5 rounded-full bg-crimson-surface text-primary font-bold uppercase tracking-wider">Tersimpan</span>
        </div>
        <span class="text-[11px] text-text-muted">${filtered.length} bacaan</span>
      </div>

      <!-- Articles Card Grid -->
      <div class="grid grid-cols-1 md:grid-cols-2 gap-3.5">
        ${filtered.length === 0 ? `
          <div class="col-span-full py-12 flex flex-col items-center justify-center text-center text-text-muted">
            <span class="material-symbols-outlined text-4xl mb-2 text-primary/40">auto_stories</span>
            <p class="text-sm font-semibold">Belum ada bacaan di kategori ini</p>
            <p class="text-xs text-text-muted mt-1">Klik tombol "Tambah Bacaan Baru" untuk memulai.</p>
          </div>
        ` : filtered.map(art => {
          const s0 = art.sentences && art.sentences.length > 0 ? art.sentences[0] : null;
          const tokens = s0 ? parseTokens(s0.furigana_payload) : [];
          return `
            <div class="flex flex-col justify-between p-4 rounded-2xl bg-surface-card border border-surface-container shadow-sm hover:shadow-md transition-all">
              <div>
                <div class="flex items-center justify-between mb-2">
                  <span class="text-[10px] font-bold px-2 py-0.5 rounded-full bg-highlight-yellow text-primary">JLPT ${art.difficulty_level}</span>
                  <div class="flex items-center gap-1 text-[11px] text-text-muted">
                    <span class="material-symbols-outlined text-[13px]">timer</span>
                    <span>${art.raw_text.length} huruf</span>
                  </div>
                </div>

                <h3 class="font-bold text-base text-text-primary tracking-tight mb-2 truncate">${art.title}</h3>

                <div class="p-2.5 rounded-xl bg-canvas-secondary/70 mb-3 font-mincho text-sm leading-relaxed line-clamp-2">
                  ${s0 ? buildRubyHtml(tokens, state.settings.furigana_mode) : art.raw_text}
                </div>
              </div>

              <div class="flex items-center justify-between pt-2 border-t border-surface-container/60">
                <span class="text-[11px] text-text-muted">${art.category}</span>
                <div class="flex items-center gap-1.5">
                  <button class="btn-delete-article p-1.5 rounded-full text-text-muted hover:text-error hover:bg-error-container/30 transition-colors" data-id="${art.id}" title="Hapus Bacaan">
                    <span class="material-symbols-outlined text-[16px]">delete</span>
                  </button>
                  <button class="btn-open-reader flex items-center gap-1 py-1 px-3.5 rounded-full bg-crimson-surface text-primary text-xs font-bold active:scale-95 transition-transform" data-id="${art.id}">
                    <span>Buka</span>
                    <span class="material-symbols-outlined text-[14px]">arrow_forward</span>
                  </button>
                </div>
              </div>
            </div>
          `;
        }).join('')}
      </div>
    </div>
  `;
}

// -------------------------------------------------------------
// 4. RENDER READER STUDIO
// -------------------------------------------------------------
function renderReader(state: AppState): string {
  const article = state.activeArticle;
  if (!article) {
    return `
      <div class="py-12 flex flex-col items-center justify-center text-center text-text-muted">
        <span class="material-symbols-outlined text-4xl mb-2 text-primary">menu_book</span>
        <p class="text-sm font-semibold">Tidak ada artikel yang sedang dibuka.</p>
        <button id="reader-back-to-library" class="mt-3 px-4 py-2 rounded-full bg-primary text-white text-xs font-bold">
          Buka Koleksi
        </button>
      </div>
    `;
  }

  const sentences = article.sentences || [];

  return `
    <div class="flex flex-col gap-3">
      <!-- Sub-header Control Bar -->
      <div class="flex items-center justify-between py-1 border-b border-surface-container">
        <div class="flex items-center gap-2 min-w-0">
          <button id="reader-back-btn" class="w-8 h-8 rounded-full hover:bg-canvas-secondary flex items-center justify-center text-text-secondary">
            <span class="material-symbols-outlined text-[18px]">arrow_back</span>
          </button>
          <span class="px-2.5 py-0.5 rounded-full bg-crimson-surface text-primary text-[10px] font-bold">JLPT ${article.difficulty_level}</span>
          <span class="text-xs font-bold text-text-primary truncate">${article.title}</span>
        </div>

        <!-- Furigana Switcher Pill -->
        <div class="inline-flex p-1 bg-surface-container rounded-full items-center shrink-0">
          <button class="furi-mode-btn px-2.5 py-0.5 rounded-full text-[10px] font-bold ${state.settings.furigana_mode === 'always' ? 'bg-white text-primary shadow-sm' : 'text-text-muted'}" data-mode="always">ON</button>
          <button class="furi-mode-btn px-2.5 py-0.5 rounded-full text-[10px] font-bold ${state.settings.furigana_mode === 'tap' ? 'bg-white text-primary shadow-sm' : 'text-text-muted'}" data-mode="tap">TAP</button>
          <button class="furi-mode-btn px-2.5 py-0.5 rounded-full text-[10px] font-bold ${state.settings.furigana_mode === 'off' ? 'bg-white text-primary shadow-sm' : 'text-text-muted'}" data-mode="off">OFF</button>
        </div>
      </div>

      <!-- Reading Canvas Card -->
      <div class="bg-surface-card rounded-2xl p-5 shadow-sm border border-surface-container flex flex-col gap-4">
        <div class="flex items-center justify-between text-xs text-text-muted pb-1 border-b border-surface-container">
          <span class="flex items-center gap-1">
            <span class="material-symbols-outlined text-[14px]">touch_app</span>
            <span>Ketuk kalimat untuk analisis tatabahasa & audio</span>
          </span>
          <span>${sentences.length} Kalimat</span>
        </div>

        <!-- Sentences list -->
        <div class="flex flex-col gap-4 ${state.settings.kanji_font_style === 'gothic' ? 'font-gothic' : 'font-mincho'}">
          ${sentences.map((sent, idx) => {
            const tokens = parseTokens(sent.furigana_payload);
            const isSelected = state.activeSentenceIndex === idx;
            const pastelBg = isSelected ? getPastelHighlight(idx) : '';

            return `
              <div class="reader-sentence-row relative group cursor-pointer p-3 rounded-2xl transition-all ${isSelected ? 'shadow-md ring-2 ring-primary/40' : 'hover:bg-canvas-secondary/50'}" style="background-color: ${isSelected ? pastelBg : 'transparent'};" data-index="${idx}" data-sent-id="${sent.id}">
                ${isSelected ? `
                  <!-- Floating Selection Toolbar -->
                  <div class="absolute -top-10 left-4 z-30 flex items-center bg-text-primary text-white rounded-full px-2.5 py-1 shadow-lg gap-2 text-[10px] font-bold">
                    <button class="btn-inspect-action flex items-center gap-1 hover:text-highlight-yellow active:scale-95" data-action="translate">
                      <span class="material-symbols-outlined text-[14px]">translate</span>
                      <span>TERJEMAHKAN</span>
                    </button>
                    <div class="w-px h-3 bg-white/20"></div>
                    <button class="btn-inspect-action flex items-center gap-1 hover:text-highlight-yellow active:scale-95" data-action="audio">
                      <span class="material-symbols-outlined text-[14px]">volume_up</span>
                      <span>AUDIO</span>
                    </button>
                    <div class="w-px h-3 bg-white/20"></div>
                    <button class="btn-inspect-action flex items-center gap-1 hover:text-highlight-yellow active:scale-95" data-action="save">
                      <span class="material-symbols-outlined text-[14px]">bookmark</span>
                      <span>SIMPAN</span>
                    </button>
                  </div>
                ` : ''}

                <div class="text-lg text-text-primary leading-[3.2rem]">
                  ${buildRubyHtml(tokens, state.settings.furigana_mode)}
                </div>

                ${sent.needs_deep_study ? `
                  <div class="mt-1 flex items-center gap-1 text-[10px] font-bold text-secondary">
                    <span class="material-symbols-outlined text-[12px]">flag</span>
                    <span>Perlu pengulangan intensif (Inspeksi: ${sent.inspection_count}x)</span>
                  </div>
                ` : ''}
              </div>
            `;
          }).join('')}
        </div>
      </div>
    </div>
  `;
}

// -------------------------------------------------------------
// 5. RENDER VOCABULARY SRS DECK
// -------------------------------------------------------------
function renderVocab(state: AppState): string {
  let list = state.vocabularies;
  if (selectedVocabFilter === 'Baru') {
    list = list.filter(v => v.mastery_status === 0);
  } else if (selectedVocabFilter === 'Dipelajari') {
    list = list.filter(v => v.mastery_status === 1);
  } else if (selectedVocabFilter === 'Dikuasai') {
    list = list.filter(v => v.mastery_status === 2);
  }

  return `
    <div class="flex flex-col gap-4">
      <div class="flex items-center justify-between">
        <div>
          <span class="text-[10px] font-bold uppercase tracking-wider text-text-muted">Koleksi Kata & Flashcard</span>
          <h2 class="text-lg font-bold text-text-primary tracking-tight">Dek Spaced Repetition (SRS)</h2>
        </div>
        <span class="text-xs px-2.5 py-1 rounded-full bg-crimson-surface text-primary font-bold">${state.vocabularies.length} Kata</span>
      </div>

      <!-- Mastery Filters -->
      <div class="flex items-center gap-2 overflow-x-auto no-scrollbar py-0.5">
        ${['Semua', 'Baru', 'Dipelajari', 'Dikuasai'].map(f => {
          const isActive = selectedVocabFilter === f;
          return `
            <button class="vocab-filter-pill px-3.5 py-1.5 rounded-full text-xs font-semibold shadow-sm transition-all ${isActive ? 'bg-primary text-white' : 'bg-surface-card border border-surface-container text-text-secondary'}" data-filter="${f}">
              ${f}
            </button>
          `;
        }).join('')}
      </div>

      <!-- Vocab List -->
      <div class="flex flex-col gap-3">
        ${list.length === 0 ? `
          <div class="py-12 flex flex-col items-center justify-center text-center text-text-muted bg-surface-card rounded-2xl border border-surface-container p-6">
            <span class="material-symbols-outlined text-4xl mb-2 text-primary/40">style</span>
            <p class="text-sm font-semibold">Tidak ada kosakata di kategori ini.</p>
            <p class="text-xs text-text-muted mt-1">Buka Reader Studio dan simpan kata dari bacaan.</p>
          </div>
        ` : list.map(v => {
          const masteryLabel = v.mastery_status === 0 ? 'Baru' : v.mastery_status === 1 ? 'Dipelajari' : 'Dikuasai';
          const masteryBg = v.mastery_status === 0 ? 'bg-highlight-blue text-text-primary' : v.mastery_status === 1 ? 'bg-highlight-yellow text-primary' : 'bg-highlight-mint text-primary';

          return `
            <div class="p-4 rounded-2xl bg-surface-card border border-surface-container shadow-sm flex items-center justify-between gap-3">
              <div class="flex items-center gap-3 min-w-0">
                <button class="btn-play-vocab-audio w-10 h-10 rounded-xl bg-canvas-secondary flex items-center justify-center text-primary shrink-0 hover:bg-crimson-surface transition-colors" data-kanji="${v.kanji}">
                  <span class="material-symbols-outlined text-[20px]">volume_up</span>
                </button>
                <div class="flex flex-col min-w-0">
                  <div class="flex items-baseline gap-2">
                    <span class="font-mincho font-bold text-lg text-text-primary">${v.kanji}</span>
                    <span class="text-xs text-text-muted">${v.reading}</span>
                  </div>
                  <span class="text-xs text-text-secondary truncate">${v.meaning}</span>
                </div>
              </div>

              <div class="flex items-center gap-2 shrink-0">
                <div class="flex flex-col items-end">
                  <span class="text-[10px] font-bold px-2 py-0.5 rounded-full ${masteryBg}">${masteryLabel}</span>
                  <span class="text-[10px] text-text-muted mt-0.5">${v.review_count}x tinjauan</span>
                </div>
                <button class="btn-review-vocab p-1.5 rounded-full text-text-muted hover:text-primary hover:bg-canvas-secondary transition-colors" data-id="${v.id}" title="Review Kartu">
                  <span class="material-symbols-outlined text-[18px]">rate_review</span>
                </button>
                <button class="btn-delete-vocab p-1.5 rounded-full text-text-muted hover:text-error hover:bg-error-container/30 transition-colors" data-id="${v.id}" title="Hapus Kata">
                  <span class="material-symbols-outlined text-[18px]">delete</span>
                </button>
              </div>
            </div>
          `;
        }).join('')}
      </div>
    </div>
  `;
}

// -------------------------------------------------------------
// 6. RENDER ANALYTICS
// -------------------------------------------------------------
function renderAnalytics(state: AppState): string {
  const stats = state.stats;

  return `
    <div class="flex flex-col gap-4">
      <div class="flex items-center justify-between">
        <div>
          <span class="text-[10px] font-bold uppercase tracking-wider text-text-muted">Statistik Belajar</span>
          <h2 class="text-lg font-bold text-text-primary tracking-tight">Progres & Wawasan</h2>
        </div>
        <div class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-crimson-surface text-primary text-xs font-semibold">
          <span class="material-symbols-outlined text-[14px]">schedule</span>
          <span>7 Hari Terakhir</span>
        </div>
      </div>

      <!-- Ringkasan Performa: 2 Kartu Metrik -->
      <div class="grid grid-cols-2 gap-3">
        <div class="bg-surface-card p-4 rounded-2xl border border-surface-container shadow-sm flex flex-col justify-between">
          <div class="flex items-center justify-between mb-2">
            <span class="text-xs text-text-secondary font-medium">Waktu Baca</span>
            <div class="w-7 h-7 rounded-full bg-canvas-secondary flex items-center justify-center text-primary">
              <span class="material-symbols-outlined text-[16px]">timer</span>
            </div>
          </div>
          <div>
            <div class="text-2xl font-bold text-text-primary tracking-tight leading-none mb-1">${stats.readingTimeHours} <span class="text-xs font-normal text-text-secondary">Jam</span></div>
            <div class="inline-flex items-center gap-1 text-[11px] text-primary font-semibold">
              <span class="material-symbols-outlined text-[13px]">trending_up</span>
              <span>+18% mgg ini</span>
            </div>
          </div>
        </div>

        <div class="bg-surface-card p-4 rounded-2xl border border-surface-container shadow-sm flex flex-col justify-between">
          <div class="flex items-center justify-between mb-2">
            <span class="text-xs text-text-secondary font-medium">Dikuasai</span>
            <div class="w-7 h-7 rounded-full bg-canvas-secondary flex items-center justify-center text-secondary">
              <span class="material-symbols-outlined text-[16px]">check_circle</span>
            </div>
          </div>
          <div>
            <div class="text-2xl font-bold text-text-primary tracking-tight leading-none mb-1">${stats.masteredSentences} <span class="text-xs font-normal text-text-secondary">Kalimat</span></div>
            <div class="inline-flex items-center gap-1 text-[11px] text-secondary font-semibold">
              <span class="material-symbols-outlined text-[13px]">arrow_upward</span>
              <span>12 selesai hari ini</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Visualisasi Grafik Intensitas Mingguan -->
      <div class="bg-surface-card p-4 rounded-2xl border border-surface-container shadow-sm flex flex-col gap-3">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <div class="w-6 h-6 rounded-full bg-crimson-surface flex items-center justify-center text-primary">
              <span class="material-symbols-outlined text-[14px]">bar_chart</span>
            </div>
            <span class="text-sm font-bold text-text-primary">Ritme Mingguan</span>
          </div>
          <span class="text-[11px] text-text-muted">Target: 30 mnt/hari</span>
        </div>

        <!-- Weekly Bar Chart -->
        <div class="h-28 flex items-end justify-between gap-2 px-1 pt-2">
          ${stats.weeklyStudyMinutes.map(item => {
            const heightPercent = Math.min(100, Math.round((item.minutes / 65) * 100));
            const isPeak = item.minutes >= 60;
            return `
              <div class="flex-1 flex flex-col items-center gap-1">
                <span class="text-[10px] text-text-muted">${item.minutes}m</span>
                <div class="w-full bg-canvas-secondary rounded-t-full h-20 flex items-end">
                  <div class="w-full rounded-t-full transition-all duration-300 ${isPeak ? 'bg-primary shadow-sm' : 'bg-crimson-deep/40'}" style="height: ${heightPercent}%;"></div>
                </div>
                <span class="text-[10px] font-bold text-text-secondary">${item.day}</span>
              </div>
            `;
          }).join('')}
        </div>
      </div>

      <!-- JLPT Distribution -->
      <div class="bg-surface-card p-4 rounded-2xl border border-surface-container shadow-sm flex flex-col gap-2.5">
        <span class="text-sm font-bold text-text-primary">Distribusi Level JLPT Kosakata</span>
        <div class="grid grid-cols-5 gap-2 text-center">
          ${Object.entries(stats.jlptDistribution).map(([level, count]) => `
            <div class="p-2.5 rounded-xl bg-canvas-secondary flex flex-col items-center">
              <span class="text-xs font-bold text-primary">${level}</span>
              <span class="text-sm font-bold text-text-primary">${count}</span>
            </div>
          `).join('')}
        </div>
      </div>
    </div>
  `;
}

// -------------------------------------------------------------
// 7. RENDER SETTINGS & BYOK
// -------------------------------------------------------------
function renderSettings(state: AppState): string {
  const s = state.settings;

  return `
    <div class="flex flex-col gap-4">
      <!-- Profile Banner -->
      <div class="rounded-3xl bg-surface-card p-4 border border-surface-container shadow-sm flex items-center gap-3">
        <img src="assets/logo.png" alt="Profile" class="w-14 h-14 rounded-full object-cover p-0.5 bg-crimson-surface shadow-sm shrink-0">
        <div class="flex flex-col min-w-0 flex-1">
          <h3 class="font-bold text-base text-text-primary truncate">Aoi Tanaka (田中 葵)</h3>
          <p class="text-xs text-text-secondary mt-0.5 flex items-center gap-1.5 flex-wrap">
            <span class="bg-crimson-surface text-primary font-bold px-2 py-0.5 rounded-full text-[10px]">N3 CHALLENGER</span>
            <span class="text-secondary font-medium text-[11px]">🔥 42 Hari Beruntun</span>
          </p>
        </div>
      </div>

      <!-- SECTION 1: Reading Preferences -->
      <div class="bg-surface-card rounded-2xl p-4 border border-surface-container shadow-sm flex flex-col gap-3">
        <div class="flex items-center gap-2 text-xs font-bold text-primary uppercase tracking-wider">
          <span class="material-symbols-outlined text-[16px]">menu_book</span>
          <span>Preferensi Tampilan Furigana</span>
        </div>

        <div class="grid grid-cols-3 gap-2" id="settings-furigana-picker">
          <button class="furi-setting-btn p-2 rounded-xl text-center text-xs font-bold transition-all ${s.furigana_mode === 'always' ? 'bg-primary text-white shadow-sm' : 'bg-canvas-secondary text-text-secondary'}" data-mode="always">
            Selalu Tampil
          </button>
          <button class="furi-setting-btn p-2 rounded-xl text-center text-xs font-bold transition-all ${s.furigana_mode === 'tap' ? 'bg-primary text-white shadow-sm' : 'bg-canvas-secondary text-text-secondary'}" data-mode="tap">
            Ketuk Tampil
          </button>
          <button class="furi-setting-btn p-2 rounded-xl text-center text-xs font-bold transition-all ${s.furigana_mode === 'off' ? 'bg-primary text-white shadow-sm' : 'bg-canvas-secondary text-text-secondary'}" data-mode="off">
            Nonaktif
          </button>
        </div>

        <!-- Font Style -->
        <div class="flex flex-col gap-1.5 pt-1">
          <label class="text-xs font-semibold text-text-primary">Gaya Huruf Kanji (Font Style)</label>
          <div class="grid grid-cols-2 gap-2" id="settings-font-picker">
            <button class="font-setting-btn p-2.5 rounded-xl border text-left flex items-center justify-between text-xs transition-all ${s.kanji_font_style === 'mincho' ? 'border-primary bg-crimson-surface text-primary font-bold' : 'border-surface-container bg-canvas-secondary text-text-secondary'}" data-font="mincho">
              <span>明朝体 (Mincho Serif)</span>
              <span class="material-symbols-outlined text-[16px]">${s.kanji_font_style === 'mincho' ? 'check' : ''}</span>
            </button>
            <button class="font-setting-btn p-2.5 rounded-xl border text-left flex items-center justify-between text-xs transition-all ${s.kanji_font_style === 'gothic' ? 'border-primary bg-crimson-surface text-primary font-bold' : 'border-surface-container bg-canvas-secondary text-text-secondary'}" data-font="gothic">
              <span>ゴシック (Gothic Sans)</span>
              <span class="material-symbols-outlined text-[16px]">${s.kanji_font_style === 'gothic' ? 'check' : ''}</span>
            </button>
          </div>
        </div>
      </div>

      <!-- SECTION 2: Audio & TTS -->
      <div class="bg-surface-card rounded-2xl p-4 border border-surface-container shadow-sm flex flex-col gap-3">
        <div class="flex items-center gap-2 text-xs font-bold text-primary uppercase tracking-wider">
          <span class="material-symbols-outlined text-[16px]">record_voice_over</span>
          <span>Audio & Kecepatan Suara</span>
        </div>

        <div class="flex items-center justify-between">
          <label class="text-xs font-medium text-text-primary">Kecepatan Pelafalan (Speed)</label>
          <div class="flex items-center gap-2">
            <span id="speed-indicator" class="text-xs font-bold text-primary bg-crimson-surface px-2 py-0.5 rounded-full">${s.tts_speed.toFixed(1)}x</span>
            <button id="settings-preview-audio" class="w-6 h-6 rounded-full bg-crimson-surface text-primary flex items-center justify-center hover:bg-primary hover:text-white transition-colors">
              <span class="material-symbols-outlined text-[14px]">volume_up</span>
            </button>
          </div>
        </div>

        <input id="settings-speed-range" type="range" min="0.5" max="1.5" step="0.1" value="${s.tts_speed}" class="w-full accent-primary h-1.5 bg-canvas-secondary rounded-lg appearance-none cursor-pointer">
      </div>

      <!-- SECTION 3: DeepSeek BYOK Key -->
      <div class="bg-surface-card rounded-2xl p-4 border border-surface-container shadow-sm flex flex-col gap-3">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2 text-xs font-bold text-primary uppercase tracking-wider">
            <span class="material-symbols-outlined text-[16px]">key</span>
            <span>API DeepSeek AI (BYOK)</span>
          </div>
          <span class="text-[10px] font-bold px-2 py-0.5 rounded-full bg-highlight-mint text-primary">ENCRYPTED</span>
        </div>

        <div class="flex flex-col gap-1.5">
          <label class="text-xs text-text-secondary">Kunci API DeepSeek V3 (Opsional - Kunci Pribadi)</label>
          <div class="relative flex items-center">
            <input id="deepseek-key-input" type="password" placeholder="sk-deepseek-..." value="${s.deepseek_api_key || ''}" class="w-full bg-canvas-secondary text-text-primary text-xs rounded-xl py-2.5 px-3 pr-20 focus:outline-none focus:ring-1 focus:ring-primary font-mono">
            <button id="btn-save-key" class="absolute right-1.5 px-3 py-1 bg-primary text-white rounded-lg text-xs font-bold active:scale-95 transition-all">
              Simpan
            </button>
          </div>
          <p class="text-[11px] text-text-muted">
            Jika dikosongkan, sistem otomatis menggunakan mesin morfologi deterministik bawaan yang cepat dan akurat.
          </p>
        </div>
      </div>

      <!-- SECTION 4: Cloud Sync -->
      <div class="bg-surface-card rounded-2xl p-4 border border-surface-container shadow-sm text-center flex flex-col items-center gap-2">
        <div class="flex items-center gap-1.5 text-xs text-text-muted">
          <span class="material-symbols-outlined text-[16px] text-primary">cloud_sync</span>
          <span>Versi 1.0.0 (Cloudflare Edge Connected)</span>
        </div>
        <p class="text-[11px] text-text-muted">
          Status: <strong class="text-primary">${state.syncStatus.toUpperCase()}</strong> • Terakhir disinkron: ${new Date(state.lastSyncTimestamp).toLocaleTimeString()}
        </p>
        <button id="btn-manual-sync" class="w-full py-2.5 rounded-full bg-crimson-surface text-primary font-bold text-xs hover:bg-primary hover:text-white transition-all active:scale-95 mt-1">
          Sinkronkan Sekarang
        </button>
      </div>
    </div>
  `;
}

// -------------------------------------------------------------
// MODALS & BOTTOM SHEETS
// -------------------------------------------------------------
function showModal(contentHtml: string): void {
  const container = getEl<HTMLDivElement>('modal-container');
  if (!container) return;

  container.innerHTML = `
    <div class="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4">
      <div class="modal-backdrop fixed inset-0 bg-black/40 backdrop-blur-xs transition-opacity"></div>
      <div class="relative w-full max-w-lg bg-surface-card rounded-t-3xl sm:rounded-3xl p-5 shadow-2xl z-10 max-h-[90vh] overflow-y-auto border border-surface-container">
        ${contentHtml}
      </div>
    </div>
  `;
  container.classList.remove('hidden');

  const backdrop = container.querySelector('.modal-backdrop');
  backdrop?.addEventListener('click', hideModal);
}

function hideModal(): void {
  const container = getEl<HTMLDivElement>('modal-container');
  if (!container) return;
  container.classList.add('hidden');
  container.innerHTML = '';
}

function showSentenceBottomSheet(sentence: Sentence): void {
  const tokens = parseTokens(sentence.furigana_payload);
  const grammarPoints = parseGrammarPoints(sentence.grammar_analysis);

  const mainKanjiToken = tokens.find(t => t.pos === 'verb' || t.pos === 'noun' || t.pos === 'i-adj') || tokens[0];

  const html = `
    <div class="flex flex-col gap-3.5">
      <!-- Drag handle -->
      <div class="w-10 h-1 rounded-full bg-surface-container mx-auto"></div>

      <!-- Header Expression -->
      <div class="flex items-start justify-between gap-3">
        <div class="flex flex-col min-w-0">
          <div class="flex items-center gap-2">
            <span class="font-mincho text-xl font-bold text-text-primary">${sentence.original_text}</span>
          </div>
          <span class="text-xs text-text-muted mt-0.5">${tokens.map(t => t.romaji).filter(Boolean).join(' ')}</span>
        </div>
        <button id="modal-close-btn" class="w-8 h-8 rounded-full bg-canvas-secondary hover:bg-crimson-surface flex items-center justify-center text-text-secondary shrink-0">
          <span class="material-symbols-outlined text-[18px]">close</span>
        </button>
      </div>

      <!-- Translation Tile -->
      <div class="p-3.5 rounded-xl bg-canvas-secondary flex flex-col gap-1">
        <span class="text-[10px] font-bold text-text-muted uppercase tracking-wider">ARTI / TERJEMAHAN</span>
        <p class="text-sm font-semibold text-text-primary leading-normal">
          ${sentence.translated_text}
        </p>
      </div>

      <!-- Grammar Breakdown -->
      ${grammarPoints.length > 0 ? `
        <div class="flex flex-col gap-1.5">
          <span class="text-[10px] font-bold text-primary uppercase tracking-wider">Analisis Tata Bahasa</span>
          ${grammarPoints.map(g => `
            <div class="p-3 rounded-xl bg-crimson-surface/50 border border-crimson-surface flex flex-col gap-1">
              <span class="text-xs font-bold text-primary">${g.pattern}</span>
              <p class="text-xs text-text-secondary leading-relaxed">${g.explanation}</p>
            </div>
          `).join('')}
        </div>
      ` : ''}

      <!-- Kanji Deconstruction -->
      ${mainKanjiToken ? `
        <div class="flex items-center gap-3 p-3 rounded-xl bg-canvas-secondary">
          <div class="w-12 h-12 rounded-xl bg-surface-card flex items-center justify-center text-primary font-mincho text-2xl font-bold shadow-sm shrink-0">
            ${mainKanjiToken.surface}
          </div>
          <div class="flex flex-col min-w-0">
            <div class="flex items-center gap-2">
              <span class="text-xs font-bold text-text-primary">Kanji: ${mainKanjiToken.surface}</span>
              <span class="text-[10px] px-1.5 py-0.2 rounded-full bg-white text-primary font-bold">${mainKanjiToken.jlpt || 'N4'}</span>
            </div>
            <span class="text-xs text-text-secondary truncate">Bacaan: ${mainKanjiToken.reading} • ${mainKanjiToken.meaning || 'Istilah kunci'}</span>
          </div>
        </div>
      ` : ''}

      <!-- Actions -->
      <div class="grid grid-cols-2 gap-2 pt-1">
        <button id="bs-add-flashcard-btn" class="w-full py-2.5 rounded-full bg-primary text-white font-bold text-xs flex items-center justify-center gap-1.5 active:scale-95 transition-all shadow-sm">
          <span class="material-symbols-outlined text-[16px]">bookmark_add</span>
          <span>+ Flashcard</span>
        </button>
        <button id="bs-play-audio-btn" class="w-full py-2.5 rounded-full bg-canvas-secondary text-primary font-bold text-xs flex items-center justify-center gap-1.5 active:scale-95 transition-all">
          <span class="material-symbols-outlined text-[16px]">volume_up</span>
          <span>Putar Audio</span>
        </button>
      </div>
    </div>
  `;

  showModal(html);

  getEl('modal-close-btn')?.addEventListener('click', hideModal);

  getEl('bs-play-audio-btn')?.addEventListener('click', () => {
    playAudioPronunciation(sentence.original_text, store.getState().settings.tts_speed);
  });

  getEl('bs-add-flashcard-btn')?.addEventListener('click', () => {
    if (mainKanjiToken) {
      const newVocab: Vocabulary = {
        id: `voc_${Date.now()}`,
        user_id: store.getState().settings.user_id,
        sentence_id: sentence.id,
        kanji: mainKanjiToken.surface,
        reading: mainKanjiToken.reading,
        meaning: mainKanjiToken.meaning || sentence.translated_text,
        part_of_speech: mainKanjiToken.pos,
        jlpt_level: mainKanjiToken.jlpt || 'N5',
        mastery_status: 0,
        review_count: 0,
        next_review_at: Date.now() + 86400000,
        created_at: Date.now(),
        updated_at: Date.now()
      };
      store.addVocabulary(newVocab);
      hideModal();
      alert(`Kosakata "${mainKanjiToken.surface}" berhasil ditambahkan ke Dek Flashcard!`);
    }
  });
}

function showAddArticleModal(): void {
  const html = `
    <div class="flex flex-col gap-3.5">
      <div class="flex items-center justify-between">
        <h3 class="font-bold text-base text-text-primary">Tambah Catatan / Bahan Bacaan</h3>
        <button id="modal-close-btn" class="w-8 h-8 rounded-full hover:bg-canvas-secondary flex items-center justify-center text-text-secondary">
          <span class="material-symbols-outlined text-[18px]">close</span>
        </button>
      </div>

      <div class="flex flex-col gap-1">
        <label class="text-xs font-semibold text-text-primary">Judul Catatan</label>
        <input id="modal-art-title" type="text" placeholder="Contoh: Percakapan di Kafe Shinjuku" class="w-full px-3 py-2 bg-canvas-secondary rounded-xl text-xs text-text-primary focus:outline-none focus:ring-1 focus:ring-primary">
      </div>

      <div class="flex flex-col gap-1">
        <label class="text-xs font-semibold text-text-primary">Kategori</label>
        <select id="modal-art-category" class="w-full px-3 py-2 bg-canvas-secondary rounded-xl text-xs text-text-primary focus:outline-none focus:ring-1 focus:ring-primary">
          <option value="Percakapan">Percakapan</option>
          <option value="Buku & Artikel">Buku & Artikel</option>
          <option value="Lirik Lagu">Lirik Lagu</option>
          <option value="Menu & Tempat">Menu & Tempat</option>
          <option value="Umum">Umum</option>
        </select>
      </div>

      <div class="flex flex-col gap-1">
        <label class="text-xs font-semibold text-text-primary">Teks Bahasa Jepang</label>
        <textarea id="modal-art-text" rows="4" placeholder="Masukkan kalimat Jepang..." class="w-full px-3 py-2 bg-canvas-secondary rounded-xl text-xs text-text-primary font-mincho resize-none focus:outline-none focus:ring-1 focus:ring-primary"></textarea>
      </div>

      <button id="modal-submit-art-btn" class="w-full py-3 rounded-full bg-primary text-white font-bold text-xs shadow-sm active:scale-95 transition-all mt-1">
        Simpan & Analisis Bacaan
      </button>
    </div>
  `;

  showModal(html);
  getEl('modal-close-btn')?.addEventListener('click', hideModal);

  getEl('modal-submit-art-btn')?.addEventListener('click', async () => {
    const title = (getEl<HTMLInputElement>('modal-art-title')?.value || '').trim();
    const category = (getEl<HTMLSelectElement>('modal-art-category')?.value || 'Percakapan');
    const rawText = (getEl<HTMLTextAreaElement>('modal-art-text')?.value || '').trim();

    if (!title || !rawText) {
      alert('Judul dan Teks Bahasa Jepang wajib diisi.');
      return;
    }

    const now = Date.now();
    const artId = `art_${now}`;

    // Create sentence
    const sentId = `sent_${artId}_01`;
    const newSentence: Sentence = {
      id: sentId,
      article_id: artId,
      original_text: rawText,
      translated_text: `Terjemahan konteks: ${title}`,
      furigana_payload: JSON.stringify([
        { surface: rawText, reading: rawText, romaji: '', pos: 'noun', jlpt: 'N4' }
      ]),
      grammar_analysis: JSON.stringify([]),
      sequence_order: 1,
      inspection_count: 0,
      audio_play_count: 0,
      needs_deep_study: 0,
      updated_at: now
    };

    const newArticle: Article = {
      id: artId,
      user_id: store.getState().settings.user_id,
      title: title,
      category: category,
      raw_text: rawText,
      difficulty_level: 'N4',
      kanji_ratio: 0.35,
      created_at: now,
      updated_at: now,
      sentences: [newSentence]
    };

    store.addArticle(newArticle);
    hideModal();

    // Trigger sync
    store.syncWithBackend();
  });
}

function showVocabReviewModal(vocab: Vocabulary): void {
  const html = `
    <div class="flex flex-col gap-3.5 text-center">
      <div class="w-10 h-1 rounded-full bg-surface-container mx-auto"></div>
      <span class="text-[10px] font-bold text-text-muted uppercase tracking-wider">Spaced Repetition Review</span>

      <div class="p-6 rounded-2xl bg-canvas-secondary flex flex-col items-center gap-1">
        <span class="font-mincho text-3xl font-bold text-text-primary">${vocab.kanji}</span>
        <span class="text-sm font-semibold text-primary">${vocab.reading}</span>
        <span class="text-xs text-text-secondary mt-2">${vocab.meaning}</span>
      </div>

      <p class="text-xs text-text-muted">Seberapa mudah kamu mengingat kata ini?</p>

      <div class="grid grid-cols-3 gap-2">
        <button class="btn-srs-rating py-2.5 px-3 rounded-xl bg-surface-container text-text-primary text-xs font-bold active:scale-95 transition-all" data-q="1">
          Ulangi
        </button>
        <button class="btn-srs-rating py-2.5 px-3 rounded-xl bg-canvas-secondary text-primary text-xs font-bold active:scale-95 transition-all" data-q="3">
          Bagus
        </button>
        <button class="btn-srs-rating py-2.5 px-3 rounded-xl bg-primary text-white text-xs font-bold active:scale-95 transition-all shadow-sm" data-q="5">
          Mudah
        </button>
      </div>
    </div>
  `;

  showModal(html);

  const buttons = document.querySelectorAll('.btn-srs-rating');
  buttons.forEach(btn => {
    btn.addEventListener('click', (e) => {
      const q = parseInt((e.currentTarget as HTMLElement).dataset.q || '3', 10);
      const next = calculateNextSrsInterval(vocab.review_count, q);
      store.updateVocabulary(vocab.id, {
        review_count: vocab.review_count + 1,
        mastery_status: next.nextMastery,
        next_review_at: next.nextReviewAt
      });
      hideModal();
    });
  });
}

// -------------------------------------------------------------
// EVENT BINDINGS
// -------------------------------------------------------------
function attachEvents(state: AppState): void {
  // Navigation bar
  const navButtons = document.querySelectorAll('.nav-item');
  navButtons.forEach(btn => {
    btn.addEventListener('click', (e) => {
      const view = (e.currentTarget as HTMLElement).dataset.view as AppState['currentView'];
      if (view) store.setView(view);
    });
  });

  // Top bar sync trigger
  getEl('sync-trigger-btn')?.addEventListener('click', () => {
    store.syncWithBackend();
  });

  // Splash screen buttons
  getEl('splash-start-btn')?.addEventListener('click', () => {
    store.setView('quick-read');
  });
  getEl('splash-library-btn')?.addEventListener('click', () => {
    store.setView('koleksi');
  });

  // Quick read events
  const qrInput = getEl<HTMLTextAreaElement>('qr-input');
  qrInput?.addEventListener('input', (e) => {
    const len = (e.target as HTMLTextAreaElement).value.length;
    const charCountEl = getEl('qr-char-count');
    if (charCountEl) charCountEl.innerText = `${len} Karakter`;
  });

  getEl('qr-clear-btn')?.addEventListener('click', () => {
    if (qrInput) {
      qrInput.value = '';
      qrInput.dispatchEvent(new Event('input'));
    }
  });

  getEl('qr-paste-btn')?.addEventListener('click', async () => {
    try {
      const text = await navigator.clipboard.readText();
      if (qrInput && text) {
        qrInput.value = text;
        qrInput.dispatchEvent(new Event('input'));
      }
    } catch {
      alert('Gunakan Ctrl+V untuk menempel.');
    }
  });

  const chips = document.querySelectorAll('.qr-chip');
  chips.forEach(c => {
    c.addEventListener('click', (e) => {
      const txt = (e.currentTarget as HTMLElement).dataset.text || '';
      if (qrInput) {
        qrInput.value = txt;
        qrInput.dispatchEvent(new Event('input'));
      }
    });
  });

  getEl('qr-analyze-btn')?.addEventListener('click', async () => {
    const text = qrInput?.value.trim() || '';
    if (!text) return;

    isAnalyzing = true;
    store.setView('quick-read');

    try {
      const res = await fetch(`${store.getState().apiBaseUrl}/api/v1/analyze`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          text,
          api_key: store.getState().settings.deepseek_api_key || undefined
        })
      });

      if (res.ok) {
        const data = await res.json() as {
          difficulty_level: string;
          kanji_ratio: number;
          sentences: Array<{
            sequence_order: number;
            original_text: string;
            translated_text: string;
            tokens: Token[];
            grammar_points: GrammarPoint[];
          }>;
        };
        quickReadResult = { text, ...data };
      }
    } catch (e) {
      console.warn('API error, falling back to local result', e);
    } finally {
      isAnalyzing = false;
      store.setView('quick-read');
    }
  });

  getEl('qr-play-audio-btn')?.addEventListener('click', () => {
    const text = qrInput?.value.trim() || '今週の土曜日に新しいカフェに行きませんか。';
    playAudioPronunciation(text, store.getState().settings.tts_speed);
  });

  getEl('qr-copy-btn')?.addEventListener('click', () => {
    const text = qrInput?.value.trim() || '';
    if (text) {
      navigator.clipboard.writeText(text);
      alert('Hasil teks berhasil disalin!');
    }
  });

  getEl('qr-save-to-collection-btn')?.addEventListener('click', () => {
    const text = qrInput?.value.trim() || '今週の土曜日に新しいカフェに行きませんか。';
    const now = Date.now();
    const artId = `art_${now}`;

    const newArticle: Article = {
      id: artId,
      user_id: store.getState().settings.user_id,
      title: quickReadResult?.sentences[0]?.translated_text.substring(0, 30) || 'Bacaan Baru',
      category: 'Percakapan',
      raw_text: text,
      difficulty_level: quickReadResult?.difficulty_level || 'N4',
      kanji_ratio: quickReadResult?.kanji_ratio || 0.35,
      created_at: now,
      updated_at: now,
      sentences: [
        {
          id: `sent_${artId}_01`,
          article_id: artId,
          original_text: text,
          translated_text: quickReadResult?.sentences[0]?.translated_text || 'Terjemahan langsung',
          furigana_payload: JSON.stringify(quickReadResult?.sentences[0]?.tokens || []),
          grammar_analysis: JSON.stringify(quickReadResult?.sentences[0]?.grammar_points || []),
          sequence_order: 1,
          inspection_count: 0,
          audio_play_count: 0,
          needs_deep_study: 0,
          updated_at: now
        }
      ]
    };

    store.addArticle(newArticle);
    alert('Bacaan berhasil disimpan ke Koleksi!');
    store.setView('koleksi');
  });

  // Koleksi Events
  getEl('btn-add-article-modal')?.addEventListener('click', showAddArticleModal);
  getEl('btn-paste-clipboard')?.addEventListener('click', async () => {
    try {
      const text = await navigator.clipboard.readText();
      if (text) {
        showAddArticleModal();
        const textInput = getEl<HTMLTextAreaElement>('modal-art-text');
        if (textInput) textInput.value = text;
      }
    } catch {
      showAddArticleModal();
    }
  });

  const catPills = document.querySelectorAll('.category-pill');
  catPills.forEach(p => {
    p.addEventListener('click', (e) => {
      selectedCategory = (e.currentTarget as HTMLElement).dataset.category || 'Semua';
      store.setView('koleksi');
    });
  });

  const searchInput = getEl<HTMLInputElement>('article-search-input');
  searchInput?.addEventListener('input', (e) => {
    searchQuery = (e.target as HTMLInputElement).value;
    const viewContainer = getEl('view-container');
    if (viewContainer) {
      viewContainer.innerHTML = renderKoleksi(store.getState());
      attachEvents(store.getState());
    }
  });

  const openReaderButtons = document.querySelectorAll('.btn-open-reader');
  openReaderButtons.forEach(btn => {
    btn.addEventListener('click', (e) => {
      const id = (e.currentTarget as HTMLElement).dataset.id;
      const article = store.getState().articles.find(a => a.id === id);
      if (article) {
        store.setActiveArticle(article);
        store.setView('reader');
      }
    });
  });

  const deleteArticleButtons = document.querySelectorAll('.btn-delete-article');
  deleteArticleButtons.forEach(btn => {
    btn.addEventListener('click', (e) => {
      const id = (e.currentTarget as HTMLElement).dataset.id;
      if (id && confirm('Apakah Anda yakin ingin menghapus bacaan ini?')) {
        store.deleteArticle(id);
      }
    });
  });

  // Reader Studio Events
  getEl('reader-back-btn')?.addEventListener('click', () => {
    store.setView('koleksi');
  });
  getEl('reader-back-to-library')?.addEventListener('click', () => {
    store.setView('koleksi');
  });

  const furiModeButtons = document.querySelectorAll('.furi-mode-btn');
  furiModeButtons.forEach(btn => {
    btn.addEventListener('click', (e) => {
      const mode = (e.currentTarget as HTMLElement).dataset.mode as 'always' | 'tap' | 'off';
      if (mode) {
        store.updateSettings({ furigana_mode: mode });
      }
    });
  });

  const sentenceRows = document.querySelectorAll('.reader-sentence-row');
  sentenceRows.forEach(row => {
    row.addEventListener('click', (e) => {
      const target = e.target as HTMLElement;
      if (target.closest('.btn-inspect-action')) return; // handled by toolbar

      const index = parseInt((e.currentTarget as HTMLElement).dataset.index || '0', 10);
      const sentId = (e.currentTarget as HTMLElement).dataset.sentId || '';
      store.setActiveSentenceIndex(index);
      store.inspectSentence(sentId);
    });
  });

  const inspectActions = document.querySelectorAll('.btn-inspect-action');
  inspectActions.forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const action = (e.currentTarget as HTMLElement).dataset.action;
      const article = store.getState().activeArticle;
      if (!article || !article.sentences) return;

      const sentence = article.sentences[store.getState().activeSentenceIndex];
      if (!sentence) return;

      if (action === 'translate' || action === 'save') {
        showSentenceBottomSheet(sentence);
      } else if (action === 'audio') {
        playAudioPronunciation(sentence.original_text, store.getState().settings.tts_speed);
      }
    });
  });

  // Vocab Events
  const vocabFilterPills = document.querySelectorAll('.vocab-filter-pill');
  vocabFilterPills.forEach(p => {
    p.addEventListener('click', (e) => {
      selectedVocabFilter = (e.currentTarget as HTMLElement).dataset.filter || 'Semua';
      store.setView('vocab');
    });
  });

  const playVocabAudioBtns = document.querySelectorAll('.btn-play-vocab-audio');
  playVocabAudioBtns.forEach(btn => {
    btn.addEventListener('click', (e) => {
      const kanji = (e.currentTarget as HTMLElement).dataset.kanji || '';
      playAudioPronunciation(kanji, store.getState().settings.tts_speed);
    });
  });

  const reviewVocabBtns = document.querySelectorAll('.btn-review-vocab');
  reviewVocabBtns.forEach(btn => {
    btn.addEventListener('click', (e) => {
      const id = (e.currentTarget as HTMLElement).dataset.id;
      const vocab = store.getState().vocabularies.find(v => v.id === id);
      if (vocab) {
        showVocabReviewModal(vocab);
      }
    });
  });

  const deleteVocabBtns = document.querySelectorAll('.btn-delete-vocab');
  deleteVocabBtns.forEach(btn => {
    btn.addEventListener('click', (e) => {
      const id = (e.currentTarget as HTMLElement).dataset.id;
      if (id && confirm('Hapus kosakata ini dari dek belajar?')) {
        store.deleteVocabulary(id);
      }
    });
  });

  // Settings Events
  const furiSettingBtns = document.querySelectorAll('.furi-setting-btn');
  furiSettingBtns.forEach(btn => {
    btn.addEventListener('click', (e) => {
      const mode = (e.currentTarget as HTMLElement).dataset.mode as 'always' | 'tap' | 'off';
      if (mode) store.updateSettings({ furigana_mode: mode });
    });
  });

  const fontSettingBtns = document.querySelectorAll('.font-setting-btn');
  fontSettingBtns.forEach(btn => {
    btn.addEventListener('click', (e) => {
      const font = (e.currentTarget as HTMLElement).dataset.font as 'mincho' | 'gothic';
      if (font) store.updateSettings({ kanji_font_style: font });
    });
  });

  const speedRange = getEl<HTMLInputElement>('settings-speed-range');
  speedRange?.addEventListener('input', (e) => {
    const val = parseFloat((e.target as HTMLInputElement).value);
    const ind = getEl('speed-indicator');
    if (ind) ind.innerText = `${val.toFixed(1)}x`;
    store.updateSettings({ tts_speed: val });
  });

  getEl('settings-preview-audio')?.addEventListener('click', () => {
    playAudioPronunciation('東京の春は桜が満開です。', store.getState().settings.tts_speed);
  });

  getEl('btn-save-key')?.addEventListener('click', () => {
    const key = (getEl<HTMLInputElement>('deepseek-key-input')?.value || '').trim();
    store.updateSettings({ deepseek_api_key: key || null });
    alert('Kunci API DeepSeek berhasil diperbarui!');
  });

  getEl('btn-manual-sync')?.addEventListener('click', () => {
    store.syncWithBackend();
  });
}

// -------------------------------------------------------------
// MAIN RENDER LOOP
// -------------------------------------------------------------
function renderApp(state: AppState): void {
  const container = getEl('view-container');
  if (!container) return;

  // Update Top Bar & Sync status
  const syncIcon = getEl('sync-icon');
  const syncText = getEl('sync-text');
  if (syncIcon && syncText) {
    if (state.syncStatus === 'syncing') {
      syncIcon.innerText = 'sync';
      syncIcon.classList.add('animate-spin');
      syncText.innerText = 'Menyinkron...';
    } else if (state.syncStatus === 'synced') {
      syncIcon.innerText = 'cloud_done';
      syncIcon.classList.remove('animate-spin');
      syncText.innerText = 'Tersambung';
    } else {
      syncIcon.innerText = 'cloud_off';
      syncIcon.classList.remove('animate-spin');
      syncText.innerText = 'Luring (Offline)';
    }
  }

  // Update navigation highlight
  const navButtons = document.querySelectorAll('.nav-item');
  navButtons.forEach(btn => {
    const view = (btn as HTMLElement).dataset.view;
    if (view === state.currentView) {
      btn.classList.add('text-primary', 'font-bold');
      btn.classList.remove('text-text-muted');
    } else {
      btn.classList.remove('text-primary', 'font-bold');
      btn.classList.add('text-text-muted');
    }
  });

  // Render current view
  let html = '';
  switch (state.currentView) {
    case 'splash':
      html = renderSplash(state);
      break;
    case 'quick-read':
      html = renderQuickRead(state);
      break;
    case 'koleksi':
      html = renderKoleksi(state);
      break;
    case 'reader':
      html = renderReader(state);
      break;
    case 'vocab':
      html = renderVocab(state);
      break;
    case 'analytics':
      html = renderAnalytics(state);
      break;
    case 'settings':
      html = renderSettings(state);
      break;
  }

  container.innerHTML = html;
  attachEvents(state);
}

// Subscribe store to render loop
store.subscribe(renderApp);

// Initial render
if (typeof document !== 'undefined') {
  renderApp(store.getState());
  // Try syncing on start
  store.syncWithBackend();
}
