import { AnalyzeRequest, AnalyzeResponse } from './types';
import { analyzeJapaneseTextFallback } from './tokenizer';

const SYSTEM_PROMPT = `You are a Japanese Morphological Analyzer and Linguistic Tutor.
Return only valid JSON strictly matching this schema:
{
  "difficulty_level": "N5" | "N4" | "N3" | "N2" | "N1",
  "kanji_ratio": number,
  "sentences": [
    {
      "sequence_order": number,
      "original_text": string,
      "translated_text": string,
      "tokens": [
        {
          "surface": string,
          "reading": string,
          "romaji": string,
          "pos": "noun" | "verb" | "particle" | "i-adj" | "na-adj" | "adverb" | "copula" | "punct" | "other",
          "meaning": string,
          "base_form"?: string,
          "form"?: string,
          "jlpt": "N5" | "N4" | "N3" | "N2" | "N1" | "-"
        }
      ],
      "grammar_points": [
        {
          "pattern": string,
          "explanation": string
        }
      ]
    }
  ]
}
Target translation language is Indonesian unless requested otherwise. Do not include markdown code blocks.`;

export async function analyzeWithDeepSeek(
  req: AnalyzeRequest,
  envApiKey?: string
): Promise<AnalyzeResponse> {
  const apiKey = req.api_key || envApiKey;

  if (!apiKey || apiKey.trim() === '' || apiKey.startsWith('sk-optional')) {
    // No valid API key provided -> use resilient local morphological analyzer
    return analyzeJapaneseTextFallback(req.text);
  }

  try {
    const toneInstruction = req.tone ? `Contextual tone: ${req.tone}.` : '';
    const langInstruction = req.target_language ? `Target translation language: ${req.target_language}.` : '';

    const response = await fetch('https://api.deepseek.com/v1/chat/completions', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${apiKey}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        model: 'deepseek-chat',
        response_format: { type: 'json_object' },
        messages: [
          { role: 'system', content: `${SYSTEM_PROMPT}\n${toneInstruction}\n${langInstruction}`.trim() },
          { role: 'user', content: req.text }
        ],
        temperature: 0.2
      })
    });

    if (!response.ok) {
      console.warn(`DeepSeek API returned HTTP ${response.status}. Falling back to deterministic tokenizer.`);
      return analyzeJapaneseTextFallback(req.text);
    }

    const payload = await response.json() as {
      choices?: Array<{ message?: { content?: string } }>;
    };

    const contentStr = payload.choices?.[0]?.message?.content;
    if (!contentStr) {
      return analyzeJapaneseTextFallback(req.text);
    }

    let cleanJson = contentStr.trim();
    // Robustly strip markdown fences or extraneous text around JSON
    if (cleanJson.startsWith('```')) {
      cleanJson = cleanJson.replace(/^```(?:json)?\s*/i, '').replace(/\s*```$/, '').trim();
    }
    const firstBrace = cleanJson.indexOf('{');
    const lastBrace = cleanJson.lastIndexOf('}');
    if (firstBrace !== -1 && lastBrace !== -1) {
      cleanJson = cleanJson.substring(firstBrace, lastBrace + 1);
    }

    const parsed = JSON.parse(cleanJson) as AnalyzeResponse;
    if (parsed.sentences && Array.isArray(parsed.sentences) && parsed.sentences.length > 0) {
      return parsed;
    }
    return analyzeJapaneseTextFallback(req.text);
  } catch (err) {
    console.error('DeepSeek request exception, using fallback analyzer:', err);
    return analyzeJapaneseTextFallback(req.text);
  }
}
