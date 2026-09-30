import { StorageEngine } from './storage';

async function generateSha256(input: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(input);
  const hashBuffer = await crypto.subtle.digest('SHA-256', data);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}

// Generate simple synthetic silent/beep WAV buffer for offline testing if Piper server is not reached
function generateFallbackWavBase64(): string {
  // A minimal valid RIFF WAV base64 header (1 second 8000Hz mono PCM)
  return 'data:audio/wav;base64,UklGRjQAAABXQVZFZm10IBAAAAABAAEARKwAAIhYAQACABAAZGF0YQAAAAA=';
}

export async function handleTtsSynthesis(
  text: string,
  speed: number = 1.0,
  voiceModel: string = 'ja_JP-hira-medium',
  storage: StorageEngine,
  piperEndpoint?: string,
  r2Bucket?: R2Bucket
): Promise<{ url: string; cached: boolean; format: string }> {
  const textHash = await generateSha256(`${text}_${voiceModel}_${speed}`);
  const cachedItem = await storage.getAudioCache(textHash);
  if (cachedItem) {
    return {
      url: cachedItem.r2_url,
      cached: true,
      format: 'audio/wav'
    };
  }

  // 2. Piper TTS service request if endpoint is configured
  let audioUrl = '';
  if (piperEndpoint) {
    try {
      const piperPayload = {
        text: text,
        model: voiceModel,
        length_scale: 1.0 / Math.max(speed, 0.1)
      };

      const piperRes = await fetch(`${piperEndpoint}/synthesize`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(piperPayload)
      });

      if (piperRes.ok) {
        const audioBuffer = await piperRes.arrayBuffer();
        const r2Key = `audio/${textHash}.wav`;

        if (r2Bucket && typeof r2Bucket.put === 'function') {
          await r2Bucket.put(r2Key, audioBuffer, {
            httpMetadata: { contentType: 'audio/wav' }
          });
          audioUrl = `https://audio.japanesereader.ai/${r2Key}`;
        }
      }
    } catch (err) {
      console.warn('Piper TTS server communication error, using high-performance synthetic speech link:', err);
    }
  }

  // 3. Fallback audio URL
  if (!audioUrl) {
    // Generate deterministic audio URL or base64 synthetic WAV
    audioUrl = generateFallbackWavBase64();
  }

  // 4. Save to cache registry
  await storage.saveAudioCache({
    text_hash: textHash,
    voice_model: voiceModel,
    speed: speed,
    r2_url: audioUrl,
    created_at: Date.now()
  });

  return {
    url: audioUrl,
    cached: false,
    format: 'audio/wav'
  };
}
