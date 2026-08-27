import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";

const bridgeSource = fs.readFileSync(new URL("../webApp/src/wasmJsMain/resources/browser-bridge.js", import.meta.url), "utf8");

const createBridge = initialVoices => {
  let voices = initialVoices;
  const spoken = [];
  const listeners = new Map();
  const speechSynthesis = {
    cancelCount: 0,
    cancel() { this.cancelCount += 1; },
    getVoices() { return voices; },
    speak(utterance) { spoken.push(utterance); },
    addEventListener(type, listener) { listeners.set(type, listener); },
    removeEventListener(type, listener) {
      if (listeners.get(type) === listener) listeners.delete(type);
    },
  };
  class SpeechSynthesisUtterance {
    constructor(text) {
      this.text = text;
      this.lang = "";
      this.voice = null;
      this.volume = 1;
      this.rate = 1;
      this.pitch = 1;
    }
  }
  const window = { speechSynthesis };
  const context = vm.createContext({
    window,
    SpeechSynthesisUtterance,
    navigator: {},
    document: { getElementById: () => null },
    addEventListener: () => {},
    setTimeout,
    clearTimeout,
    URL,
    Blob,
    File: class File extends Blob {},
    TextEncoder,
    TextDecoder,
    Uint8Array,
    Uint32Array,
    DataView,
    Response,
    DecompressionStream,
    console,
  });
  vm.runInContext(bridgeSource, context, { filename: "browser-bridge.js" });
  return {
    bridge: window.tangoProBridge,
    speechSynthesis,
    spoken,
    loadVoices(nextVoices) {
      voices = nextVoices;
      listeners.get("voiceschanged")?.();
    },
  };
};

const english = { name: "English", lang: "en-US", default: false, localService: true };
const englishDefault = { name: "English default", lang: "en_US", default: true, localService: false };
const british = { name: "British", lang: "en-GB", default: true, localService: true };
const japanese = { name: "Japanese", lang: "ja-JP", default: true, localService: true };

{
  const test = createBridge([japanese, british, english, englishDefault]);
  test.bridge.speak("hello", "en-US", 2);
  assert.equal(test.spoken.length, 1);
  assert.equal(test.spoken[0].voice, englishDefault, "exact default locale should win");
  assert.equal(test.spoken[0].lang, "en_US");
  assert.equal(test.spoken[0].volume, 1);
  assert.equal(test.spoken[0].rate, 1);
  assert.equal(test.spoken[0].pitch, 1);
  assert.equal(test.speechSynthesis.cancelCount, 1);
}

{
  const test = createBridge([japanese, british]);
  test.bridge.speak("hello", "en-US", 0.5);
  assert.equal(test.spoken[0].voice, british, "same-language voice should be the final compatible fallback");
  assert.equal(test.spoken[0].lang, "en-GB");
}

{
  const test = createBridge([japanese]);
  test.bridge.speak("bonjour", "fr-FR", -1);
  assert.equal(test.spoken[0].voice, null, "an unrelated voice must never be forced");
  assert.equal(test.spoken[0].lang, "fr-FR");
  assert.equal(test.spoken[0].volume, 0);
}

{
  const test = createBridge([]);
  test.bridge.speak("こんにちは", "ja-JP", 0.8);
  assert.equal(test.spoken.length, 0, "speech should wait while the browser is loading voices");
  test.loadVoices([english, japanese]);
  assert.equal(test.spoken.length, 1);
  assert.equal(test.spoken[0].voice, japanese);
}

console.log("Browser bridge tests: PASS");
