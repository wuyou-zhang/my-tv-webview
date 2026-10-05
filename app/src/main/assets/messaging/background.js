/* My TV 0 原生桥 —— 扩展 background script。
 *
 * 职责：
 *  收集页面加载过程中发起的全部 HTTP(S) 请求 URL，转发给原生层，
 *  对应 WebView 的 shouldInterceptRequest（WebViewHolder 对每个请求都
 *  appendResourceUrl，供页面脚本 App.getResourceUrl() 使用）。
 *
 * 注意：
 *  - browser.webRequest 只能在 background script 中使用（content script 无此 API），
 *    因此本文件单独存在，bridge.js（content script）无需改动。
 *  - 用 browser.runtime.sendNativeMessage 一次性消息回传，避免占用 content script
 *    连接的那条端口（native 侧 onConnect 只维护 content script 的端口）。
 *  - 原生侧在扩展级 MessageDelegate.onMessage 里按 type=resourceUrl 处理。
 */
browser.webRequest.onBeforeRequest.addListener(
  function (details) {
    var url = details.url || '';
    if (url.indexOf('http://') === 0 || url.indexOf('https://') === 0) {
      browser.runtime.sendNativeMessage('mytv0', {
        type: 'resourceUrl',
        url: url
      }).catch(function () {});
    }
  },
  { urls: ['<all_urls>'] }
);