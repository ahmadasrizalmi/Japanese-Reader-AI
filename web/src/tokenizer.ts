import { Token, GrammarPoint } from './state';

const KANA_TO_ROMAJI: Record<string, string> = {
  'あ': 'a', 'い': 'i', 'う': 'u', 'え': 'e', 'お': 'o',
  'か': 'ka', 'き': 'ki', 'く': 'ku', 'け': 'ke', 'こ': 'ko',
  'さ': 'sa', 'し': 'shi', 'す': 'su', 'せ': 'se', 'そ': 'so',
  'た': 'ta', 'ち': 'chi', 'つ': 'tsu', 'て': 'te', 'と': 'to',
  'な': 'na', 'に': 'ni', 'ぬ': 'nu', 'ね': 'ne', 'の': 'no',
  'は': 'ha', 'ひ': 'hi', 'ふ': 'fu', 'へ': 'he', 'ほ': 'ho',
  'ま': 'ma', 'み': 'mi', 'む': 'mu', 'め': 'me', 'も': 'mo',
  'や': 'ya', 'ゆ': 'yu', 'よ': 'yo',
  'ら': 'ra', 'り': 'ri', 'る': 'ru', 'れ': 're', 'ろ': 'ro',
  'わ': 'wa', 'を': 'wo', 'ん': 'n',
  'が': 'ga', 'ぎ': 'gi', 'ぐ': 'gu', 'げ': 'ge', 'ご': 'go',
  'ざ': 'za', 'じ': 'ji', 'ず': 'zu', 'ぜ': 'ze', 'ぞ': 'zo',
  'だ': 'da', 'ぢ': 'ji', 'づ': 'zu', 'де': 'de', 'ど': 'do',
  'ば': 'ba', 'び': 'bi', 'ぶ': 'bu', 'べ': 'be', 'ぼ': 'bo',
  'ぱ': 'pa', 'ぴ': 'pi', 'ぷ': 'pu', 'ぺ': 'pe', 'ぽ': 'po',
  'ぁ': 'a', 'ぃ': 'i', 'ぅ': 'u', 'ぇ': 'e', 'ぉ': 'o',
  'ゃ': 'ya', 'ゅ': 'yu', 'ょ': 'yo',
  'きゃ': 'kya', 'きゅ': 'kyu', 'きょ': 'kyo',
  'しゃ': 'sha', 'しゅ': 'shu', 'しょ': 'sho',
  'ちゃ': 'cha', 'ちゅ': 'chu', 'ちょ': 'cho',
  'にゃ': 'nya', 'にゅ': 'nyu', 'にょ': 'nyo',
  'ひゃ': 'hya', 'ひゅ': 'hyu', 'ひょ': 'hyo',
  'みゃ': 'mya', 'みゅ': 'myu', 'みょ': 'myo',
  'りゃ': 'rya', 'りゅ': 'ryu', 'りょ': 'ryo',
  'ぎゃ': 'gya', 'ぎゅ': 'gyu', 'ぎょ': 'gyo',
  'じゃ': 'ja', 'じゅ': 'ju', 'じょ': 'jo',
  'びゃ': 'bya', 'びゅ': 'byu', 'びょ': 'byo',
  'ぴゃ': 'pya', 'ぴゅ': 'pyu', 'ぴょ': 'pyo'
};

export function kanaToRomaji(kana: string): string {
  let romaji = '';
  let i = 0;
  while (i < kana.length) {
    if (i + 1 < kana.length) {
      const combo = kana.slice(i, i + 2);
      if (KANA_TO_ROMAJI[combo]) {
        romaji += KANA_TO_ROMAJI[combo];
        i += 2;
        continue;
      }
    }
    const single = kana[i];
    if (single === 'っ' || single === 'ッ') {
      if (i + 1 < kana.length) {
        const nextChar = kana[i + 1];
        const nextRomaji = kanaToRomaji(nextChar);
        if (nextRomaji.length > 0) {
          romaji += nextRomaji[0];
        }
      }
      i++;
      continue;
    }
    if (single === 'ー') {
      if (romaji.length > 0) {
        romaji += romaji[romaji.length - 1];
      }
      i++;
      continue;
    }
    romaji += KANA_TO_ROMAJI[single] || single;
    i++;
  }
  return romaji;
}

interface LexiconItem {
  surface: string;
  reading: string;
  pos: string;
  meaning: string;
  jlpt: string;
  baseForm?: string;
  form?: string;
}

const DICTIONARY: LexiconItem[] = [
  { surface: '東京', reading: 'とうきょう', pos: 'noun', meaning: 'Tokyo (ibukota Jepang)', jlpt: 'N5' },
  { surface: '日本', reading: 'にほん', pos: 'noun', meaning: 'Jepang', jlpt: 'N5' },
  { surface: '春', reading: 'はる', pos: 'noun', meaning: 'Musim semi', jlpt: 'N5' },
  { surface: '夏', reading: 'なつ', pos: 'noun', meaning: 'Musim panas', jlpt: 'N5' },
  { surface: '秋', reading: 'あき', pos: 'noun', meaning: 'Musim gugur', jlpt: 'N5' },
  { surface: '冬', reading: 'ふゆ', pos: 'noun', meaning: 'Musim dingin', jlpt: 'N5' },
  { surface: '花', reading: 'はな', pos: 'noun', meaning: 'Bunga', jlpt: 'N5' },
  { surface: '桜', reading: 'さくら', pos: 'noun', meaning: 'Bunga sakura', jlpt: 'N5' },
  { surface: '公園', reading: 'こうえん', pos: 'noun', meaning: 'Taman', jlpt: 'N5' },
  { surface: 'カフェ', reading: 'カフェ', pos: 'noun', meaning: 'Kafe', jlpt: 'N5' },
  { surface: '今週', reading: 'こんしゅう', pos: 'noun', meaning: 'Minggu ini', jlpt: 'N5' },
  { surface: '来週', reading: 'らいしゅう', pos: 'noun', meaning: 'Minggu depan', jlpt: 'N5' },
  { surface: '今週末', reading: 'こんしゅうまつ', pos: 'noun', meaning: 'Akhir pekan ini', jlpt: 'N4' },
  { surface: '土曜日', reading: 'どようび', pos: 'noun', meaning: 'Hari Sabtu', jlpt: 'N5' },
  { surface: '日曜日', reading: 'にちようび', pos: 'noun', meaning: 'Hari Minggu', jlpt: 'N5' },
  { surface: '時間', reading: 'じかん', pos: 'noun', meaning: 'Waktu / jam', jlpt: 'N5' },
  { surface: '試験', reading: 'しけん', pos: 'noun', meaning: 'Ujian', jlpt: 'N5' },
  { surface: '準備', reading: 'じゅんび', pos: 'noun', meaning: 'Persiapan', jlpt: 'N4' },
  { surface: '秋葉原', reading: 'あきはばら', pos: 'noun', meaning: 'Akihabara', jlpt: 'N4' },
  { surface: '渋谷', reading: 'しぶや', pos: 'noun', meaning: 'Shibuya', jlpt: 'N4' },
  { surface: '田中', reading: 'たなか', pos: 'noun', meaning: 'Tanaka (nama keluarga)', jlpt: 'N5' },
  { surface: '健一', reading: 'けんいち', pos: 'noun', meaning: 'Kenichi (nama pria)', jlpt: 'N5' },
  { surface: '葵', reading: 'あおい', pos: 'noun', meaning: 'Aoi (nama orang)', jlpt: 'N4' },
  { surface: '雨', reading: 'あめ', pos: 'noun', meaning: 'Hujan', jlpt: 'N5' },
  { surface: '予報', reading: 'よほう', pos: 'noun', meaning: 'Prakiraan (cuaca)', jlpt: 'N4' },
  { surface: '傘', reading: 'かさ', pos: 'noun', meaning: 'Payung', jlpt: 'N5' },
  { surface: '午後', reading: 'ごご', pos: 'noun', meaning: 'Sore / PM', jlpt: 'N5' },
  { surface: '午前', reading: 'ごぜん', pos: 'noun', meaning: 'Pagi / AM', jlpt: 'N5' },
  { surface: '駅', reading: 'えき', pos: 'noun', meaning: 'Stasiun', jlpt: 'N5' },
  { surface: '改札', reading: 'かいさつ', pos: 'noun', meaning: 'Gerbang tiket stasiun', jlpt: 'N3' },
  { surface: '前', reading: 'まえ', pos: 'noun', meaning: 'Depan / sebelumnya', jlpt: 'N5' },
  { surface: '人間', reading: 'にんげん', pos: 'noun', meaning: 'Manusia', jlpt: 'N3' },
  { surface: '生き物', reading: 'いきもの', pos: 'noun', meaning: 'Makhluk hidup', jlpt: 'N3' },
  { surface: '季節', reading: 'きせつ', pos: 'noun', meaning: 'Musim', jlpt: 'N4' },
  { surface: '満開', reading: 'まんかい', pos: 'noun', meaning: 'Mekar penuh', jlpt: 'N3' },
  { surface: '架空', reading: 'かくう', pos: 'noun', meaning: 'Fiktif / imajinasi', jlpt: 'N1' },
  { surface: '普通', reading: 'ふつう', pos: 'noun', meaning: 'Biasa / normal', jlpt: 'N4' },
  { surface: '物語', reading: 'ものがたり', pos: 'noun', meaning: 'Cerita / kisah', jlpt: 'N3' },
  { surface: '文法', reading: 'ぶんぽう', pos: 'noun', meaning: 'Tata bahasa', jlpt: 'N4' },
  { surface: '咲いて', reading: 'さいて', pos: 'verb', meaning: 'Mekar (te-form)', jlpt: 'N4', baseForm: '咲く', form: 'te-form' },
  { surface: '咲く', reading: 'さく', pos: 'verb', meaning: 'Mekar', jlpt: 'N4', baseForm: '咲く', form: 'dictionary' },
  { surface: '行きます', reading: 'いきます', pos: 'verb', meaning: 'Pergi (sopan)', jlpt: 'N5', baseForm: '行く', form: 'masu-form' },
  { surface: '行きませんか', reading: 'いきませんか', pos: 'verb', meaning: 'Maukah pergi? (ajakan)', jlpt: 'N5', baseForm: '行く', form: 'invitation' },
  { surface: '行きましょう', reading: 'いきましょう', pos: 'verb', meaning: 'Ayo kita pergi!', jlpt: 'N5', baseForm: '行く', form: 'volitional' },
  { surface: '行く', reading: 'いく', pos: 'verb', meaning: 'Pergi', jlpt: 'N5', baseForm: '行く', form: 'dictionary' },
  { surface: '進んでいますか', reading: 'すすんでいますか', pos: 'verb', meaning: 'Apakah mengalami kemajuan?', jlpt: 'N4', baseForm: '進む', form: 'progressive-question' },
  { surface: '進む', reading: 'すすむ', pos: 'verb', meaning: 'Maju / berkembang', jlpt: 'N4', baseForm: '進む', form: 'dictionary' },
  { surface: 'あれば', reading: 'あれば', pos: 'verb', meaning: 'Jika ada (kondisional)', jlpt: 'N4', baseForm: 'ある', form: 'ba-form' },
  { surface: 'ある', reading: 'ある', pos: 'verb', meaning: 'Ada (benda mati)', jlpt: 'N5', baseForm: 'ある', form: 'dictionary' },
  { surface: '話しませんか', reading: 'はなしませんか', pos: 'verb', meaning: 'Maukah berbincang?', jlpt: 'N5', baseForm: '話す', form: 'invitation' },
  { surface: '話す', reading: 'はなす', pos: 'verb', meaning: 'Berbicara / mengobrol', jlpt: 'N5', baseForm: '話す', form: 'dictionary' },
  { surface: '待っています', reading: 'まっています', pos: 'verb', meaning: 'Sedang menunggu', jlpt: 'N5', baseForm: '待つ', form: 'te-iru' },
  { surface: '新しい', reading: 'あたらしい', pos: 'i-adj', meaning: 'Baru', jlpt: 'N5' },
  { surface: 'きれい', reading: 'きれい', pos: 'na-adj', meaning: 'Indah / cantik / bersih', jlpt: 'N5' },
  { surface: 'とても', reading: 'とても', pos: 'adverb', meaning: 'Sangat', jlpt: 'N5' },
  { surface: 'もし', reading: 'もし', pos: 'adverb', meaning: 'Jika / seandainya', jlpt: 'N5' },
  { surface: 'はい', reading: 'はい', pos: 'expression', meaning: 'Ya', jlpt: 'N5' },
  { surface: 'ぜひ', reading: 'ぜひ', pos: 'adverb', meaning: 'Dengan senang hati', jlpt: 'N4' },
  { surface: '一緒に', reading: 'いっしょに', pos: 'adverb', meaning: 'Bersama-sama', jlpt: 'N5' },
  { surface: 'お疲れ様です', reading: 'おつかれさまです', pos: 'expression', meaning: 'Terima kasih atas kerja kerasnya!', jlpt: 'N4' },
  { surface: '明日', reading: 'あした', pos: 'noun', meaning: 'Besok', jlpt: 'N5' },
  { surface: '今日', reading: 'きょう', pos: 'noun', meaning: 'Hari ini', jlpt: 'N5' },
  { surface: 'は', reading: 'は', pos: 'particle', meaning: 'Partikel topik (dibaca "wa")', jlpt: 'N5' },
  { surface: 'が', reading: 'が', pos: 'particle', meaning: 'Partikel penanda subjek', jlpt: 'N5' },
  { surface: 'の', reading: 'の', pos: 'particle', meaning: 'Partikel kepemilikan / asosiasi', jlpt: 'N5' },
  { surface: 'に', reading: 'に', pos: 'particle', meaning: 'Partikel arah, target, atau waktu', jlpt: 'N5' },
  { surface: 'で', reading: 'で', pos: 'particle', meaning: 'Partikel lokasi tindakan / sarana', jlpt: 'N5' },
  { surface: 'を', reading: 'を', pos: 'particle', meaning: 'Partikel penanda objek langsung', jlpt: 'N5' },
  { surface: 'へ', reading: 'へ', pos: 'particle', meaning: 'Partikel tujuan arah (dibaca "e")', jlpt: 'N5' },
  { surface: 'と', reading: 'と', pos: 'particle', meaning: 'Dan / dengan', jlpt: 'N5' },
  { surface: 'や', reading: 'や', pos: 'particle', meaning: 'Dan (daftar tidak lengkap)', jlpt: 'N5' },
  { surface: 'か', reading: 'か', pos: 'particle', meaning: 'Partikel penanda tanya (?)', jlpt: 'N5' },
  { surface: 'です', reading: 'です', pos: 'copula', meaning: 'Adalah (kopula formal)', jlpt: 'N5' },
  { surface: 'でした', reading: 'でした', pos: 'copula', meaning: 'Adalah (bentuk lampau formal)', jlpt: 'N5' },
  { surface: 'という', reading: 'という', pos: 'particle', meaning: 'Disebut / yang dinamakan', jlpt: 'N4' },
  { surface: '、', reading: '', pos: 'punct', meaning: 'Koma Jepang', jlpt: '-' },
  { surface: '。', reading: '', pos: 'punct', meaning: 'Titik Jepang', jlpt: '-' },
  { surface: '！', reading: '', pos: 'punct', meaning: 'Tanda seru', jlpt: '-' },
  { surface: '？', reading: '', pos: 'punct', meaning: 'Tanda tanya', jlpt: '-' }
];

DICTIONARY.sort((a, b) => b.surface.length - a.surface.length);

export function isKanji(char: string): boolean {
  return /[\u4e00-\u9faf\u3400-\u4dbf]/.test(char);
}

export function tokenizeSentence(sentence: string): Token[] {
  const tokens: Token[] = [];
  let index = 0;

  while (index < sentence.length) {
    const char = sentence[index];
    if (/\s/.test(char)) {
      index++;
      continue;
    }

    let matched: LexiconItem | null = null;
    for (const item of DICTIONARY) {
      if (sentence.startsWith(item.surface, index)) {
        matched = item;
        break;
      }
    }

    if (matched) {
      const romaji = matched.reading ? kanaToRomaji(matched.reading) : '';
      tokens.push({
        surface: matched.surface,
        reading: matched.reading,
        romaji: romaji,
        pos: matched.pos,
        meaning: matched.meaning,
        jlpt: matched.jlpt
      });
      index += matched.surface.length;
      continue;
    }

    const ch = sentence[index];
    const cp = ch.charCodeAt(0);
    const isKatakana = (cp >= 0x30A0 && cp <= 0x30FF) || ch === 'ー';
    const isCharKanji = (cp >= 0x4E00 && cp <= 0x9FAF) || (cp >= 0x3400 && cp <= 0x4DBF);

    if (isKatakana) {
      let end = index;
      while (end < sentence.length && (((sentence.charCodeAt(end) >= 0x30A0 && sentence.charCodeAt(end) <= 0x30FF)) || sentence[end] === 'ー')) {
        end++;
      }
      const word = sentence.substring(index, end);
      tokens.push({
        surface: word,
        reading: word,
        romaji: kanaToRomaji(word),
        pos: 'noun',
        meaning: word,
        jlpt: 'N4'
      });
      index = end;
      continue;
    }

    if (isCharKanji) {
      let end = index;
      while (end < sentence.length && ((sentence.charCodeAt(end) >= 0x4E00 && sentence.charCodeAt(end) <= 0x9FAF) || (sentence.charCodeAt(end) >= 0x3400 && sentence.charCodeAt(end) <= 0x4DBF))) {
        end++;
      }
      const word = sentence.substring(index, end);
      tokens.push({
        surface: word,
        reading: word,
        romaji: word,
        pos: 'noun',
        meaning: `Kosakata: ${word}`,
        jlpt: 'N3'
      });
      index = end;
      continue;
    }

    if (/[a-zA-Z0-9]/.test(ch)) {
      let end = index;
      while (end < sentence.length && /[a-zA-Z0-9]/.test(sentence[end])) {
        end++;
      }
      const word = sentence.substring(index, end);
      tokens.push({
        surface: word,
        reading: word,
        romaji: word,
        pos: 'other',
        meaning: word,
        jlpt: '-'
      });
      index = end;
      continue;
    }

    const singleChar = sentence[index];
    tokens.push({
      surface: singleChar,
      reading: singleChar,
      romaji: kanaToRomaji(singleChar),
      pos: /[\u3040-\u309f]/.test(singleChar) ? 'particle' : 'punct',
      meaning: '',
      jlpt: '-'
    });
    index++;
  }

  return tokens;
}

export function detectGrammarPoints(text: string): GrammarPoint[] {
  const points: GrammarPoint[] = [];
  if (text.includes('〜ませんか') || text.includes('ませんか')) {
    points.push({ pattern: '〜ませんか (~masen ka)', explanation: 'Pola ajakan sopan: Maukah kamu / Bagaimana kalau kita...?' });
  }
  if (text.includes('咲いて') || text.includes('て、') || text.includes('て ')) {
    points.push({ pattern: '〜て (Te-form)', explanation: 'Bentuk konjungtif penghubung klausa (urutan atau sebab-akibat).' });
  }
  if (text.includes('進んでいます') || text.includes('ています') || text.includes('待っています')) {
    points.push({ pattern: '〜ている (~te iru)', explanation: 'Bentuk progresif atau kondisi yang sedang berlangsung.' });
  }
  if (text.includes('あれば') || text.includes('ば')) {
    points.push({ pattern: '〜ば (Ba-conditional)', explanation: 'Bentuk pengandaian (jika / seandainya).' });
  }
  if (text.includes('行きましょう') || text.includes('ましょう')) {
    points.push({ pattern: '〜ましょう (~mashou)', explanation: 'Bentuk ajakan positif: Mari kita lakukan!' });
  }
  return points;
}
