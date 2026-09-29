(function () {
  var KEY = 'animalin.locale';

  function read() {
    try {
      var stored = localStorage.getItem(KEY);
      if (stored === 'en' || stored === 'es') return stored;
    } catch (e) {}
    return 'es';
  }

  function apply(locale) {
    var root = document.documentElement;
    root.lang = locale;
    var title = root.getAttribute('data-title-' + locale);
    if (title) document.title = title;
    var description = root.getAttribute('data-description-' + locale);
    if (description) {
      var meta = document.querySelector('meta[name="description"]');
      if (meta) meta.setAttribute('content', description);
      var ogDescription = document.querySelector('meta[property="og:description"]');
      if (ogDescription) ogDescription.setAttribute('content', description);
    }
    var ogTitle = document.querySelector('meta[property="og:title"]');
    if (ogTitle && title) ogTitle.setAttribute('content', title);
    var select = document.getElementById('lang-select');
    if (select) select.value = locale;
    var labeled = document.querySelectorAll('[data-label-en]');
    for (var i = 0; i < labeled.length; i++) {
      var label = labeled[i].getAttribute('data-label-' + locale);
      if (label) labeled[i].setAttribute('aria-label', label);
    }
  }

  function boot() {
    var locale = read();
    apply(locale);
    var select = document.getElementById('lang-select');
    if (!select) return;
    select.addEventListener('change', function () {
      var next = select.value === 'en' ? 'en' : 'es';
      try { localStorage.setItem(KEY, next); } catch (e) {}
      apply(next);
    });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
