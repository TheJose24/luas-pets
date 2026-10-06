const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const origin = 'https://luas.test';
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/sw.js'), 'utf8');

function response(url, options = {}) {
    const r = new Response(options.body || 'public', {
        status: options.status || 200,
        headers: { 'Content-Type': options.type || 'text/css', 'Cache-Control': options.control || 'public' }
    });
    Object.defineProperties(r, { url: { value: url }, redirected: { value: !!options.redirected } });
    r.clone = () => response(url, options);
    return r;
}
function harness() {
    const events = {}, stores = new Map(), fetched = [], writes = [];
    let network = async req => response(typeof req === 'string' ? origin + req : req.url);
    const caches = {
        async open(name) {
            if (!stores.has(name)) stores.set(name, new Map());
            const store = stores.get(name);
            return {
                async put(key, value) { writes.push(key); store.set(key, value); },
                async match(key) { return store.get(key)?.clone(); },
                async delete(key) { return store.delete(key); }
            };
        },
        async keys() { return [...stores.keys()]; },
        async delete(name) { return stores.delete(name); }
    };
    vm.runInNewContext(source, {
        URL, Response, Map, caches,
        fetch: async (req, options) => { fetched.push({ req, options }); return network(req, options); },
        self: { location: { origin }, addEventListener: (name, callback) => events[name] = callback,
            skipWaiting: async () => {}, clients: { claim: async () => {} } }
    });
    return {
        stores, fetched, writes, caches, setNetwork: fn => network = fn,
        async lifecycle(name) { let work; events[name]({ waitUntil: p => work = p }); await work; },
        request(url, options = {}) {
            let work;
            events.fetch({ request: { url: origin + url, method: 'GET', mode: 'cors', headers: new Headers(), ...options },
                respondWith: p => work = p });
            return work;
        }
    };
}

test('install caches only the minimum public shell and activation deletes only own old versions', async () => {
    const h = harness();
    h.setNetwork(async req => response(origin + req, { type: req === '/offline.html' ? 'text/html' : req.endsWith('.js') ? 'text/javascript' : 'text/css', control: req === '/offline.html' ? 'no-store' : 'max-age=0, public, must-revalidate' }));
    await h.lifecycle('install');
    assert.deepEqual(h.writes, ['/offline.html', '/css/luaspets.css', '/js/pwa.js']);
    assert.equal(h.fetched[0].options.credentials, 'omit');
    await h.caches.open('luas-pets-pwa-v0'); await h.caches.open('other-app');
    await h.lifecycle('activate');
    assert.deepEqual([...h.stores.keys()].sort(), ['luas-pets-pwa-v2', 'other-app']);
});

test('install rejects redirect, private, error, wrong type and wrong URL', async () => {
    for (const options of [{ redirected: true }, { control: 'private' }, { status: 500 }, { type: 'application/json' }]) {
        const h = harness(); h.setNetwork(async req => response(origin + req, { type: 'text/html', ...options }));
        await assert.rejects(h.lifecycle('install')); assert.equal(h.writes.length, 0);
    }
    const h = harness(); h.setNetwork(async () => response(origin + '/login', { type: 'text/html' }));
    await assert.rejects(h.lifecycle('install'));
});

test('mutations, APIs, sensitive non-navigation GETs, queries and external origins are untouched', () => {
    const h = harness();
    for (const method of ['POST', 'PUT', 'PATCH', 'DELETE']) assert.equal(h.request('/css/luaspets.css', { method }), undefined);
    for (const url of ['/api/pets', '/notificaciones', '/images/login.jpg', '/unknown', '/css/luaspets.css?v=1',
        '/cliente', '/cliente/dashboard', '/doctor/patients', '/admin', '/perfil', '/2fa', '/login', '/logout'])
        assert.equal(h.request(url), undefined, url);
    assert.equal(h.request('/css/luaspets.css', { url: 'https://cdn.test/css/luaspets.css' }), undefined);
    assert.equal(h.fetched.length, 0); assert.equal(h.writes.length, 0);
});

test('every navigation including private routes uses network only, generic offline on network failure', async () => {
    const h = harness();
    const cache = await h.caches.open('luas-pets-pwa-v2');
    await cache.put('/offline.html', response(origin + '/offline.html', { type: 'text/html', body: 'generic offline' }));
    h.writes.length = 0;
    for (const url of ['/', '/cliente', '/doctor', '/admin', '/perfil', '/2fa', '/login', '/logout', '/login?error', '/cliente?tab=x']) {
        h.setNetwork(async req => response(req.url, { type: 'text/html', body: 'private page' }));
        assert.equal(await (await h.request(url, { mode: 'navigate' })).text(), 'private page');
        assert.equal(h.fetched.at(-1).options.cache, 'no-store');
        h.setNetwork(async () => { throw new TypeError('offline'); });
        assert.equal(await (await h.request(url, { mode: 'navigate' })).text(), 'generic offline');
    }
    assert.equal(h.writes.length, 0);
});

test('HTTP navigation and static errors preserve the server response', async () => {
    const h = harness();
    for (const status of [401, 403, 404, 500, 503]) {
        h.setNetwork(async req => response(req.url, { status }));
        assert.equal((await h.request('/cliente', { mode: 'navigate' })).status, status);
        assert.equal((await h.request('/css/luaspets.css')).status, status);
    }
    assert.equal(h.writes.length, 0);
});

test('static caching is restricted by response identity, type and privacy headers', async () => {
    const h = harness();
    await h.request('/css/luaspets.css');
    assert.deepEqual(h.writes, ['/css/luaspets.css']);
    assert.equal(h.fetched[0].options.credentials, 'omit');
    h.setNetwork(async () => { throw new TypeError('offline'); });
    assert.equal((await h.request('/css/luaspets.css')).status, 200);
    for (const options of [{ type: 'text/html' }, { control: 'private' }, { control: 'no-store' }, { redirected: true }]) {
        h.setNetwork(async req => response(req.url, options));
        await h.request('/css/luaspets.css');
        const cache = await h.caches.open('luas-pets-pwa-v2');
        assert.equal(await cache.match('/css/luaspets.css'), undefined);
    }
    h.setNetwork(async () => response(origin + '/login'));
    await h.request('/css/luaspets.css');
    assert.equal(h.writes.length, 1);
});

test('navigation without installed offline resource gives a generic 503', async () => {
    const h = harness(); h.setNetwork(async () => { throw new TypeError('offline'); });
    const result = await h.request('/admin', { mode: 'navigate' });
    assert.equal(result.status, 503); assert.match(await result.text(), /Sin conexión/);
});

test('cache storage write failure preserves successful network response', async () => {
    const h = harness();
    h.caches.open = async () => ({ put: async () => { throw new Error('quota'); } });
    const result = await h.request('/css/luaspets.css');
    assert.equal(result.status, 200);
    assert.equal(await result.text(), 'public');
});

test('unavailable cache does not prevent network response or generic offline 503', async () => {
    const h = harness(); h.caches.open = async () => { throw new Error('storage unavailable'); };
    assert.equal((await h.request('/css/luaspets.css')).status, 200);
    h.setNetwork(async () => { throw new TypeError('offline'); });
    assert.equal((await h.request('/perfil', { mode: 'navigate' })).status, 503);
    await assert.rejects(h.request('/css/luaspets.css'), /offline/);
});


test('precache validates public CSS/JS before writing anything', async () => {
    for (const options of [{ control: 'private' }, { control: 'no-store' }, { control: 'max-age=0' },
        { type: 'text/html' }, { redirected: true }, { status: 404 }, { status: 503 }]) {
        const h = harness();
        h.setNetwork(async req => response(origin + req, req === '/offline.html'
            ? { type: 'text/html', control: 'no-store' } : options));
        await assert.rejects(h.lifecycle('install'));
        assert.equal(h.writes.length, 0);
    }
});

test('runtime public PNG and JS are cached, private or unknown icons never are', async () => {
    const h = harness();
    for (const url of ['/icons/icon-192.png', '/icons/icon-512.png', '/icons/icon-maskable-512.png',
        '/icons/apple-touch-icon.png', '/js/carrito.js', '/js/notificaciones.js', '/js/pwa.js']) {
        h.setNetwork(async req => response(req.url, { type: url.endsWith('.png') ? 'image/png' : 'text/javascript',
            control: 'max-age=0, public, must-revalidate' }));
        await h.request(url);
        h.setNetwork(async () => { throw new TypeError('offline'); });
        assert.equal((await h.request(url)).status, 200);
        h.setNetwork(async req => response(req.url, { type: 'text/html', control: 'private' }));
        await h.request(url);
        await assert.rejects((async () => {
            h.setNetwork(async () => { throw new TypeError('offline'); });
            await h.request(url);
        })());
    }
    assert.equal(h.request('/icons/future-user-avatar.png'), undefined);
    assert.equal(h.request('/icons/heart-pulse-fill.svg'), undefined);
});

test('previously seeded private pages are never served or added during navigation', async () => {
    const h = harness();
    const cache = await h.caches.open('luas-pets-pwa-v2');
    await cache.put('/offline.html', response(origin + '/offline.html', { type: 'text/html', body: 'generic offline' }));
    for (const path of ['/admin', '/doctor', '/cliente', '/perfil', '/2fa', '/login', '/logout']) {
        await cache.put(path, response(origin + path, { type: 'text/html', body: 'secret historical page' }));
    }
    h.writes.length = 0;
    h.setNetwork(async () => { throw new TypeError('offline'); });
    for (const path of ['/admin', '/doctor', '/cliente', '/perfil', '/2fa', '/login', '/logout']) {
        assert.equal(await (await h.request(path, { mode: 'navigate' })).text(), 'generic offline');
    }
    assert.deepEqual(h.writes, []);
});


test('explicit Authorization static requests remain outside Cache Storage', () => {
    const h = harness();
    assert.equal(h.request('/css/luaspets.css', { headers: new Headers({ Authorization: 'Bearer test-only' }) }), undefined);
    assert.equal(h.fetched.length, 0);
    assert.equal(h.writes.length, 0);
});
