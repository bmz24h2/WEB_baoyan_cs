/**
 * features/voice.js  —  语音 I/O 模块
 *
 * 职责：
 *  A. STT（语音识别）：浏览器 Web Speech API
 *     - VoiceRecorder.init(options)
 *     - VoiceRecorder.start()
 *     - VoiceRecorder.stop()
 *     - VoiceRecorder.toggle()
 *     - VoiceRecorder.isActive()       ← 修复录音停止 bug：用按钮 dataset 追踪状态
 *
 *  B. TTS（语音合成）：阿里云 CosyVoice（本地后端代理）→ 浏览器降级
 *     - VoiceSynth.speak(text)
 *     - VoiceSynth.stop()
 *     - VoiceSynth.isSpeaking()
 *
 * 依赖：无外部依赖（CosyVoice 通过项目后端 /api/speech/synthesize 调用）
 */

/* ══════════════════════════════════════════════════════════
   A. STT  —  语音录入
   ══════════════════════════════════════════════════════════ */
var VoiceRecorder = (function () {
  'use strict';

  var recognition = null;
  var options     = {};
  var waveTimer   = null;

  /* 内部：是否正在录音（用按钮 dataset 而非独立变量，避免异步竞态） */
  function _isRecording() {
    var btn = options.button ? document.getElementById(options.button) : null;
    return btn ? btn.dataset.recording === 'true' : false;
  }

  function _setRecording(val) {
    var btn = options.button ? document.getElementById(options.button) : null;
    if (btn) btn.dataset.recording = val ? 'true' : 'false';
  }

  /* 波形动画 */
  function _startWave() {
    var bars = document.querySelectorAll('.wave-bar');
    if (!bars.length) return;
    waveTimer = setInterval(function () {
      bars.forEach(function (b) { b.style.height = (4 + Math.random() * 18) + 'px'; });
    }, 80);
  }
  function _stopWave() {
    clearInterval(waveTimer);
    document.querySelectorAll('.wave-bar').forEach(function (b) { b.style.height = '4px'; });
  }

  function _setStatus(msg) {
    var el = options.statusEl ? document.getElementById(options.statusEl) : null;
    if (el) el.textContent = msg;
  }

  function _setBtnState(state) {
    var btn = options.button ? document.getElementById(options.button) : null;
    if (!btn) return;
    btn.classList.remove('listening', 'processing');
    if (state === 'listening') {
      btn.classList.add('listening');
      btn.textContent = '⏹';
    } else if (state === 'processing') {
      btn.classList.add('processing');
      btn.textContent = '…';
    } else {
      btn.textContent = '🎤';
    }
  }

  /**
   * 初始化语音识别
   * @param {Object} opts
   *   button     {string}  录音按钮的 id
   *   statusEl   {string}  状态文字元素的 id
   *   outputEl   {string}  输出文字区（textarea/input）的 id
   *   lang       {string}  语言代码，默认 'zh-CN'
   *   onResult   {Function(text)}  实时识别结果回调
   *   onEnd      {Function(text)}  识别结束回调（全文）
   *   onTimer    {Function(sec)}   计时回调
   */
  function init(opts) {
    options = opts || {};
    var SpeechAPI = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechAPI) {
      _setStatus('浏览器不支持语音识别，请使用 Chrome 或 Edge');
      return false;
    }

    recognition = new SpeechAPI();
    recognition.lang           = options.lang || 'zh-CN';
    recognition.continuous     = true;
    recognition.interimResults = true;
    recognition.maxAlternatives = 1;

    var finalText = '';
    var timerInterval = null, timerSec = 0;

    recognition.onstart = function () {
      _setRecording(true);
      finalText = '';
      _setBtnState('listening');
      _setStatus('正在录音…说完后点击停止');
      _startWave();
      // 计时
      timerSec = 0;
      var timerEl = options.timerEl ? document.getElementById(options.timerEl) : null;
      if (timerEl) timerEl.style.display = '';
      timerInterval = setInterval(function () {
        timerSec++;
        if (timerEl) {
          var m = String(Math.floor(timerSec / 60)).padStart(2, '0');
          var s = String(timerSec % 60).padStart(2, '0');
          timerEl.textContent = m + ':' + s;
          timerEl.style.background =
            timerSec < 120 ? 'var(--green-bg,#e8f5e9)' :
            timerSec < 180 ? 'var(--amber-bg,#fffbeb)' : '#fee2e2';
        }
        if (options.onTimer) options.onTimer(timerSec);
      }, 1000);
    };

    recognition.onresult = function (e) {
      var interim = '';
      for (var i = e.resultIndex; i < e.results.length; i++) {
        if (e.results[i].isFinal) finalText += e.results[i][0].transcript;
        else interim = e.results[i][0].transcript;
      }
      var combined = finalText + interim;
      var outEl = options.outputEl ? document.getElementById(options.outputEl) : null;
      if (outEl) outEl.value = combined;
      if (options.onResult) options.onResult(combined);
    };

    recognition.onerror = function (e) {
      if (e.error === 'no-speech') return;
      _setStatus('识别错误：' + e.error);
      _cleanup(timerInterval);
    };

    recognition.onend = function () {
      _cleanup(timerInterval);
      _setStatus('录音结束');
      if (options.onEnd) {
        var outEl = options.outputEl ? document.getElementById(options.outputEl) : null;
        options.onEnd(outEl ? outEl.value : finalText);
      }
    };

    // 启用按钮
    var btn = options.button ? document.getElementById(options.button) : null;
    if (btn) btn.disabled = false;
    return true;
  }

  function _cleanup(timerInterval) {
    clearInterval(timerInterval);
    _setRecording(false);
    _setBtnState('idle');
    _stopWave();
  }

  function start() {
    if (!recognition && !init(options)) return;
    _setBtnState('processing');
    recognition.start();
  }

  function stop() {
    if (recognition) recognition.stop();
  }

  /** 切换录音：按钮被点击时调用 */
  function toggle() {
    if (!recognition && !init(options)) return;
    if (_isRecording()) stop();
    else start();
  }

  function isActive() { return _isRecording(); }

  return { init: init, start: start, stop: stop, toggle: toggle, isActive: isActive };
})();


/* ══════════════════════════════════════════════════════════
   B. TTS  —  语音合成
   ══════════════════════════════════════════════════════════ */
var VoiceSynth = (function () {
  'use strict';

  var _speaking    = false;
  var _audio       = null;    // HTMLAudioElement（阿里云 mp3）
  var _utterance   = null;    // SpeechSynthesisUtterance（浏览器 TTS）

  var _API = 'http://localhost:8080/api/speech/synthesize';

  /** 朗读文字（自动选择最佳引擎） */
  function speak(text, opts) {
    opts = opts || {};
    var engine = opts.engine || 'aliyun';
    if (engine === 'aliyun') {
      _speakAliyun(text, opts).catch(function () { _speakBrowser(text, opts); });
    } else {
      _speakBrowser(text, opts);
    }
  }

  async function _speakAliyun(text, opts) {
    _setBtnState(true, opts);
    _setStatus('合成中…', opts);
    _speaking = true;

    var resp = await fetch(_API, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        text:   text,
        voice:  opts.voice  || 'longxiaochun',
        speed:  opts.speed  || 1.1,
        format: 'mp3',
      }),
    });
    if (!resp.ok) throw new Error('TTS API ' + resp.status);

    var blob = await resp.blob();
    var url  = URL.createObjectURL(blob);
    if (_audio) { _audio.pause(); URL.revokeObjectURL(_audio.src); }
    _audio = new Audio(url);
    _audio.play();
    _setStatus('阿里云 CosyVoice', opts);
    _audio.onended = function () {
      _speaking = false;
      _setBtnState(false, opts);
      _setStatus('', opts);
      URL.revokeObjectURL(url);
    };
  }

  function _speakBrowser(text, opts) {
    if (!window.speechSynthesis) { alert('此浏览器不支持语音合成'); return; }
    window.speechSynthesis.cancel();
    var utter = new SpeechSynthesisUtterance(text);
    utter.lang  = 'zh-CN';
    utter.rate  = opts.speed || 1.1;
    var voices  = window.speechSynthesis.getVoices();
    var cnVoice = voices.find(function (v) { return v.lang === 'zh-CN' || v.lang === 'zh-TW'; });
    if (cnVoice) utter.voice = cnVoice;
    _utterance = utter;
    _speaking  = true;
    _setBtnState(true, opts);
    _setStatus('浏览器内置 TTS', opts);
    utter.onend = function () {
      _speaking = false;
      _setBtnState(false, opts);
      _setStatus('', opts);
    };
    window.speechSynthesis.speak(utter);
  }

  /** 停止朗读 */
  function stop() {
    if (_audio)  { _audio.pause(); _audio.currentTime = 0; }
    if (window.speechSynthesis) window.speechSynthesis.cancel();
    _speaking = false;
  }

  /** 切换朗读/停止 */
  function toggle(text, opts) {
    if (_speaking) stop();
    else speak(text, opts);
  }

  function isSpeaking() { return _speaking; }

  function _setBtnState(playing, opts) {
    var btn = opts && opts.btnEl ? document.getElementById(opts.btnEl) : null;
    if (!btn) return;
    btn.textContent = playing ? '⏹ 停止朗读' : '朗读点评';
    btn.classList.toggle('playing', playing);
  }
  function _setStatus(msg, opts) {
    var el = opts && opts.statusEl ? document.getElementById(opts.statusEl) : null;
    if (el) el.textContent = msg;
  }

  return { speak: speak, stop: stop, toggle: toggle, isSpeaking: isSpeaking };
})();
