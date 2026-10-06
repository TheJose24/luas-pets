(function () {
    'use strict';
    if (!('serviceWorker' in navigator) || !window.isSecureContext) {
        return;
    }
    window.addEventListener('load', function () {
        navigator.serviceWorker.register('/sw.js', { scope: '/', updateViaCache: 'none' })
            .catch(function (error) {
                console.warn('No se pudo registrar la PWA de Luas Pets.', error);
            });
    });
})();
