(() => {
  const base = "/projects/tango-pro/web/";
  let releaseDatabaseLock = null;
  let pendingSpeechCleanup = null;
  let speechRequestId = 0;

  const normalizeLanguageTag = value => String(value || "")
    .trim()
    .replace(/_/g, "-")
    .toLowerCase();

  const selectSpeechVoice = (voices, requestedLanguage) => {
    const requested = normalizeLanguageTag(requestedLanguage);
    const requestedBase = requested.split("-")[0];
    if (!requestedBase) return null;

    const compatible = Array.from(voices || []).filter(voice => {
      const language = normalizeLanguageTag(voice?.lang);
      return language === requested || language.split("-")[0] === requestedBase;
    });
    if (!compatible.length) return null;

    compatible.sort((left, right) => {
      const score = voice => {
        const language = normalizeLanguageTag(voice.lang);
        const exactLocale = language === requested;
        return (exactLocale ? 0 : 100) + (voice.default ? 0 : 10) + (voice.localService ? 0 : 1);
      };
      return score(left) - score(right);
    });
    return compatible[0];
  };

  const speakWithAvailableVoice = (synth, text, language, volume, requestId) => {
    if (requestId !== speechRequestId) return;
    const utterance = new SpeechSynthesisUtterance(text);
    const voice = selectSpeechVoice(synth.getVoices(), language);
    utterance.lang = voice?.lang || language;
    if (voice) utterance.voice = voice;
    utterance.volume = Math.max(0, Math.min(1, Number(volume) || 0));
    utterance.rate = 1;
    utterance.pitch = 1;
    synth.speak(utterance);
  };

  const waitForVoicesAndSpeak = (synth, text, language, volume, requestId) => {
    const finish = () => {
      pendingSpeechCleanup?.();
      pendingSpeechCleanup = null;
      speakWithAvailableVoice(synth, text, language, volume, requestId);
    };
    const onVoicesChanged = () => {
      if (synth.getVoices().length) finish();
    };
    const timer = setTimeout(finish, 1000);
    synth.addEventListener?.("voiceschanged", onVoicesChanged);
    pendingSpeechCleanup = () => {
      clearTimeout(timer);
      synth.removeEventListener?.("voiceschanged", onVoicesChanged);
    };
  };

  const download = (blob, filename) => {
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = filename;
    anchor.rel = "noopener";
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  };

  const crcTable = (() => {
    const table = new Uint32Array(256);
    for (let n = 0; n < 256; n++) {
      let value = n;
      for (let k = 0; k < 8; k++) value = (value & 1) ? (0xedb88320 ^ (value >>> 1)) : (value >>> 1);
      table[n] = value >>> 0;
    }
    return table;
  })();
  const crc32 = bytes => {
    let value = 0xffffffff;
    for (const byte of bytes) value = crcTable[(value ^ byte) & 0xff] ^ (value >>> 8);
    return (value ^ 0xffffffff) >>> 0;
  };
  const u16 = value => [value & 255, (value >>> 8) & 255];
  const u32 = value => [value & 255, (value >>> 8) & 255, (value >>> 16) & 255, (value >>> 24) & 255];
  const concatBytes = chunks => {
    const output = new Uint8Array(chunks.reduce((sum, chunk) => sum + chunk.length, 0));
    let offset = 0;
    for (const chunk of chunks) { output.set(chunk, offset); offset += chunk.length; }
    return output;
  };
  const buildStoreZip = entries => {
    const encoder = new TextEncoder();
    const localChunks = [];
    const centralChunks = [];
    let offset = 0;
    for (const entry of entries) {
      const name = encoder.encode(entry.path);
      const content = encoder.encode(entry.text);
      const crc = crc32(content);
      const local = new Uint8Array([
        ...u32(0x04034b50), ...u16(20), ...u16(0x0800), ...u16(0), ...u16(0), ...u16(0),
        ...u32(crc), ...u32(content.length), ...u32(content.length), ...u16(name.length), ...u16(0),
        ...name, ...content
      ]);
      localChunks.push(local);
      centralChunks.push(new Uint8Array([
        ...u32(0x02014b50), ...u16(20), ...u16(20), ...u16(0x0800), ...u16(0), ...u16(0), ...u16(0),
        ...u32(crc), ...u32(content.length), ...u32(content.length), ...u16(name.length), ...u16(0), ...u16(0),
        ...u16(0), ...u16(0), ...u32(0), ...u32(offset), ...name
      ]));
      offset += local.length;
    }
    const central = concatBytes(centralChunks);
    const end = new Uint8Array([
      ...u32(0x06054b50), ...u16(0), ...u16(0), ...u16(entries.length), ...u16(entries.length),
      ...u32(central.length), ...u32(offset), ...u16(0)
    ]);
    return concatBytes([...localChunks, central, end]);
  };
  const inflateRaw = async bytes => {
    if (typeof DecompressionStream !== "function") throw new Error("このブラウザはZIP展開に対応していません。");
    const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream("deflate-raw"));
    return new Uint8Array(await new Response(stream).arrayBuffer());
  };
  const parseZip = async buffer => {
    const bytes = new Uint8Array(buffer);
    const view = new DataView(buffer);
    let eocd = -1;
    for (let index = Math.max(0, bytes.length - 65557); index <= bytes.length - 22; index++) {
      if (view.getUint32(index, true) === 0x06054b50) eocd = index;
    }
    if (eocd < 0) throw new Error("ZIP終端がありません。");
    const count = view.getUint16(eocd + 10, true);
    if (count > 1001) throw new Error("ZIP内のファイル数が上限を超えています。");
    let centralOffset = view.getUint32(eocd + 16, true);
    const decoder = new TextDecoder("utf-8", { fatal: true });
    const seen = new Set();
    const entries = [];
    let total = 0;
    for (let index = 0; index < count; index++) {
      if (view.getUint32(centralOffset, true) !== 0x02014b50) throw new Error("ZIP中央ディレクトリが不正です。");
      const flags = view.getUint16(centralOffset + 8, true);
      const method = view.getUint16(centralOffset + 10, true);
      const compressedSize = view.getUint32(centralOffset + 20, true);
      const expandedSize = view.getUint32(centralOffset + 24, true);
      const nameLength = view.getUint16(centralOffset + 28, true);
      const extraLength = view.getUint16(centralOffset + 30, true);
      const commentLength = view.getUint16(centralOffset + 32, true);
      const externalAttributes = view.getUint32(centralOffset + 38, true);
      const localOffset = view.getUint32(centralOffset + 42, true);
      if (((externalAttributes >>> 16) & 0xf000) === 0xa000) throw new Error("symlink entryは使えません。");
      const path = decoder.decode(bytes.slice(centralOffset + 46, centralOffset + 46 + nameLength));
      if (!path || path.startsWith("/") || path.includes("\\") || path.split("/").some(part => !part || part === "." || part === "..")) throw new Error("ZIP entry pathが不正です。");
      if (seen.has(path)) throw new Error("ZIP内のファイル名が重複しています。");
      seen.add(path);
      if (expandedSize > 64 * 1024 * 1024) throw new Error("ZIP内のファイルが大きすぎます。");
      total += expandedSize;
      if (total > 256 * 1024 * 1024) throw new Error("ZIPの展開サイズが上限を超えています。");
      if (view.getUint32(localOffset, true) !== 0x04034b50) throw new Error("ZIP local headerが不正です。");
      const localNameLength = view.getUint16(localOffset + 26, true);
      const localExtraLength = view.getUint16(localOffset + 28, true);
      const dataOffset = localOffset + 30 + localNameLength + localExtraLength;
      const compressed = bytes.slice(dataOffset, dataOffset + compressedSize);
      let content;
      if (method === 0) content = compressed;
      else if (method === 8) content = await inflateRaw(compressed);
      else throw new Error(`未対応のZIP圧縮方式です (${method})。`);
      if (content.length !== expandedSize) throw new Error("ZIP展開サイズが一致しません。");
      entries.push({ path, text: decoder.decode(content) });
      centralOffset += 46 + nameLength + extraLength + commentLength;
    }
    return entries;
  };

  window.tangoProBridge = {
    acquireDatabaseLock(callback) {
      if (!navigator.locks?.request) {
        callback(true);
        return;
      }
      navigator.locks.request("tango-pro-web-database", { ifAvailable: true }, async lock => {
        if (!lock) {
          callback(false);
          return;
        }
        callback(true);
        await new Promise(resolve => { releaseDatabaseLock = resolve; });
      });
    },
    releaseDatabaseLock() {
      releaseDatabaseLock?.();
      releaseDatabaseLock = null;
    },
    registerServiceWorker() {
      if ("serviceWorker" in navigator) {
        navigator.serviceWorker.register(`${base}sw.js`, { scope: base }).catch(console.error);
      }
    },
    requestPersistentStorage(callback) {
      if (!navigator.storage?.persist) {
        callback(false);
        return;
      }
      navigator.storage.persist().then(callback).catch(() => callback(false));
    },
    loadBundledText(fileName, callback) {
      fetch(`${base}bundled/${encodeURIComponent(fileName)}`)
        .then(response => {
          if (!response.ok) throw new Error(`HTTP ${response.status}`);
          return response.text();
        })
        .then(text => callback(text, ""))
        .catch(error => callback("", String(error?.message || error)));
    },
    pickTextFile(accept, callback) {
      const input = document.createElement("input");
      input.type = "file";
      input.accept = accept;
      input.onchange = async () => {
        const file = input.files?.[0];
        if (!file) return;
        callback(file.name, await file.text());
      };
      input.click();
    },
    pickStudyArchive(callback) {
      const input = document.createElement("input");
      input.type = "file";
      input.accept = ".zip,application/zip";
      input.onchange = async () => {
        const file = input.files?.[0];
        if (!file) return;
        try { callback(JSON.stringify(await parseZip(await file.arrayBuffer())), ""); }
        catch (error) { callback("", String(error?.message || error)); }
      };
      input.click();
    },
    createStudyArchive(filename, entriesJson) {
      const entries = JSON.parse(entriesJson);
      download(new Blob([buildStoreZip(entries)], { type: "application/zip" }), filename);
    },
    downloadText(filename, text, mime) {
      download(new Blob([text], { type: mime }), filename);
    },
    async shareTextFile(filename, text, mime) {
      const file = new File([text], filename, { type: mime });
      if (navigator.share && navigator.canShare?.({ files: [file] })) {
        try {
          await navigator.share({ files: [file], title: filename });
          return;
        } catch (error) {
          if (error?.name === "AbortError") return;
        }
      }
      download(file, filename);
    },
    speak(text, language, volume) {
      const synth = window.speechSynthesis;
      const normalizedText = String(text || "").trim();
      const normalizedLanguage = String(language || "").trim();
      if (!synth || typeof SpeechSynthesisUtterance !== "function" || !normalizedText || !normalizedLanguage) return;

      speechRequestId += 1;
      const requestId = speechRequestId;
      pendingSpeechCleanup?.();
      pendingSpeechCleanup = null;
      synth.cancel();

      if (synth.getVoices().length) {
        speakWithAvailableVoice(synth, normalizedText, normalizedLanguage, volume, requestId);
      } else {
        waitForVoicesAndSpeak(synth, normalizedText, normalizedLanguage, volume, requestId);
      }
    },
    playTone(correct, volume) {
      const AudioContext = window.AudioContext || window.webkitAudioContext;
      if (!AudioContext || volume <= 0) return;
      const context = new AudioContext();
      const oscillator = context.createOscillator();
      const gain = context.createGain();
      oscillator.type = "sine";
      oscillator.frequency.setValueAtTime(correct ? 880 : 180, context.currentTime);
      gain.gain.setValueAtTime(Math.min(volume, 0.2), context.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.0001, context.currentTime + (correct ? 0.12 : 0.22));
      oscillator.connect(gain).connect(context.destination);
      oscillator.start();
      oscillator.stop(context.currentTime + (correct ? 0.12 : 0.22));
      oscillator.onended = () => context.close();
    },
    getSetting(key) { return localStorage.getItem(`tango-pro.${key}`); },
    setSetting(key, value) { localStorage.setItem(`tango-pro.${key}`, value); },
    removeLoading() { document.getElementById("loading")?.remove(); }
  };

  addEventListener("pagehide", () => window.tangoProBridge.releaseDatabaseLock());
})();
