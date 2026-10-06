/* Online-first: never persist navigations, session pages, or API responses. */
'use strict';
const CACHE_PREFIX = 'luas-pets-pwa-';
const CACHE_NAME = CACHE_PREFIX + 'v2';
const OFFLINE_URL = '/offline.html';
// Deliberate allowlist: no uploaded images or future dynamic/static routes.
const STATIC_TYPES = new Map([
    ['/css/luaspets.css', 'text/css'],
    ['/js/carrito.js', 'javascript'],
    ['/js/notificaciones.js', 'javascript'],
    ['/js/pwa.js', 'javascript'],
    ['/icons/icon-192.png', 'image/png'],
    ['/icons/icon-512.png', 'image/png'],
    ['/icons/icon-maskable-512.png', 'image/png'],
    ['/icons/apple-touch-icon.png', 'image/png']
]);
const PRECACHE = [OFFLINE_URL, '/css/luaspets.css', '/js/pwa.js'];
const SENSITIVE_PATH = /^\/(?:admin|doctor|cliente|perfil|2fa|login|logout)(?:\/|$)/;

function validResponse(response, path, type) {
    return response.ok && !response.redirected
        && response.url === new URL(path, self.location.origin).href
        && (response.headers.get('Content-Type') || '').toLowerCase().includes(type);
}

self.addEventListener('install', event => {
    event.waitUntil((async () => {
        // Fetch and validate the whole minimum shell before any persistent writes.
        const responses = await Promise.all(PRECACHE.map(async path => {
            const response = await fetch(path, { credentials: 'omit', cache: 'no-store' });
            const control = response.headers.get('Cache-Control') || '';
            const type = path === OFFLINE_URL ? 'text/html' : STATIC_TYPES.get(path);
            // Only the fixed generic offline file ignores Spring's default no-store.
            const privateResponse = path === OFFLINE_URL ? /private/i : /(?:private|no-store)/i;
            if (!validResponse(response, path, type) || privateResponse.test(control)
                || (path !== OFFLINE_URL && !/(?:^|,)\s*public(?:\s*,|$)/i.test(control))) {
                throw new Error('Precache resource is not a public static response: ' + path);
            }
            return response;
        }));
        const cache = await caches.open(CACHE_NAME);
        for (let i = 0; i < PRECACHE.length; i++) await cache.put(PRECACHE[i], responses[i]);
        await self.skipWaiting();
    })());
});

self.addEventListener('activate', event => {
    event.waitUntil((async () => {
        const names = await caches.keys();
        await Promise.all(names.filter(name => name.startsWith(CACHE_PREFIX) && name !== CACHE_NAME)
            .map(name => caches.delete(name)));
        await self.clients.claim();
    })());
});

async function navigation(request) {
    try {
        // Bypass the browser HTTP cache as well as Cache Storage for all HTML.
        return await fetch(request, { cache: 'no-store' });
    } catch (error) {
        try {
            const cache = await caches.open(CACHE_NAME);
            const offline = await cache.match(OFFLINE_URL);
            if (offline) return offline;
        } catch (cacheError) {
            // A disabled/unavailable cache still gets a generic error page.
        }
        return new Response('Sin conexión. Vuelve a intentarlo.', {
            status: 503, headers: { 'Content-Type': 'text/plain; charset=UTF-8' }
        });
    }
}

async function staticAsset(request, path) {
    let response;
    try {
        response = await fetch(request, { credentials: 'omit', cache: 'no-store' });
    } catch (error) {
        try {
            const cache = await caches.open(CACHE_NAME);
            const cached = await cache.match(path);
            if (cached) return cached;
        } catch (cacheError) {
            // Preserve the original network failure when storage is unavailable.
        }
        throw error;
    }
    const control = response.headers.get('Cache-Control') || '';
    try {
        const cache = await caches.open(CACHE_NAME);
        if (validResponse(response, path, STATIC_TYPES.get(path))
            && !/(?:private|no-store)/i.test(control)
            && /(?:^|,)\s*public(?:\s*,|$)/i.test(control)) {
            await cache.put(path, response.clone());
        } else {
            await cache.delete(path);
        }
    } catch (error) {
        // Cache quota/storage failures must not hide a successful network response.
    }
    return response; // Preserve real HTTP errors; never return offline HTML for assets.
}

self.addEventListener('fetch', event => {
    const request = event.request;
    const url = new URL(request.url);
    if (request.method !== 'GET' || url.origin !== self.location.origin) return;
    if (request.mode === 'navigate') {
        event.respondWith(navigation(request));
        return;
    }
    if (url.search || SENSITIVE_PATH.test(url.pathname) || request.headers.has('Authorization')) return;
    if (STATIC_TYPES.has(url.pathname)) {
        event.respondWith(staticAsset(request, url.pathname));
    }
});
