/* ========================================================================
   Service Worker — prayer-times
   يقوم بـ:
   1) تخزين prayer-times.html مؤقتاً (cache-first) للعمل دون اتصال.
   2) فحص version.json دورياً وعند اكتشاف نسخة جديدة يُخطر الصفحة
      ليعيد المستخدم التحميل أو يُعاد التحميل تلقائياً.
   يعمل فقط على بروتوكول https/http — لا يعمل مع file:// (الأندرويد يعتمد
   على NativeBridge.getDeviceId بدلاً من هذا).
   ======================================================================== */

var SW_CACHE = "prayer-times-v65";
var APP_SHELL = [
  "./",
  "./prayer-times.html",
  "./content.json",
  "./version.json",
  "./public/manifest.json"
];

self.addEventListener("install", function(event) {
  event.waitUntil(
    caches.open(SW_CACHE).then(function(cache) {
      return cache.addAll(APP_SHELL).catch(function() {});
    })
  );
  self.skipWaiting();
});

self.addEventListener("activate", function(event) {
  event.waitUntil(
    caches.keys().then(function(keys) {
      return Promise.all(
        keys.filter(function(k) { return k !== SW_CACHE; })
            .map(function(k) { return caches.delete(k); })
      );
    })
  );
  self.clients.claim();
});

self.addEventListener("fetch", function(event) {
  var req = event.request;
  if (req.method !== "GET") return;

  // لا تخزّن طلبات GitHub raw (تُجلب دائماً طازجة)
  var url = new URL(req.url);
  if (url.hostname.indexOf("raw.githubusercontent.com") !== -1) {
    event.respondWith(fetch(req));
    return;
  }

  // استراتيجية cache-first مع تحديث خلفي
  event.respondWith(
    caches.match(req).then(function(cached) {
      var network = fetch(req).then(function(resp) {
        if (resp && resp.status === 200 && resp.type === "basic") {
          var clone = resp.clone();
          caches.open(SW_CACHE).then(function(cache) {
            cache.put(req, clone).catch(function() {});
          });
        }
        return resp;
      }).catch(function() { return cached; });
      return cached || network;
    })
  );
});

// استقبال رسائل من الصفحة
self.addEventListener("message", function(event) {
  if (event.data === "skipWaiting") self.skipWaiting();
});
