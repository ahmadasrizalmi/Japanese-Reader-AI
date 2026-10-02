import {
  AppStore,
  AppState,
  Article,
  Sentence,
  Vocabulary,
  Token,
  GrammarPoint,
  AnalysisProgress,
  buildRubyHtml,
  parseTokens,
  parseGrammarPoints,
  getPastelHighlight,
  calculateNextSrsInterval
} from './state';
import { tokenizeSentence, detectGrammarPoints } from './tokenizer';

const store = new AppStore();

// Synthesize audio using Web Audio API or fallback SpeechSynthesis
function playAudioPronunciation(text: string, speed: number = 1.0): void {
  if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = 'ja-JP';
    utterance.rate = speed;
    window.speechSynthesis.speak(utterance);
  }
}

// DOM helper
function getEl<T extends HTMLElement>(id: string): T | null {
  return document.getElementById(id) as T | null;
}

// Filter states
let selectedCategory: string = 'Semua';
let searchQuery: string = '';
let selectedFlashcardFilter: string = 'Semua';
let currentFlashcardIndex: number = 0;
let isFlashcardFlipped: boolean = false;

// Reader local states
let readerFuriganaMode: 'always' | 'tap' | 'off' = 'off';
let readerTranslateMode: boolean = false;
let readerShowPillBar: boolean = true;
let readerFontSizeSp: number = 20;
let readerExpandedSentences: Set<string> = new Set();
let inspectedToken: Token | null = null;
let inspectedSentence: Sentence | null = null;

// Modal state
let showAddTextModal: boolean = false;

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

      <h2 class="relative z-10 text-2xl font-bold tracking-tight mb-2 font-mincho">
        Komorebi Reader AI
      </h2>
      <p class="relative z-10 text-xs text-white/80 max-w-sm mb-6 leading-relaxed">
        Platform membaca bahasa Jepang modern dengan kanvas Zen full-width, furigana ruby rapi, terjemahan inline, dan dek flashcard Spaced Repetition otomatis.
      </p>

      <div class="relative z-10 w-full flex flex-col gap-2.5 max-w-xs">
        <button id="splash-start-btn" class="w-full py-3.5 px-6 rounded-full bg-surface text-crimson-deep font-bold text-sm shadow-xl active:scale-95 transition-all flex items-center justify-center gap-2">
          <span>Buka Koleksi Bacaan</span>
          <span class="material-symbols-outlined text-[18px]">arrow_forward</span>
        </button>
      </div>
    </div>
  `;
}

// -------------------------------------------------------------
// 2. RENDER KOLEKSI BACAAN
// -------------------------------------------------------------
function renderKoleksi(state: AppState): string {
  const categories = ['Semua', ...Array.from(new Set(state.articles.map(a => a.category).filter(c => c && c !== 'Semua')))];
  
  let list = state.articles;
  if (selectedCategory !== 'Semua') {
    list = list.filter(a => a.category === selectedCategory);
  }
  if (searchQuery.trim() !== '') {
    const q = searchQuery.toLowerCase();
    list = list.filter(a => a.title.toLowerCase().includes(q) || a.raw_text.toLowerCase().includes(q));
  }

  return `
    <div class="flex flex-col gap-4">
      <!-- Search Bar -->
      <div class="relative">
        <span class="material-symbols-outlined absolute left-3.5 top-1/2 -translate-y-1/2 text-text-muted text-[18px]">search</span>
        <input id="collection-search-input" type="text" placeholder="Cari judul atau isi bacaan..." value="${searchQuery}" class="w-full bg-surface-card border border-surface-container rounded-full pl-10 pr-4 py-2.5 text-xs text-text-primary placeholder:text-text-muted focus:outline-none focus:border-primary shadow-sm">
      </div>

      <!-- Categories Pills -->
      <div class="flex items-center gap-2 overflow-x-auto no-scrollbar py-0.5">
        ${categories.map(c => `
          <button class="collection-cat-pill px-3.5 py-1.5 rounded-full text-xs font-semibold shadow-sm transition-all shrink-0 ${selectedCategory === c ? 'bg-primary text-white' : 'bg-surface-card border border-surface-container text-text-secondary hover:border-primary/50'}" data-category="${c}">
            ${c}
          </button>
        `).join('')}
      </div>

      <!-- Articles Grid/List -->
      <div class="flex flex-col gap-3">
        ${list.length === 0 ? `
          <div class="py-16 flex flex-col items-center justify-center text-center text-text-muted bg-surface-card rounded-2xl border border-surface-container p-6">
            <span class="material-symbols-outlined text-4xl mb-2 text-primary/40">menu_book</span>
            <p class="text-sm font-semibold">Tidak ada artikel di kategori ini.</p>
            <p class="text-xs text-text-muted mt-1">Klik tombol (+) di bawah untuk menambahkan bacaan baru.</p>
          </div>
        ` : list.map(art => {
          const charCount = art.raw_text.length;
          const estimatedMinutes = Math.max(1, Math.round(charCount / 300));
          return `
            <div class="article-card group cursor-pointer bg-surface-card border border-surface-container rounded-2xl p-4 shadow-sm hover:shadow-md hover:border-primary/30 transition-all flex flex-col gap-2.5" data-art-id="${art.id}">
              <div class="flex items-center justify-between text-xs">
                <span class="text-[11px] font-semibold text-primary">~${estimatedMinutes} menit baca • ${charCount} karakter</span>
                <span class="text-[10px] text-text-muted font-medium">${new Date(art.created_at).toLocaleDateString('id-ID', { day: 'numeric', month: 'short' })}</span>
              </div>

              <h3 class="font-bold text-base text-text-primary group-hover:text-primary transition-colors font-mincho line-clamp-1 leading-snug">
                ${art.title}
              </h3>

              <p class="text-xs text-text-secondary font-mincho line-clamp-2 leading-relaxed">
                ${art.raw_text}
              </p>
            </div>
          `;
        }).join('')}
      </div>
    </div>
  `;
}

// -------------------------------------------------------------
// 3. RENDER ZEN FULL-WIDTH READER
// -------------------------------------------------------------
function renderReader(state: AppState): string {
  const article = state.activeArticle;
  if (!article) {
    return `
      <div class="py-16 flex flex-col items-center justify-center text-center text-text-muted">
        <span class="material-symbols-outlined text-4xl mb-2 text-primary">menu_book</span>
        <p class="text-sm font-semibold">Tidak ada artikel yang sedang dibuka.</p>
        <button id="reader-back-to-library" class="mt-3 px-4 py-2 rounded-full bg-primary text-white text-xs font-bold">
          Buka Koleksi
        </button>
      </div>
    `;
  }

  const sentences = article.sentences || [];
  const charCount = article.raw_text.length;
  const estimatedMinutes = Math.max(1, Math.round(charCount / 300));
  const prog = state.analysisProgress;
  const fontClass = state.settings.kanji_font_style === 'gothic' ? 'font-gothic' : 'font-mincho';

  return `
    <div id="zen-reader-canvas" class="min-h-screen bg-surface flex flex-col relative select-text" style="font-size: ${readerFontSizeSp}px;">
      <!-- Subtle Reading Progress Bar at Top -->
      <div id="reader-progress-bar" class="fixed top-0 inset-x-0 h-1 bg-primary/20 z-50">
        <div id="reader-progress-fill" class="h-full bg-primary transition-all duration-150" style="width: 0%;"></div>
      </div>

      <!-- Top Back Header & Progressive Banner -->
      <div class="sticky top-0 z-30 bg-surface/90 backdrop-blur-md px-4 py-2.5 flex items-center justify-between border-b border-surface-container/60">
        <button id="reader-back-btn" class="flex items-center gap-1.5 text-xs font-bold text-text-secondary hover:text-primary transition-colors py-1 px-2.5 rounded-full hover:bg-canvas-secondary">
          <span class="material-symbols-outlined text-[18px]">arrow_back</span>
          <span>Kembali</span>
        </button>

        ${prog && prog.articleId === article.id ? `
          <div class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full ${prog.isComplete ? 'bg-green-100 text-green-800' : 'bg-crimson-surface text-primary'} text-[11px] font-bold shadow-sm animate-pulse">
            <span class="material-symbols-outlined text-[14px]">${prog.isComplete ? 'check_circle' : 'sync'}</span>
            <span>${prog.isComplete ? 'Analisis teks lengkap!' : `Menganalisis: bagian ${prog.currentChunk} dari ${prog.totalChunks}...`}</span>
          </div>
        ` : `
          <span class="text-[11px] font-medium text-text-muted">Ketuk ruang kosong untuk menu</span>
        `}
      </div>

      <!-- Article Reading Content (Clean, natural zero-space typesetting) -->
      <div id="reader-article-content" class="max-w-2xl w-full mx-auto px-5 pt-8 pb-32 flex flex-col gap-6 cursor-pointer">
        <!-- Article Header Metadata -->
        <header class="flex flex-col gap-2 pb-4 border-b border-surface-container/80 cursor-default">
          <div class="flex items-center gap-2 text-xs font-semibold text-primary">
            <span>~${estimatedMinutes} menit baca</span>
            <span>•</span>
            <span class="text-text-muted font-normal">${charCount} karakter</span>
          </div>
          <h1 class="text-2xl sm:text-3xl font-bold text-text-primary ${fontClass} leading-tight tracking-tight">
            ${article.title}
          </h1>
        </header>

        <!-- Sentences Flow -->
        <div class="flex flex-col gap-5 ${fontClass}">
          ${sentences.map((sent, sIdx) => {
            const tokens = parseTokens(sent.furigana_payload);
            const isExpanded = readerTranslateMode || readerExpandedSentences.has(sent.id);

            return `
                <div class="flex items-baseline gap-2.5">
                  <!-- Translation trigger icon (Always visible for seamless per-sentence access) -->
                  <button class="btn-toggle-inline-trans shrink-0 flex items-center justify-center w-6 h-6 rounded-md ${isExpanded ? 'bg-primary text-white shadow-sm' : 'bg-canvas-secondary text-text-secondary hover:text-primary hover:bg-crimson-surface'} text-[11px] font-bold transition-all" data-sent-id="${sent.id}" title="Lihat terjemahan kalimat ini">
                    あA
                  </button>

                  <!-- Japanese Tokens Row with Zero-space natural typography -->
                  <div class="japanese-sentence leading-[2.8em] text-text-primary tracking-normal flex-1" style="word-spacing: 0; letter-spacing: 0;">
                    ${(() => {
                      const sentenceTokens = tokens.length > 0 ? tokens : tokenizeSentence(sent.original_text);
                      const isFuriOn = readerFuriganaMode === 'always';
                      return sentenceTokens.map((token, tIdx) => {
                        const hasReading = token.reading && token.reading !== token.surface && !['、', '。', '！', '？', '「', '」', '（', '）', '・'].includes(token.surface);
                        const tokenJson = encodeURIComponent(JSON.stringify(token));

                        if (isFuriOn && hasReading) {
                          return `<ruby class="token-word cursor-pointer hover:bg-highlight-yellow rounded px-0 transition-colors" data-token="${tokenJson}" data-sent-id="${sent.id}">${token.surface}<rt class="text-primary font-semibold text-[10px] select-none">${token.reading}</rt></ruby>`;
                        } else {
                          return `<span class="token-word cursor-pointer hover:bg-highlight-yellow rounded px-0 transition-colors" data-token="${tokenJson}" data-sent-id="${sent.id}">${token.surface}</span>`;
                        }
                      }).join('');
                    })()}
                  </div>
                </div>

                <!-- Smooth Inline Translation directly below the sentence -->
                ${isExpanded ? `
                  <div class="sentence-translation font-sans text-xs text-[#6E6262] leading-relaxed pl-8 pt-0.5 transition-all">
                    ${sent.translated_text || 'Sedang menganalisis terjemahan konteks...'}
                  </div>
                ` : ''}
              </div>
            `;
          }).join('')}
        </div>

        <!-- End of Article Footer Actions -->
        <footer class="mt-12 pt-6 border-t border-surface-container flex flex-col gap-3 cursor-default">
          <button id="reader-finish-btn" class="w-full py-3.5 px-6 rounded-xl bg-[#22C55E] text-white font-bold text-sm shadow-md hover:bg-[#1eb354] active:scale-95 transition-all flex items-center justify-center gap-2">
            <span class="material-symbols-outlined text-[18px]">check</span>
            <span>Selesai Membaca</span>
          </button>

          <button id="reader-show-words-btn" class="w-full py-3 px-6 rounded-xl border border-surface-container bg-surface-card text-text-secondary font-bold text-xs hover:border-primary/40 active:scale-95 transition-all flex items-center justify-center gap-2">
            <span>Lihat Kata yang Dicari (Flashcard)</span>
          </button>
        </footer>
      </div>

      <!-- Floating Minimalist Pill Bar (Appears on clicking background / toggle) -->
      <!-- Floating Minimalist Pill Bar (Always visible floating at bottom center) -->
      <div id="reader-floating-pill" class="fixed bottom-6 left-1/2 -translate-x-1/2 z-40 flex items-center gap-1.5 bg-[#2D2424]/95 text-white backdrop-blur-lg px-4 py-2 rounded-full shadow-2xl transition-all border border-white/10 select-none">
        <button id="pill-furi-btn" class="px-3 py-1.5 rounded-full text-xs font-bold transition-colors ${readerFuriganaMode === 'always' ? 'bg-primary text-white shadow-sm' : 'text-white/70 hover:text-white'}">
          ふりがな
        </button>

        <div class="w-px h-3.5 bg-white/20"></div>

        <!-- Toggle Translate -->
        <button id="pill-trans-btn" class="px-3 py-1.5 rounded-full text-xs font-bold flex items-center gap-1 transition-colors ${readerTranslateMode ? 'bg-primary text-white shadow-sm' : 'text-white/70 hover:text-white'}">
          <span>あA</span>
          <span class="text-[11px] font-medium">Terjemah</span>
        </button>

        <div class="w-px h-3.5 bg-white/20"></div>

        <!-- Font Zoom Steppers -->
        <div class="flex items-center gap-1 px-1">
          <button id="pill-font-minus" class="w-7 h-7 rounded-full flex items-center justify-center font-bold text-xs text-white/70 hover:text-white active:scale-90">A-</button>
          <span class="text-[10px] text-white/60 font-mono">${readerFontSizeSp}</span>
          <button id="pill-font-plus" class="w-7 h-7 rounded-full flex items-center justify-center font-bold text-xs text-white/70 hover:text-white active:scale-90">A+</button>
        </div>
      </div>
    </div>
  `;
}

// -------------------------------------------------------------
// 4. RENDER FLASHCARD SRS (INTERACTIVE 3D FLIP CARD)
// -------------------------------------------------------------
function renderFlashcards(state: AppState): string {
  let list = state.vocabularies;
  if (selectedFlashcardFilter === 'Perlu Dipelajari') {
    list = list.filter(v => v.mastery_status === 0 || v.review_count === 0);
  } else if (selectedFlashcardFilter === 'Sedang Diulang') {
    list = list.filter(v => v.mastery_status === 1);
  } else if (selectedFlashcardFilter === 'Dikuasai') {
    list = list.filter(v => v.mastery_status === 2);
  }

  if (currentFlashcardIndex >= list.length) {
    currentFlashcardIndex = Math.max(0, list.length - 1);
  }

  const currentVocab = list[currentFlashcardIndex];
  const fontClass = state.settings.kanji_font_style === 'gothic' ? 'font-gothic' : 'font-mincho';

  // Find original context sentence
  const allSentences = state.articles.flatMap(a => a.sentences || []);
  const contextSentence = currentVocab
    ? (allSentences.find(s => s.id === currentVocab.sentence_id) || allSentences.find(s => s.original_text.includes(currentVocab.kanji)))
    : null;

  return `
    <div class="flex flex-col gap-4 max-w-xl mx-auto">
      <!-- Top Header -->
      <div class="flex items-center justify-between">
        <div>
          <span class="text-[10px] font-bold uppercase tracking-wider text-text-muted">DEK KOSAKATA SRS</span>
          <h2 class="text-xl font-bold text-text-primary tracking-tight">Latihan Flashcard</h2>
        </div>
        ${list.length > 0 ? `
          <span class="text-xs px-3 py-1 rounded-full bg-crimson-surface text-primary font-bold">
            ${currentFlashcardIndex + 1} / ${list.length}
          </span>
        ` : ''}
      </div>

      <!-- Filters -->
      <div class="flex items-center gap-2 overflow-x-auto no-scrollbar py-0.5">
        ${['Semua', 'Perlu Dipelajari', 'Sedang Diulang', 'Dikuasai'].map(f => `
          <button class="fc-filter-pill px-3.5 py-1.5 rounded-full text-xs font-semibold shadow-sm transition-all shrink-0 ${selectedFlashcardFilter === f ? 'bg-primary text-white' : 'bg-surface-card border border-surface-container text-text-secondary hover:border-primary/50'}" data-filter="${f}">
            ${f}
          </button>
        `).join('')}
      </div>

      <!-- Main Card Container -->
      ${list.length === 0 ? `
        <div class="py-20 flex flex-col items-center justify-center text-center text-text-muted bg-surface-card rounded-3xl border border-surface-container p-6 shadow-sm">
          <span class="material-symbols-outlined text-5xl mb-3 text-primary/40">style</span>
          <h3 class="text-base font-bold text-text-primary">Belum ada kartu di kategori ini</h3>
          <p class="text-xs text-text-muted mt-1.5 max-w-xs leading-relaxed">
            Ketuk kata saat membaca di Reader Studio untuk menyimpannya ke dek flashcard ini secara otomatis.
          </p>
        </div>
      ` : `
        <!-- 3D Interactive Flip Card -->
        <div class="perspective-1000 w-full min-h-[380px] cursor-pointer" id="flashcard-flip-container">
          <div id="flashcard-flipper" class="relative w-full h-full min-h-[380px] rounded-3xl transition-transform duration-500 transform-style-3d ${isFlashcardFlipped ? 'rotate-y-180' : ''}">
            
            <!-- SISI DEPAN (FRONT) -->
            <div class="absolute inset-0 backface-hidden bg-surface-card rounded-3xl p-6 border border-surface-container shadow-sm flex flex-col justify-between">
              <div class="flex items-center justify-between">
                <span class="px-2.5 py-1 rounded-full bg-crimson-surface text-primary text-xs font-bold">
                  JLPT ${currentVocab.jlpt_level || 'N5'}
                </span>
                <button class="btn-play-fc-audio w-9 h-9 rounded-full bg-canvas-secondary flex items-center justify-center text-primary hover:bg-crimson-surface transition-colors" data-kanji="${currentVocab.kanji}">
                  <span class="material-symbols-outlined text-[18px]">volume_up</span>
                </button>
              </div>

              <!-- Kanji Besar -->
              <div class="flex flex-col items-center justify-center text-center my-auto py-8">
                <div class="text-5xl font-bold text-text-primary ${fontClass} tracking-wider">
                  ${currentVocab.kanji}
                </div>
                <span class="text-xs text-text-muted mt-3 font-medium">Tinjauan: ${currentVocab.review_count}x</span>
              </div>

              <!-- Bottom Hint -->
              <div class="flex items-center justify-center gap-1.5 text-xs text-text-muted font-medium pt-2 border-t border-surface-container/60">
                <span class="material-symbols-outlined text-[16px]">touch_app</span>
                <span>Ketuk kartu untuk melihat arti & konteks</span>
              </div>
            </div>

            <!-- SISI BELAKANG (BACK) -->
            <div class="absolute inset-0 backface-hidden rotate-y-180 bg-surface-card rounded-3xl p-6 border border-surface-container shadow-md flex flex-col justify-between">
              <div class="flex flex-col gap-4">
                <!-- Reading & Audio -->
                <div class="flex items-center justify-between pb-3 border-b border-surface-container">
                  <div class="flex flex-col">
                    <span class="text-xl font-bold text-primary ${fontClass}">${currentVocab.reading}</span>
                    <span class="text-xs text-text-muted font-mono">${currentVocab.part_of_speech} • ${currentVocab.kanji}</span>
                  </div>
                  <button class="btn-play-fc-audio w-9 h-9 rounded-full bg-canvas-secondary flex items-center justify-center text-primary hover:bg-crimson-surface transition-colors" data-kanji="${currentVocab.kanji}">
                    <span class="material-symbols-outlined text-[18px]">volume_up</span>
                  </button>
                </div>

                <!-- Meaning -->
                <div class="bg-canvas-secondary rounded-2xl p-3.5">
                  <span class="text-[10px] font-bold uppercase tracking-wider text-primary">ARTI (INDONESIA)</span>
                  <p class="text-sm font-bold text-text-primary mt-0.5 leading-snug">${currentVocab.meaning}</p>
                </div>

                <!-- Context Sentence from Reading -->
                ${contextSentence ? `
                  <div class="bg-highlight-yellow/40 rounded-2xl p-3 border border-highlight-yellow flex flex-col gap-1">
                    <div class="flex items-center justify-between text-[10px] font-bold text-primary">
                      <span>📝 Konteks Kalimat Asli:</span>
                      <button class="btn-play-fc-audio text-primary hover:scale-110 transition-transform" data-kanji="${contextSentence.original_text}">
                        <span class="material-symbols-outlined text-[14px]">volume_up</span>
                      </button>
                    </div>
                    <p class="text-xs text-text-primary ${fontClass} font-medium leading-relaxed">${contextSentence.original_text}</p>
                    ${contextSentence.translated_text ? `
                      <p class="text-[11px] text-text-secondary leading-snug">${contextSentence.translated_text}</p>
                    ` : ''}
                  </div>
                ` : ''}
              </div>

              <!-- SRS Action Buttons (Lupa & Ingat) -->
              <div class="grid grid-cols-2 gap-3 pt-3">
                <button class="btn-srs-rating py-3 px-4 rounded-xl bg-crimson-surface text-primary font-bold text-xs hover:bg-primary hover:text-white transition-all active:scale-95" data-quality="1" data-id="${currentVocab.id}">
                  🔴 Lupa
                </button>
                <button class="btn-srs-rating py-3 px-4 rounded-xl bg-[#22C55E] text-white font-bold text-xs hover:bg-[#1eb354] transition-all active:scale-95 shadow-sm" data-quality="4" data-id="${currentVocab.id}">
                  🟢 Ingat
                </button>
              </div>
            </div>

          </div>
        </div>

        <!-- Previous / Next Controls -->
        <div class="flex items-center justify-between pt-2">
          <button id="fc-prev-btn" class="w-10 h-10 rounded-full bg-surface-card border border-surface-container flex items-center justify-center text-text-secondary hover:text-primary transition-colors disabled:opacity-40" ${currentFlashcardIndex === 0 ? 'disabled' : ''}>
            <span class="material-symbols-outlined text-[20px]">chevron_left</span>
          </button>

          <span class="text-xs text-text-muted font-medium">Ketuk kartu untuk membalik</span>

          <button id="fc-next-btn" class="w-10 h-10 rounded-full bg-surface-card border border-surface-container flex items-center justify-center text-text-secondary hover:text-primary transition-colors disabled:opacity-40" ${currentFlashcardIndex >= list.length - 1 ? 'disabled' : ''}>
            <span class="material-symbols-outlined text-[20px]">chevron_right</span>
          </button>
        </div>
      `}
    </div>
  `;
}

// -------------------------------------------------------------
// 5. RENDER KEMAJUAN (ANALYTICS)
// -------------------------------------------------------------
function renderAnalytics(state: AppState): string {
  const stats = state.stats;
  const totalChars = state.articles.reduce((acc, a) => acc + a.raw_text.length, 0);
  const totalArticles = state.articles.length;
  const totalVocab = state.vocabularies.length;
  const mastered = state.vocabularies.filter(v => v.mastery_status === 2).length;

  return `
    <div class="flex flex-col gap-4">
      <div>
        <span class="text-[10px] font-bold uppercase tracking-wider text-text-muted">STATISTIK BELAJAR</span>
        <h2 class="text-xl font-bold text-text-primary tracking-tight">Kemajuan Membaca</h2>
      </div>

      <!-- Quick Metrics Grid -->
      <div class="grid grid-cols-2 gap-3">
        <div class="bg-surface-card rounded-2xl p-4 border border-surface-container shadow-sm flex flex-col gap-1">
          <span class="text-[11px] text-text-muted font-medium">Total Bahan Bacaan</span>
          <span class="text-2xl font-bold text-primary">${totalArticles}</span>
          <span class="text-[10px] text-text-muted">${totalChars} karakter terbaca</span>
        </div>

        <div class="bg-surface-card rounded-2xl p-4 border border-surface-container shadow-sm flex flex-col gap-1">
          <span class="text-[11px] text-text-muted font-medium">Kosakata Tersimpan</span>
          <span class="text-2xl font-bold text-primary">${totalVocab}</span>
          <span class="text-[10px] text-text-muted">${mastered} kata dikuasai</span>
        </div>
      </div>

      <!-- Weekly Study Chart -->
      <div class="bg-surface-card rounded-2xl p-4 border border-surface-container shadow-sm flex flex-col gap-3">
        <span class="text-xs font-bold text-text-primary uppercase tracking-wider">Aktivitas Mingguan</span>
        <div class="flex items-end justify-between h-28 pt-4 gap-2">
          ${stats.weeklyStudyMinutes.map(m => {
            const h = Math.min(100, Math.max(15, (m.minutes / 65) * 100));
            return `
              <div class="flex flex-col items-center gap-1 flex-1">
                <div class="w-full bg-crimson-surface rounded-t-lg transition-all" style="height: ${h}%;"></div>
                <span class="text-[10px] text-text-muted font-semibold">${m.day}</span>
              </div>
            `;
          }).join('')}
        </div>
      </div>
    </div>
  `;
}

// -------------------------------------------------------------
// 6. RENDER PENGATURAN (CLEAN, NO DEEPSEEK KEY / CLOUDFLARE URL)
// -------------------------------------------------------------
function renderSettings(state: AppState): string {
  const s = state.settings;

  return `
    <div class="flex flex-col gap-4">
      <!-- Profile Header -->
      <div class="rounded-3xl bg-surface-card p-4 border border-surface-container shadow-sm flex items-center gap-3">
        <div class="w-12 h-12 rounded-full bg-crimson-surface text-primary flex items-center justify-center font-bold text-lg font-mincho">
          読
        </div>
        <div class="flex flex-col min-w-0 flex-1">
          <h3 class="font-bold text-base text-text-primary">Pembelajar Bahasa Jepang</h3>
          <span class="text-xs text-text-muted">Komorebi Reader AI</span>
        </div>
      </div>

      <!-- Tampilan Huruf & Furigana -->
      <div class="bg-surface-card rounded-2xl p-4 border border-surface-container shadow-sm flex flex-col gap-3">
        <span class="text-xs font-bold text-primary uppercase tracking-wider">Tampilan Teks & Furigana</span>

        <div class="flex flex-col gap-1.5">
          <label class="text-xs font-semibold text-text-primary">Gaya Huruf Kanji</label>
          <div class="grid grid-cols-2 gap-2" id="settings-font-picker">
            <button class="font-setting-btn p-2.5 rounded-xl border text-xs font-bold flex items-center justify-between ${s.kanji_font_style === 'mincho' ? 'border-primary bg-crimson-surface text-primary' : 'border-surface-container bg-canvas-secondary text-text-secondary'}" data-font="mincho">
              <span>明朝体 (Mincho)</span>
              <span class="material-symbols-outlined text-[16px]">${s.kanji_font_style === 'mincho' ? 'check' : ''}</span>
            </button>
            <button class="font-setting-btn p-2.5 rounded-xl border text-xs font-bold flex items-center justify-between ${s.kanji_font_style === 'gothic' ? 'border-primary bg-crimson-surface text-primary' : 'border-surface-container bg-canvas-secondary text-text-secondary'}" data-font="gothic">
              <span>ゴシック (Gothic)</span>
              <span class="material-symbols-outlined text-[16px]">${s.kanji_font_style === 'gothic' ? 'check' : ''}</span>
            </button>
          </div>
        </div>
      </div>

      <!-- Audio Speed -->
      <div class="bg-surface-card rounded-2xl p-4 border border-surface-container shadow-sm flex flex-col gap-3">
        <div class="flex items-center justify-between">
          <span class="text-xs font-bold text-primary uppercase tracking-wider">Kecepatan Suara (Audio)</span>
          <span id="speed-indicator" class="text-xs font-bold text-primary bg-crimson-surface px-2 py-0.5 rounded-full">${s.tts_speed.toFixed(1)}x</span>
        </div>
        <input id="settings-speed-range" type="range" min="0.5" max="1.5" step="0.1" value="${s.tts_speed}" class="w-full accent-primary h-1.5 bg-canvas-secondary rounded-lg appearance-none cursor-pointer">
      </div>

      <!-- Cloudflare Edge Sync Status -->
      <div class="bg-surface-card rounded-2xl p-4 border border-surface-container shadow-sm flex flex-col gap-3">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2 text-xs font-bold text-primary uppercase tracking-wider">
            <span class="material-symbols-outlined text-[18px]">cloud_sync</span>
            <span>Sinkronisasi Cloudflare</span>
          </div>
          <span class="text-[10px] font-bold px-2.5 py-0.5 rounded-full bg-green-100 text-green-800">TERHUBUNG</span>
        </div>
        <p class="text-xs text-text-secondary leading-relaxed">
          Pencadangan cloud untuk koleksi bacaan, flashcard, dan analisis kecerdasan buatan otomatis di edge network.
        </p>
        <div class="flex items-center justify-between pt-1">
          <button id="settings-sync-btn" class="px-4 py-2 rounded-full bg-primary text-white text-xs font-bold shadow-sm hover:bg-primary-container active:scale-95 transition-all flex items-center gap-1.5">
            <span class="material-symbols-outlined text-[16px]">sync</span>
            <span>Sinkronkan Sekarang</span>
          </button>
          <span class="text-xs font-semibold text-primary">Status: ${state.syncStatus}</span>
        </div>
      </div>
    </div>
  `;
}

// -------------------------------------------------------------
// 7. RENDER ADD TEXT MODAL (SCROLLABLE & NON-BLOCKING)
// -------------------------------------------------------------
function renderAddTextModal(): string {
  return `
    <div id="modal-backdrop" class="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm flex items-end sm:items-center justify-center p-0 sm:p-4">
      <div class="bg-surface-card w-full max-w-lg rounded-t-3xl sm:rounded-3xl p-5 shadow-2xl flex flex-col gap-4 max-h-[90vh] overflow-y-auto">
        <div class="flex items-center justify-between pb-2 border-b border-surface-container">
          <h3 class="font-bold text-base text-text-primary">Tambah Bahan Bacaan Baru</h3>
          <button id="modal-close-btn" class="w-8 h-8 rounded-full flex items-center justify-center text-text-muted hover:text-primary">
            <span class="material-symbols-outlined text-[20px]">close</span>
          </button>
        </div>

        <div class="flex flex-col gap-1.5">
          <label class="text-xs font-semibold text-text-secondary">Judul Catatan (Opsional)</label>
          <input id="modal-title-input" type="text" placeholder="Misal: Cerita Kafe Tokyo" class="w-full bg-surface border border-surface-container rounded-xl py-2 px-3 text-xs text-text-primary placeholder:text-text-muted focus:outline-none focus:border-primary">
        </div>

        <div class="flex flex-col gap-1.5">
          <label class="text-xs font-semibold text-text-secondary">Teks Bahasa Jepang</label>
          <textarea id="modal-text-input" rows="6" placeholder="Tempel atau ketik teks Jepang di sini..." class="w-full max-h-56 bg-surface border border-surface-container rounded-xl p-3 text-sm text-text-primary font-mincho placeholder:text-text-muted focus:outline-none focus:border-primary leading-relaxed resize-none overflow-y-auto"></textarea>
        </div>

        <button id="modal-submit-btn" class="w-full py-3.5 px-6 rounded-full bg-primary text-white font-bold text-xs shadow-md hover:bg-primary-container active:scale-95 transition-all flex items-center justify-center gap-2 mt-2">
          <span class="material-symbols-outlined text-[18px]">menu_book</span>
          <span>Simpan & Buka Bacaan</span>
        </button>
      </div>
    </div>
  `;
}

// -------------------------------------------------------------
// 8. RENDER WORD INSPECTION MODAL
// -------------------------------------------------------------
function renderWordInspectionModal(): string {
  if (!inspectedToken) return '';
  const token = inspectedToken;
  const sentence = inspectedSentence;
  const grammarPoints = sentence ? parseGrammarPoints(sentence.grammar_analysis) : [];

  return `
    <div id="word-modal-backdrop" class="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm flex items-end justify-center">
      <div class="bg-surface-card w-full max-w-lg rounded-t-3xl p-5 shadow-2xl flex flex-col gap-4 max-h-[85vh] overflow-y-auto pb-8">
        <!-- Header: Word, Reading, Audio -->
        <div class="flex items-start justify-between pb-3 border-b border-surface-container">
          <div class="flex flex-col">
            <div class="flex items-baseline gap-2.5">
              <span class="text-3xl font-bold text-primary font-mincho">${token.surface}</span>
              ${token.reading && token.reading !== token.surface ? `
                <span class="text-lg font-semibold text-text-secondary">${token.reading}</span>
              ` : ''}
            </div>
            ${token.romaji ? `<span class="text-xs text-text-muted font-mono mt-0.5">${token.romaji}</span>` : ''}
            <div class="flex items-center gap-1.5 mt-2">
              ${token.jlpt && token.jlpt !== '-' ? `
                <span class="px-2 py-0.5 rounded-full bg-crimson-surface text-primary font-bold text-[10px]">JLPT ${token.jlpt}</span>
              ` : ''}
              ${token.pos ? `
                <span class="px-2 py-0.5 rounded-full bg-canvas-secondary text-text-secondary font-semibold text-[10px] uppercase">${token.pos}</span>
              ` : ''}
            </div>
          </div>

          <div class="flex items-center gap-2">
            <button class="btn-play-word-audio w-11 h-11 rounded-full bg-crimson-surface text-primary flex items-center justify-center hover:bg-primary hover:text-white transition-colors" data-text="${token.surface}">
              <span class="material-symbols-outlined text-[22px]">volume_up</span>
            </button>
            <button id="word-modal-close" class="w-8 h-8 rounded-full flex items-center justify-center text-text-muted hover:text-primary">
              <span class="material-symbols-outlined text-[20px]">close</span>
            </button>
          </div>
        </div>

        <!-- Meaning -->
        <div class="bg-canvas-secondary rounded-2xl p-4">
          <span class="text-[10px] font-bold uppercase tracking-wider text-primary">ARTI UTAMA (INDONESIA)</span>
          <p class="text-base font-semibold text-text-primary mt-1 leading-snug">${token.meaning || 'Kosakata bahasa Jepang'}</p>
        </div>

        <!-- Sentence Context -->
        ${sentence ? `
          <div class="flex flex-col gap-1">
            <span class="text-xs font-bold text-text-muted">Konteks Kalimat:</span>
            <p class="text-xs text-text-primary font-mincho leading-relaxed">${sentence.original_text}</p>
            ${sentence.translated_text ? `
              <p class="text-[11px] text-text-secondary leading-snug mt-0.5">${sentence.translated_text}</p>
            ` : ''}
          </div>
        ` : ''}

        <!-- Grammar Points -->
        ${grammarPoints.length > 0 ? `
          <div class="flex flex-col gap-2 pt-2 border-t border-surface-container">
            <span class="text-xs font-bold text-primary uppercase tracking-wider">Analisis Tata Bahasa</span>
            ${grammarPoints.map(gp => `
              <div class="p-2.5 rounded-xl bg-surface-container/50 border border-surface-container flex flex-col gap-0.5">
                <span class="text-xs font-bold text-primary">${gp.pattern}</span>
                <span class="text-[11px] text-text-primary leading-relaxed">${gp.explanation}</span>
              </div>
            `).join('')}
          </div>
        ` : ''}
      </div>
    </div>
  `;
}

// -------------------------------------------------------------
// PROGRESSIVE BACKGROUND ANALYSIS ENGINE (ZERO TIMEOUT CHUNKS)
// -------------------------------------------------------------
async function runProgressiveAnalysis(articleId: string, rawText: string): Promise<void> {
  const sentences = store.getState().articles.find(a => a.id === articleId)?.sentences || [];
  if (sentences.length === 0) return;

  // Chunk into 2-3 sentences max
  const chunks: Sentence[][] = [];
  let cur: Sentence[] = [];
  let curLen = 0;

  for (const s of sentences) {
    if (cur.length > 0 && (curLen + s.original_text.length > 350 || cur.length >= 3)) {
      chunks.push(cur);
      cur = [];
      curLen = 0;
    }
    cur.push(s);
    curLen += s.original_text.length;
  }
  if (cur.length > 0) chunks.push(cur);

  const total = chunks.length;

  for (let i = 0; i < total; i++) {
    const chunk = chunks[i];
    store.updateAnalysisProgress({
      articleId,
      currentChunk: i + 1,
      totalChunks: total,
      isComplete: false
    });

    const chunkText = chunk.map(s => s.original_text).join('');
    try {
      const res = await fetch(`${store.getState().apiBaseUrl}/api/v1/analyze`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text: chunkText })
      });

      if (res.ok) {
        interface AnalyzeResponsePayload {
          sentences?: Array<{
            translated_text: string;
            tokens: Token[];
            grammar_points: GrammarPoint[];
          }>;
        }
        const data = (await res.json()) as AnalyzeResponsePayload;
        if (data.sentences && Array.isArray(data.sentences)) {
          chunk.forEach((s, sIdx) => {
            const analyzed = data.sentences ? data.sentences[sIdx] : undefined;
            if (analyzed) {
              s.translated_text = analyzed.translated_text;
              s.furigana_payload = JSON.stringify(analyzed.tokens);
              s.grammar_analysis = JSON.stringify(analyzed.grammar_points);
            }
          });
          const active = store.getState().activeArticle;
          if (active && active.id === articleId) {
            active.sentences = [...sentences];
          }
          store.notify();
        }
      }
    } catch {
      // Continue next chunk gracefully
    }
  }

  store.updateAnalysisProgress({
    articleId,
    currentChunk: total,
    totalChunks: total,
    isComplete: true
  });

  setTimeout(() => {
    if (store.getState().analysisProgress?.articleId === articleId) {
      store.updateAnalysisProgress(null);
    }
  }, 2500);
}

// -------------------------------------------------------------
// MAIN RENDER LOOP & EVENT ATTACHMENTS
// -------------------------------------------------------------
function renderApp(state: AppState): void {
  const container = getEl('view-container');
  if (!container) return;

  const topAppBar = getEl('top-app-bar');
  const bottomNav = getEl('bottom-nav');
  const mainEl = document.querySelector('main');

  // Handle Full-Width Zen Mode in Reader
  if (state.currentView === 'reader') {
    topAppBar?.classList.add('hidden');
    bottomNav?.classList.add('hidden');
    mainEl?.classList.remove('pb-28', 'pt-3', 'px-4');
    mainEl?.classList.add('p-0', 'max-w-none');
  } else {
    topAppBar?.classList.remove('hidden');
    bottomNav?.classList.remove('hidden');
    mainEl?.classList.add('pb-28', 'pt-3', 'px-4');
    mainEl?.classList.remove('p-0', 'max-w-none');
  }

  // Update Top Bar Sync status
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
      syncText.innerText = 'Luring';
    }
  }

  // Highlight active bottom nav item
  const navButtons = document.querySelectorAll('.nav-item');
  navButtons.forEach(btn => {
    const view = (btn as HTMLElement).dataset.view;
    if (view === state.currentView) {
      btn.classList.add('text-primary');
      btn.classList.remove('text-text-muted');
      const label = btn.querySelector('span:last-child');
      if (label) label.classList.add('font-bold');
    } else {
      btn.classList.remove('text-primary');
      btn.classList.add('text-text-muted');
      const label = btn.querySelector('span:last-child');
      if (label) label.classList.remove('font-bold');
    }
  });

  // Render view
  let html = '';
  switch (state.currentView) {
    case 'splash':
      html = renderSplash(state);
      break;
    case 'koleksi':
      html = renderKoleksi(state);
      break;
    case 'flashcards':
    case 'vocab':
      html = renderFlashcards(state);
      break;
    case 'progress':
    case 'analytics':
      html = renderAnalytics(state);
      break;
    case 'settings':
      html = renderSettings(state);
      break;
    case 'reader':
      html = renderReader(state);
      break;
    default:
      html = renderKoleksi(state);
  }

  container.innerHTML = html;

  // Render Modal Container
  const modalContainer = getEl('modal-container');
  if (modalContainer) {
    if (showAddTextModal) {
      modalContainer.innerHTML = renderAddTextModal();
      modalContainer.classList.remove('hidden');
    } else if (inspectedToken) {
      modalContainer.innerHTML = renderWordInspectionModal();
      modalContainer.classList.remove('hidden');
    } else {
      modalContainer.innerHTML = '';
      modalContainer.classList.add('hidden');
    }
  }

  attachEvents(state);
}

// -------------------------------------------------------------
// EVENT DISPATCHER
// -------------------------------------------------------------
function attachEvents(state: AppState): void {
  // Navigation tabs
  document.querySelectorAll('.nav-item').forEach(btn => {
    btn.addEventListener('click', () => {
      const view = (btn as HTMLElement).dataset.view as AppState['currentView'] | undefined;
      if (view) {
        showAddTextModal = false;
        inspectedToken = null;
        store.setView(view);
      }
    });
  });

  // Center (+) FAB Button
  getEl('nav-add-btn')?.addEventListener('click', () => {
    showAddTextModal = true;
    renderApp(store.getState());
  });

  // Splash buttons
  getEl('splash-start-btn')?.addEventListener('click', () => {
    store.setView('koleksi');
  });

  // Collection Search
  const searchInput = getEl<HTMLInputElement>('collection-search-input');
  if (searchInput) {
    searchInput.addEventListener('input', () => {
      searchQuery = searchInput.value;
      renderApp(store.getState());
    });
  }

  // Collection Category Filter
  document.querySelectorAll('.collection-cat-pill').forEach(btn => {
    btn.addEventListener('click', () => {
      selectedCategory = (btn as HTMLElement).dataset.category || 'Semua';
      renderApp(store.getState());
    });
  });

  // Open Article from Collection
  document.querySelectorAll('.article-card').forEach(card => {
    card.addEventListener('click', () => {
      const artId = (card as HTMLElement).dataset.artId;
      if (artId) {
        readerExpandedSentences.clear();
        readerShowPillBar = true;
        store.openArticle(artId);
      }
    });
  });

  // Reader Back Button
  getEl('reader-back-btn')?.addEventListener('click', () => {
    store.setView('koleksi');
  });
  getEl('reader-back-to-library')?.addEventListener('click', () => {
    store.setView('koleksi');
  });

  // Reader Canvas Click (toggle floating pill bar on whitespace)
  const readerCanvas = getEl('reader-article-content');
  if (readerCanvas) {
    readerCanvas.addEventListener('click', (e) => {
      const target = e.target as HTMLElement;
      if (!target.closest('.token-word') && !target.closest('button') && !target.closest('a')) {
        readerShowPillBar = !readerShowPillBar;
        const pill = getEl('reader-floating-pill');
        if (pill) {
          if (readerShowPillBar) pill.classList.remove('hidden');
          else pill.classList.add('hidden');
        }
      }
    });
  }

  // Pill Bar Toggles
  getEl('pill-furi-btn')?.addEventListener('click', (e) => {
    e.stopPropagation();
    readerFuriganaMode = readerFuriganaMode === 'always' ? 'off' : 'always';
    renderApp(store.getState());
  });

  getEl('pill-trans-btn')?.addEventListener('click', (e) => {
    e.stopPropagation();
    readerTranslateMode = !readerTranslateMode;
    const article = store.getState().activeArticle;
    if (readerTranslateMode && article?.sentences) {
      article.sentences.forEach(s => readerExpandedSentences.add(s.id));
    } else {
      readerExpandedSentences.clear();
    }
    renderApp(store.getState());
  });

  getEl('pill-font-minus')?.addEventListener('click', (e) => {
    e.stopPropagation();
    readerFontSizeSp = Math.max(16, readerFontSizeSp - 2);
    renderApp(store.getState());
  });

  getEl('pill-font-plus')?.addEventListener('click', (e) => {
    e.stopPropagation();
    readerFontSizeSp = Math.min(32, readerFontSizeSp + 2);
    renderApp(store.getState());
  });

  // Toggle Single Sentence Inline Translation
  document.querySelectorAll('.btn-toggle-inline-trans').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const sentId = (btn as HTMLElement).dataset.sentId;
      if (sentId) {
        if (readerExpandedSentences.has(sentId)) {
          readerExpandedSentences.delete(sentId);
        } else {
          readerExpandedSentences.add(sentId);
        }
        renderApp(store.getState());
      }
    });
  });

  // Token Click -> Open Word Inspection Modal & Auto-Save
  document.querySelectorAll('.token-word').forEach(el => {
    el.addEventListener('click', (e) => {
      e.stopPropagation();
      const tokenRaw = (el as HTMLElement).dataset.token;
      const sentId = (el as HTMLElement).dataset.sentId;
      if (tokenRaw) {
        try {
          const token: Token = JSON.parse(decodeURIComponent(tokenRaw));
          inspectedToken = token;
          const article = store.getState().activeArticle;
          inspectedSentence = article?.sentences?.find(s => s.id === sentId) || null;

          // Auto-save word to Flashcard deck
          if (token.surface && !['、', '。', '！', '？', 'は', 'が', 'の', 'に', 'で', 'を', 'と'].includes(token.surface)) {
            const now = Date.now();
            store.addVocabulary({
              id: `voc_${now}_${Math.random().toString(36).substring(2, 6)}`,
              user_id: 'usr_default',
              sentence_id: sentId || null,
              kanji: token.surface,
              reading: token.reading || token.surface,
              meaning: token.meaning || inspectedSentence?.translated_text || 'Kosakata bahasa Jepang',
              part_of_speech: token.pos || 'noun',
              jlpt_level: token.jlpt || 'N5',
              mastery_status: 0,
              review_count: 0,
              next_review_at: now + 86400000,
              created_at: now,
              updated_at: now
            });
          }

          renderApp(store.getState());
        } catch {}
      }
    });
  });

  // Article Footer Buttons
  getEl('reader-finish-btn')?.addEventListener('click', () => {
    store.setView('koleksi');
  });

  getEl('reader-show-words-btn')?.addEventListener('click', () => {
    store.setView('flashcards');
  });

  // Word Inspection Modal Close & Audio
  getEl('word-modal-close')?.addEventListener('click', () => {
    inspectedToken = null;
    inspectedSentence = null;
    renderApp(store.getState());
  });
  getEl('word-modal-backdrop')?.addEventListener('click', (e) => {
    if (e.target === e.currentTarget) {
      inspectedToken = null;
      inspectedSentence = null;
      renderApp(store.getState());
    }
  });
  document.querySelectorAll('.btn-play-word-audio').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const text = (btn as HTMLElement).dataset.text;
      if (text) playAudioPronunciation(text, store.getState().settings.tts_speed);
    });
  });

  // Flashcards 3D Flip
  getEl('flashcard-flip-container')?.addEventListener('click', () => {
    isFlashcardFlipped = !isFlashcardFlipped;
    const flipper = getEl('flashcard-flipper');
    if (flipper) {
      if (isFlashcardFlipped) flipper.classList.add('rotate-y-180');
      else flipper.classList.remove('rotate-y-180');
    }
  });

  // Flashcard Filters
  document.querySelectorAll('.fc-filter-pill').forEach(btn => {
    btn.addEventListener('click', () => {
      selectedFlashcardFilter = (btn as HTMLElement).dataset.filter || 'Semua';
      currentFlashcardIndex = 0;
      isFlashcardFlipped = false;
      renderApp(store.getState());
    });
  });

  // Flashcard Prev/Next
  getEl('fc-prev-btn')?.addEventListener('click', (e) => {
    e.stopPropagation();
    if (currentFlashcardIndex > 0) {
      currentFlashcardIndex--;
      isFlashcardFlipped = false;
      renderApp(store.getState());
    }
  });
  getEl('fc-next-btn')?.addEventListener('click', (e) => {
    e.stopPropagation();
    currentFlashcardIndex++;
    isFlashcardFlipped = false;
    renderApp(store.getState());
  });

  // Flashcard Audio
  document.querySelectorAll('.btn-play-fc-audio').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const kanji = (btn as HTMLElement).dataset.kanji;
      if (kanji) playAudioPronunciation(kanji, store.getState().settings.tts_speed);
    });
  });

  // Flashcard SRS Rating (Lupa / Ingat)
  document.querySelectorAll('.btn-srs-rating').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const q = parseInt((btn as HTMLElement).dataset.quality || '3', 10);
      const id = (btn as HTMLElement).dataset.id;
      const vocab = store.getState().vocabularies.find(v => v.id === id);
      if (vocab) {
        const { nextReviewAt, nextMastery } = calculateNextSrsInterval(vocab.review_count, q);
        store.updateVocabulary({
          ...vocab,
          mastery_status: nextMastery,
          review_count: vocab.review_count + 1,
          next_review_at: nextReviewAt
        });

        isFlashcardFlipped = false;
        renderApp(store.getState());
      }
    });
  });

  // Settings Actions
  document.querySelectorAll('.font-setting-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const font = (btn as HTMLElement).dataset.font as 'mincho' | 'gothic';
      if (font) {
        store.updateSettings({ kanji_font_style: font });
      }
    });
  });

  const speedRange = getEl<HTMLInputElement>('settings-speed-range');
  if (speedRange) {
    speedRange.addEventListener('input', () => {
      const val = parseFloat(speedRange.value);
      store.updateSettings({ tts_speed: val });
    });
  }

  getEl('settings-sync-btn')?.addEventListener('click', () => {
    store.syncWithBackend();
  });
  getEl('sync-trigger-btn')?.addEventListener('click', () => {
    store.syncWithBackend();
  });

  // Add Text Modal Close
  getEl('modal-close-btn')?.addEventListener('click', () => {
    showAddTextModal = false;
    renderApp(store.getState());
  });
  getEl('modal-backdrop')?.addEventListener('click', (e) => {
    if (e.target === e.currentTarget) {
      showAddTextModal = false;
      renderApp(store.getState());
    }
  });

  // Add Text Submit -> Instant Open in 0ms + Progressive Analysis!
  getEl('modal-submit-btn')?.addEventListener('click', () => {
    const titleInput = getEl<HTMLInputElement>('modal-title-input');
    const textInput = getEl<HTMLTextAreaElement>('modal-text-input');
    const rawText = textInput ? textInput.value.trim() : '';
    if (!rawText) {
      alert('Teks bahasa Jepang tidak boleh kosong.');
      return;
    }

    const title = titleInput?.value.trim() || (rawText.length > 20 ? rawText.substring(0, 20) + '...' : rawText);
    const now = Date.now();
    const artId = `art_${now}_${Math.random().toString(36).substring(2, 6)}`;

    // Natural sentence splitting:
    const rawSentences = rawText.split(/(?<=[。！？\n])/).map(s => s.trim()).filter(s => s.length > 0);
    const sentenceList = rawSentences.length > 0 ? rawSentences : [rawText];
    const sentences: Sentence[] = sentenceList.map((s, idx) => {
      const initialTokens = tokenizeSentence(s);
      const initialGrammar = detectGrammarPoints(s);
      return {
        id: `sent_${artId}_${idx + 1}`,
        article_id: artId,
        original_text: s,
        translated_text: '',
        furigana_payload: JSON.stringify(initialTokens),
        grammar_analysis: JSON.stringify(initialGrammar),
        sequence_order: idx + 1,
        inspection_count: 0,
        audio_play_count: 0,
        needs_deep_study: 0,
        updated_at: now
      };
    });
    const article: Article = {
      id: artId,
      user_id: 'usr_default',
      title,
      category: 'Umum',
      raw_text: rawText,
      difficulty_level: '-',
      kanji_ratio: 0.0,
      created_at: now,
      updated_at: now,
      sentences
    };

    // Instant save & instant open (0ms)
    store.addArticle(article, sentences);
    showAddTextModal = false;
    readerExpandedSentences.clear();
    readerShowPillBar = true;
    store.openArticle(artId);

    // Launch background progressive analysis
    runProgressiveAnalysis(artId, rawText);
  });
}

// -------------------------------------------------------------
// STORE SUBSCRIPTION & INITIALIZATION
// -------------------------------------------------------------
store.subscribe(renderApp);

if (typeof document !== 'undefined') {
  renderApp(store.getState());
  store.syncWithBackend();
}
