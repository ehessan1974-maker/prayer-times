/* ========================================================================
   Service Worker — prayer-times
   v25.31: إصلاح أخطاء FetchEvent — معالجة أخطاء الشبكة
   ======================================================================== */

var SW_CACHE = "prayer-times-v81";
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

  var url = new URL(req.url);

  // v25.31: لطلات raw.githubusercontent.com و cdn.jsdelivr.net و api.telegram.org
  // حاول fetch، وإن فشل بسبب الشبكة، ارجع للكاش أو استجابة فارغة
  // هذا يمنع أخطاء "Uncaught (in promise) TypeError: Failed to fetch"
  if (url.hostname.indexOf("raw.githubusercontent.com") !== -1 ||
      url.hostname.indexOf("cdn.jsdelivr.net") !== -1 ||
      url.hostname.indexOf("api.telegram.org") !== -1 ||
      url.hostname.indexOf("unpkg.com") !== -1 ||
      url.hostname.indexOf("openstreetmap.org") !== -1) {
    event.respondWith(
      fetch(req).catch(function(err) {
        // v25.31: عند فشل الشبكة، ارجع للكاش إن وُجد
        return caches.match(req).then(function(cached) {
          if (cached) return cached;
          // إن لم يوجد كاش، ارجع استجابة فارغة بدلاً من خطأ
          return new Response("", {
            status: 503,
            statusText: "Service Unavailable",
            headers: { "Content-Type": "text/plain" }
          });
        });
      })
    );
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
      }).catch(function() {
        // v25.31: عند فشل الشبكة، ارجع للكاش
        return cached || new Response("", {
          status: 503,
          statusText: "Service Unavailable",
          headers: { "Content-Type": "text/plain" }
        });
      });
      return cached || network;
    })
  );
});

// استقبال رسائل من الصفحة
self.addEventListener("message", function(event) {
  if (event.data === "skipWaiting") self.skipWaiting();
});
