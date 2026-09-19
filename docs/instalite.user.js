// ==UserScript==
// @name         InstaLite
// @description  Instagram без Reels и рекомендаций: лента подписок, скрыты Reels, «Интересное» и «Рекомендации для вас».
// @version      1.0
// @author       MaksXD
// @homepageURL  https://github.com/MaksXD/instalite
// @updateURL    https://maksxd.github.io/instalite/instalite.user.js
// @downloadURL  https://maksxd.github.io/instalite/instalite.user.js
// @match        https://www.instagram.com/*
// @match        https://instagram.com/*
// @run-at       document-start
// @inject-into  auto
// @noframes
// @grant        none
// ==/UserScript==

/*
 * InstaLite: скрипт, который встраивается в instagram.com внутри приложения.
 *  - Главная всегда открывается как лента «Подписки» (/?variant=following) — только аккаунты,
 *    на которые вы подписаны, по хронологии, без алгоритмических рекомендаций.
 *  - Вкладка Reels (/reels/...) и лента аудио-подборок отключены: переход туда возвращает на главную.
 *    Отдельный рилс по ссылке от друга (/reel/ID/) открывается как обычный пост.
 *  - На странице «Интересное» (/explore/) скрыта сетка рекомендованных постов, поиск остаётся.
 *  - Блоки «Рекомендации для вас» / «Suggested for you» и рекомендованные посты в ленте скрываются.
 */
(function () {
  'use strict';
  if (window.__instaLite) return;
  window.__instaLite = true;

  var HOME = '/?variant=following';

  // Точные подписи блоков рекомендаций (RU / EN / KZ)
  var SUGGEST_LABELS = [
    'suggested for you', 'suggested posts', 'suggested reels', 'suggested',
    'рекомендации для вас', 'рекомендуемые публикации', 'рекомендуемые', 'рекомендуем',
    'рекомендуем вам', 'вам может понравиться', 'рекомендованные публикации',
    'сізге ұсынылғандар', 'ұсынылған жарияланымдар'
  ];

  var CSS = [
    /* Кнопка/вкладка Reels и ссылки на подборки аудио-рилсов */
    'a[href="/reels/"], a[href^="/reels/"], a[href^="https://www.instagram.com/reels/"] { display: none !important; }',
    /* Сетка рекомендаций на странице «Интересное»: плитки — это ссылки на посты и рилсы */
    'html.il-explore main a[href*="/p/"], html.il-explore main a[href*="/reel/"] { display: none !important; }',
    '[data-il-hidden] { display: none !important; }'
  ].join('\n');

  function norm(s) {
    return (s || '').replace(/\s+/g, ' ').trim().toLowerCase();
  }

  function addStyle() {
    if (document.getElementById('il-style')) return;
    var st = document.createElement('style');
    st.id = 'il-style';
    st.textContent = CSS;
    (document.head || document.documentElement).appendChild(st);
  }

  // ---------- Маршруты ----------

  function go(url, guardKey) {
    if (guardKey) {
      // Защита от бесконечного цикла перезагрузок
      // (не больше 2 автоматических переходов за 8 секунд)
      try {
        var now = Date.now();
        var recent = (sessionStorage.getItem(guardKey) || '').split(',')
          .map(Number).filter(function (t) { return t && now - t < 8000; });
        if (recent.length >= 2) return;
        recent.push(now);
        sessionStorage.setItem(guardKey, recent.join(','));
      } catch (e) { /* sessionStorage недоступен — просто переходим */ }
    }
    location.replace(url);
  }

  function route() {
    var p = location.pathname;
    document.documentElement.classList.toggle('il-explore', p === '/explore/' || p === '/explore');

    if (p === '/reels' || p.indexOf('/reels/') === 0) {
      go(HOME);
      return;
    }
    if (p === '/' && !/[?&]variant=/.test(location.search)) {
      go(HOME, '__il_home');
    }
  }

  var lastHref = location.href;
  function onNav() {
    if (location.href === lastHref) return;
    lastHref = location.href;
    route();
  }

  ['pushState', 'replaceState'].forEach(function (fn) {
    var orig = history[fn];
    if (typeof orig !== 'function') return;
    history[fn] = function () {
      var r = orig.apply(this, arguments);
      setTimeout(onNav, 0);
      return r;
    };
  });
  window.addEventListener('popstate', function () { setTimeout(onNav, 0); });
  setInterval(onNav, 800); // подстраховка, если сайт меняет адрес иначе

  // ---------- Скрытие рекомендаций ----------

  var labelSet = {};
  SUGGEST_LABELS.forEach(function (l) { labelSet[l] = true; });

  function hide(el) {
    if (el && el !== document.body && el !== document.documentElement && !el.hasAttribute('data-il-hidden')) {
      el.setAttribute('data-il-hidden', '1');
    }
  }

  function containerFor(el) {
    var art = el.closest('article');
    if (art) return art; // рекомендованный пост в ленте
    // Блок-карусель «Рекомендации для вас» между постами: поднимаемся до элемента,
    // соседом которого являются посты ленты.
    var c = el;
    for (var i = 0; i < 14 && c && c.parentElement; i++) {
      var parent = c.parentElement;
      if (parent === document.body) break;
      if (!c.querySelector('article') && parent.querySelector('article')) return c;
      c = parent;
    }
    return null;
  }

  function scan(root) {
    if (!root || !root.ownerDocument && root !== document) return;
    var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
    var n;
    while ((n = walker.nextNode())) {
      var t = n.nodeValue;
      if (!t || t.length > 40) continue;
      if (!labelSet[norm(t)]) continue;
      var el = n.parentElement;
      if (!el || el.closest('[data-il-hidden]')) continue;
      hide(containerFor(el));
    }
  }

  var pending = [];
  var timer = null;
  function flush() {
    timer = null;
    var list = pending;
    pending = [];
    for (var i = 0; i < list.length; i++) {
      if (list[i].isConnected) scan(list[i]);
    }
  }

  function start() {
    addStyle();
    route();
    if (document.body) scan(document.body);
    new MutationObserver(function (muts) {
      for (var i = 0; i < muts.length; i++) {
        var added = muts[i].addedNodes;
        for (var j = 0; j < added.length; j++) {
          var node = added[j];
          if (node.nodeType === 1) pending.push(node);
          else if (node.nodeType === 3 && node.parentElement) pending.push(node.parentElement);
        }
        if (muts[i].type === 'characterData' && muts[i].target.parentElement) {
          pending.push(muts[i].target.parentElement);
        }
      }
      if (pending.length && !timer) timer = setTimeout(flush, 250);
    }).observe(document.documentElement, { childList: true, subtree: true, characterData: true });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start);
  } else {
    start();
  }
})();
