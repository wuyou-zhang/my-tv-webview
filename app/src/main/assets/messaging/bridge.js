/* My TV 0 原生桥 —— GeckoView content script（隔离世界，仅顶层 frame 生效）。
 * 异步一致性约定（与 WebView 的关键差异）：
 *  - **所有有返回值的 App 方法一律异步**往返原生，回调 `App.getArg(function(r){})`，
 *    不传回调时返回 Promise；返回值以字符串回调。
 *  - 无返回值的方法（notifyResult/log/clearWebView/openUrl 等）直接转发原生执行。
 *
 * 调试：content script 的 console.log（BRIDGE 前缀）直接进入 Gecko 控制台，
 * 配合 GeckoRuntimeSettings.consoleOutput(true) 会输出到 Logcat。
 */
(function () {
  'use strict';

  // 仅顶层 frame 建立桥接（避免 iframe 里的内容脚本重复连接）
  if (window.top !== window) {
    return;
  }

  var port = null;

  function injectMainBridge() {
    var script = document.createElement('script');
    script.textContent = '(' + function () {
      // 内部回调注册表：App 异步方法按 callbackId 回调
      var callbacks = {};
      var seq = 0;

      function invokeNative(method, args, callback) {
        var id = 'cb' + (++seq);
        if (typeof callback === 'function') {
          callbacks[id] = callback;
        } else {
          callbacks[id] = null;
        }
        window.__MYTV_BRIDGE__.forward(method, args, id);
        if (typeof callback !== 'function') {
          return new Promise(function (resolve) {
            callbacks[id] = resolve;
          });
        }
        return undefined;
      }

      window.__MYTV_BRIDGE__ = {
        forward: function (method, args, callbackId) {
          document.dispatchEvent(new CustomEvent('__mytv_invoke__', {
            detail: { method: method, args: args || [], callbackId: callbackId || '' }
          }));
        },
        // 原生返回结果：按 callbackId 触发对应的 App 回调
        resolveCallback: function (callbackId, result) {
          var cb = callbacks[callbackId];
          if (cb) {
            delete callbacks[callbackId];
            cb(result);
          }
        },
        // 仅在原生 appBridge=true 时（收到 config 消息后）由本函数安装 window.App。
        installAppBridge: function () {
          if (window.App) {
            return;
          }
          // 与 WebView addJavascriptInterface 同名同签。
          window.App = {
            // ---- 有返回值的：全部异步往返原生，返回值以字符串回调 ----
            getArg: function (cb) { return invokeNative('getArg', [], cb); },
            getWebViewWidth: function (cb) { return invokeNative('getWebViewWidth', [], cb); },
            getWebViewHeight: function (cb) { return invokeNative('getWebViewHeight', [], cb); },
            getDeviceModel: function (cb) { return invokeNative('getDeviceModel', [], cb); },
            getDownloadUrl: function (cb) { return invokeNative('getDownloadUrl', [], cb); },
            getOpenOtherUrl: function (cb) { return invokeNative('getOpenOtherUrl', [], cb); },
            getDownloadFileList: function (cb) { return invokeNative('getDownloadFileList', [], cb); },
            readCache: function (key, cb) { return invokeNative('readCache', [key], cb); },
            getResourceUrl: function (cb) { return invokeNative('getResourceUrl', [], cb); },
            requestScreenshot: function (cb) { return invokeNative('requestScreenshot', [], cb); },
            setSelectFileNames: function (filenames, cb) {
              return invokeNative('setSelectFileNames', [filenames], cb);
            },

            // ---- 无返回值：直接转发原生执行 ----
            openUrl: function (url) { window.__MYTV_BRIDGE__.forward('openUrl', [url]); },
            writeCache: function (key, value) { window.__MYTV_BRIDGE__.forward('writeCache', [key, value]); },
            clearDownloadFileList: function () { window.__MYTV_BRIDGE__.forward('clearDownloadFileList', []); },
            requestDownloadFile: function (url, filename, auth, cookie, userAgent, referer) {
              window.__MYTV_BRIDGE__.forward('requestDownloadFile', [url, filename, auth, cookie, userAgent, referer]);
            },
            clearResourceUrl: function () { window.__MYTV_BRIDGE__.forward('clearResourceUrl', []); },
            showToast: function (toast) { window.__MYTV_BRIDGE__.forward('showToast', [toast]); },
            notifyResult: function (result) { window.__MYTV_BRIDGE__.forward('notifyResult', [result]); },
            log: function (log) { window.__MYTV_BRIDGE__.forward('log', [log]); },
            resetTimer: function () { window.__MYTV_BRIDGE__.forward('resetTimer', []); },
            clearWebView: function () { window.__MYTV_BRIDGE__.forward('clearWebView', []); },
            performNativeClick: function (x, y) { window.__MYTV_BRIDGE__.forward('performNativeClick', [x, y]); },
            performNativeScroll: function (sx, sy, ex, ey, d, s) {
              window.__MYTV_BRIDGE__.forward('performNativeScroll', [sx, sy, ex, ey, d, s]);
            },
            performKeyEvent: function (name) { window.__MYTV_BRIDGE__.forward('performKeyEvent', [name]); }
          };
        }
      };

      // 把页面 console 消息转发给原生 log 方法（对应 WebViewHolder 打印所有 console.log）。
      // __NATIVE_INVOKE__: 前缀的消息走原生方法调用协议，不当作普通日志。
      function forwardConsole(method, args) {
        var text;
        try {
          text = Array.prototype.slice.call(args).join(' ');
        } catch (e) {
          text = '';
        }
        if (!text) {
          return;
        }
        var first = args && args[0];
        if (method === 'log' && typeof first === 'string' && first.indexOf('__NATIVE_INVOKE__:') === 0) {
          window.__MYTV_BRIDGE__.forward('__native_invoke__', [first]);
        } else {
          // 上限截断，避免超长对象日志撑爆 IPC
          window.__MYTV_BRIDGE__.forward('log', [text.length > 4096 ? text.substring(0, 4096) : text]);
        }
      }

      function hookConsole(method) {
        var orig = window.console[method];
        if (typeof orig !== 'function') {
          return;
        }
        var origBound = orig.bind(window.console);
        window.console[method] = function () {
          try { origBound.apply(window.console, arguments); } catch (e) { /* ignore */ }
          forwardConsole(method, arguments);
        };
      }
      ['log', 'info', 'warn', 'error', 'debug'].forEach(hookConsole);
    } + ')();';
    var node = document.head || document.documentElement;
    if (node) {
      node.appendChild(script);
      script.remove();
    }
  }

  function injectMainWorld(code) {
    if (!code) { return; }
    var script = document.createElement('script');
    script.textContent = code;
    var node = document.head || document.documentElement;
    if (node) {
      node.appendChild(script);
      script.remove();
    }
  }

  function onNativeMessage(message) {
    if (!message || !message.type) {
      return;
    }
    if (message.type === 'config') {
      // 原生配置：appBridge 决定是否安装 window.App
      injectMainWorld('window.__MYTV_CONFIG__ = ' + JSON.stringify(message.config || {}) + ';');
      if (message.config && message.config.appBridge) {
        injectMainWorld(
          '(function(){var b=window.__MYTV_BRIDGE__;if(b&&b.installAppBridge){b.installAppBridge();}})();'
        );
      }
    } else if (message.type === 'eval') {
      var script = String(message.script || '');
      injectMainWorld(script);
    } else if (message.type === 'invokeResult') {
      // 同时解析：App 异步回调（内部注册表）与页面 console 协议的 __NATIVE_CALLBACK__
      injectMainWorld(
        '(function(){var b=window.__MYTV_BRIDGE__;if(b&&b.resolveCallback){b.resolveCallback(' +
        JSON.stringify(message.callbackId) + ',' + JSON.stringify(message.result) + ');}' +
        'var f=window.__NATIVE_CALLBACK__;if(f){try{f(' +
        JSON.stringify(message.callbackId) + ',' + JSON.stringify(message.result) + ');}catch(e){}}})();'
      );
    }
  }

  // 主世界 -> 原生
  document.addEventListener('__mytv_invoke__', function (evt) {
    var d = evt.detail || {};
    if (!port) {
      console.log('BRIDGE: port 未就绪，丢弃本次转发');
      return;
    }
    if (d.method === '__native_invoke__') {
      var raw = String((d.args && d.args[0]) || '');
      try {
        var jsonStr = raw.replace('__NATIVE_INVOKE__:', '');
        var json = JSON.parse(jsonStr);
        port.postMessage({
          type: 'invoke',
          method: json.method,
          args: json.args || [],
          callbackId: json.callbackId || ''
        });
      } catch (e) { /* ignore */ }
    } else {
      port.postMessage({
        type: 'invoke',
        method: d.method,
        args: d.args || [],
        callbackId: d.callbackId || ''
      });
    }
  });

  function connect() {
    try {
      port = browser.runtime.connectNative('mytv0');
    } catch (e) {
      console.log('BRIDGE: connectNative 失败: ' + e);
      return;
    }
    port.onMessage.addListener(onNativeMessage);
    port.onDisconnect.addListener(function () {
      port = null;
    });
  }

  injectMainBridge();
  connect();
})();