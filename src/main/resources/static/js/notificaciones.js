(function () {
    var VARIANTE_ESTILOS = {
        primary: { bg: 'var(--lp-primary-soft)', color: 'var(--lp-primary)' },
        info: { bg: '#dbeafe', color: 'var(--lp-info)' },
        warning: { bg: '#fef3c7', color: 'var(--lp-warning)' },
        danger: { bg: '#fee2e2', color: 'var(--lp-danger)' }
    };

    function csrfToken() {
        var meta = document.querySelector('meta[name="_csrf"]');
        return meta ? meta.getAttribute('content') : null;
    }

    function csrfHeaderName() {
        var meta = document.querySelector('meta[name="_csrf_header"]');
        return meta ? meta.getAttribute('content') : null;
    }

    function peticionPost(url) {
        var headers = {};
        var token = csrfToken();
        var headerName = csrfHeaderName();
        if (token && headerName) {
            headers[headerName] = token;
        }
        return fetch(url, { method: 'POST', headers: headers }).then(function (response) {
            if (!response.ok) {
                throw new Error('Respuesta no valida');
            }
            return response.json();
        });
    }

    function escaparHtml(texto) {
        var div = document.createElement('div');
        div.textContent = texto == null ? '' : texto;
        return div.innerHTML;
    }

    function actualizarBadge(noLeidas) {
        var badge = document.getElementById('notifBadge');
        if (!badge) {
            return;
        }
        if (!noLeidas || noLeidas <= 0) {
            badge.classList.add('d-none');
            badge.textContent = '0';
        } else {
            badge.classList.remove('d-none');
            badge.textContent = noLeidas > 9 ? '9+' : String(noLeidas);
        }
    }

    function renderizarItem(notificacion) {
        var estilos = VARIANTE_ESTILOS[notificacion.variante] || VARIANTE_ESTILOS.primary;
        var clases = 'lp-notif-item' + (notificacion.leida ? '' : ' lp-notif-no-leida');
        return (
            '<a href="#" class="' + clases + '" data-id="' + notificacion.id + '" data-url="' +
            (notificacion.url ? escaparHtml(notificacion.url) : '') + '">' +
            '<span class="lp-notif-item-icon" style="background-color: ' + estilos.bg + '; color: ' + estilos.color + ';">' +
            '<i class="bi ' + notificacion.icono + '"></i>' +
            '</span>' +
            '<span class="flex-grow-1" style="min-width: 0;">' +
            '<span class="lp-notif-item-titulo d-block">' + escaparHtml(notificacion.titulo) + '</span>' +
            '<span class="lp-notif-item-mensaje d-block">' + escaparHtml(notificacion.mensaje) + '</span>' +
            '<span class="d-block" style="font-size: 0.72rem; color: var(--lp-muted);">' + escaparHtml(notificacion.tiempoRelativo) + '</span>' +
            '</span>' +
            '</a>'
        );
    }

    function renderizarLista(items) {
        var cuerpo = document.getElementById('notifDropdownBody');
        if (!cuerpo) {
            return;
        }
        if (!items || items.length === 0) {
            cuerpo.innerHTML = '<div class="text-center py-4 small" style="color: var(--lp-muted);">No tienes notificaciones.</div>';
            return;
        }
        cuerpo.innerHTML = items.map(renderizarItem).join('');
    }

    function mostrarError() {
        var cuerpo = document.getElementById('notifDropdownBody');
        if (cuerpo) {
            cuerpo.innerHTML = '<div class="text-center py-4 small" style="color: var(--lp-muted);">No se pudieron cargar las notificaciones.</div>';
        }
    }

    function cargarNotificaciones() {
        var cuerpo = document.getElementById('notifDropdownBody');
        if (cuerpo) {
            cuerpo.innerHTML = '<div class="text-center py-4 small" style="color: var(--lp-muted);">Cargando...</div>';
        }
        fetch('/api/notificaciones', { headers: { Accept: 'application/json' } })
            .then(function (response) {
                if (!response.ok) {
                    throw new Error('Respuesta no valida');
                }
                return response.json();
            })
            .then(function (data) {
                renderizarLista(data.items);
                actualizarBadge(data.noLeidas);
            })
            .catch(function () {
                mostrarError();
            });
    }

    function marcarLeidaYNavegar(id, url) {
        peticionPost('/api/notificaciones/' + id + '/leer')
            .then(function (noLeidas) {
                actualizarBadge(noLeidas);
                if (url) {
                    window.location.href = url;
                } else {
                    cargarNotificaciones();
                }
            })
            .catch(function () {
                if (url) {
                    window.location.href = url;
                }
            });
    }

    function marcarTodasComoLeidas() {
        peticionPost('/api/notificaciones/leer-todas')
            .then(function (noLeidas) {
                actualizarBadge(noLeidas);
                cargarNotificaciones();
            })
            .catch(function () {
                mostrarError();
            });
    }

    document.addEventListener('DOMContentLoaded', function () {
        var toggle = document.getElementById('notifBellToggle');
        if (!toggle) {
            return;
        }

        var dropdownEl = toggle.closest('.dropdown');
        if (dropdownEl) {
            dropdownEl.addEventListener('show.bs.dropdown', cargarNotificaciones);
        }

        var cuerpo = document.getElementById('notifDropdownBody');
        if (cuerpo) {
            cuerpo.addEventListener('click', function (evento) {
                var item = evento.target.closest('.lp-notif-item');
                if (!item) {
                    return;
                }
                evento.preventDefault();
                var id = item.getAttribute('data-id');
                var url = item.getAttribute('data-url');
                marcarLeidaYNavegar(id, url);
            });
        }

        var botonMarcarTodas = document.getElementById('notifMarcarTodas');
        if (botonMarcarTodas) {
            botonMarcarTodas.addEventListener('click', function (evento) {
                evento.preventDefault();
                marcarTodasComoLeidas();
            });
        }

        // Pagina completa de notificaciones (templates/notificaciones/lista.html):
        // el boton recarga la pagina tras marcar todo como leido para que
        // Thymeleaf vuelva a renderizar los estilos de "leida" server-side.
        var botonMarcarTodasPagina = document.getElementById('btnMarcarTodasPagina');
        if (botonMarcarTodasPagina) {
            botonMarcarTodasPagina.addEventListener('click', function (evento) {
                evento.preventDefault();
                peticionPost('/api/notificaciones/leer-todas')
                    .then(function () {
                        window.location.reload();
                    })
                    .catch(function () {
                        window.location.reload();
                    });
            });
        }

        var listaPagina = document.getElementById('notificacionesListaContainer');
        if (listaPagina) {
            listaPagina.addEventListener('click', function (evento) {
                var item = evento.target.closest('.lp-notif-page-item');
                if (!item) {
                    return;
                }
                var url = item.getAttribute('data-url');
                if (!url) {
                    return;
                }
                evento.preventDefault();
                var id = item.getAttribute('data-id');
                marcarLeidaYNavegar(id, url);
            });
        }
    });
})();
