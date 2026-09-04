(function () {
    function csrfToken() {
        var meta = document.querySelector('meta[name="_csrf"]');
        return meta ? meta.getAttribute('content') : null;
    }

    function csrfHeaderName() {
        var meta = document.querySelector('meta[name="_csrf_header"]');
        return meta ? meta.getAttribute('content') : null;
    }

    function mostrarToast(mensaje, variante) {
        var contenedor = document.getElementById('toastContainer');
        if (!contenedor || !mensaje) {
            return;
        }
        var fondo = variante === 'error' ? 'bg-danger' : (variante === 'advertencia' ? 'bg-warning' : 'bg-success');
        var textoOscuro = variante === 'advertencia';

        var toastEl = document.createElement('div');
        toastEl.className = 'toast align-items-center border-0 ' + fondo + ' ' + (textoOscuro ? 'text-dark' : 'text-white');
        toastEl.setAttribute('role', 'alert');
        toastEl.setAttribute('aria-live', 'assertive');
        toastEl.setAttribute('aria-atomic', 'true');
        toastEl.innerHTML =
            '<div class="d-flex">' +
            '<div class="toast-body"></div>' +
            '<button type="button" class="btn-close ' + (textoOscuro ? '' : 'btn-close-white') + ' me-2 m-auto" data-bs-dismiss="toast" aria-label="Cerrar"></button>' +
            '</div>';
        toastEl.querySelector('.toast-body').textContent = mensaje;

        contenedor.appendChild(toastEl);
        var toast = new bootstrap.Toast(toastEl, { delay: 4000 });
        toast.show();
        toastEl.addEventListener('hidden.bs.toast', function () {
            toastEl.remove();
        });
    }

    function renderizarCarrito(data) {
        if (!data) {
            return;
        }
        var contador = document.getElementById('carritoContador');
        if (contador) {
            contador.textContent = data.totalItems;
        }

        var cuerpo = document.getElementById('carritoItems');
        if (cuerpo) {
            if (data.vacio) {
                cuerpo.innerHTML =
                    '<div class="lp-empty-state py-5">' +
                    '<i class="bi bi-cart-x"></i>' +
                    '<p class="mb-3">Tu carrito está vacío</p>' +
                    '<button type="button" class="lp-btn-primary" data-bs-dismiss="offcanvas">Ver productos</button>' +
                    '</div>';
            } else {
                var html = '';
                data.items.forEach(function (item) {
                    html +=
                        '<div class="lp-cart-item" data-producto-id="' + item.productoId + '">' +
                        '<div class="d-flex justify-content-between align-items-start gap-2">' +
                        '<div class="flex-grow-1" style="min-width: 0;">' +
                        '<p class="mb-0 text-truncate" style="font-size: 0.9rem; font-weight: 500; color: var(--lp-ink);">' + escaparHtml(item.nombre) + '</p>' +
                        '<p class="mb-0" style="font-size: 0.78rem; color: var(--lp-muted);">' + item.precioUnitario + ' c/u</p>' +
                        '</div>' +
                        '<button type="button" class="lp-cart-remove flex-shrink-0" data-accion="eliminar" aria-label="Eliminar producto">' +
                        '<i class="bi bi-trash"></i>' +
                        '</button>' +
                        '</div>' +
                        '<div class="d-flex justify-content-between align-items-center mt-2">' +
                        '<div class="lp-qty-control">' +
                        '<button type="button" data-accion="restar" aria-label="Disminuir cantidad"><i class="bi bi-dash"></i></button>' +
                        '<span>' + item.cantidad + '</span>' +
                        '<button type="button" data-accion="sumar" aria-label="Aumentar cantidad"><i class="bi bi-plus"></i></button>' +
                        '</div>' +
                        '<span style="font-weight: 600; color: var(--lp-ink);">' + item.subtotal + '</span>' +
                        '</div>' +
                        '</div>';
                });
                cuerpo.innerHTML = html;
            }
        }

        var subtotalTexto = document.getElementById('carritoSubtotalTexto');
        if (subtotalTexto) {
            subtotalTexto.textContent = data.total;
        }
        var totalTexto = document.getElementById('carritoTotalTexto');
        if (totalTexto) {
            totalTexto.textContent = data.total;
        }
        var btnConfirmar = document.getElementById('btnContinuarCompra');
        if (btnConfirmar) {
            btnConfirmar.disabled = data.vacio;
        }
    }

    function escaparHtml(texto) {
        var div = document.createElement('div');
        div.textContent = texto == null ? '' : texto;
        return div.innerHTML;
    }

    function peticionPost(url, params) {
        var headers = { 'Content-Type': 'application/x-www-form-urlencoded' };
        var token = csrfToken();
        var headerName = csrfHeaderName();
        if (token && headerName) {
            headers[headerName] = token;
        }
        return fetch(url, {
            method: 'POST',
            headers: headers,
            body: new URLSearchParams(params || {})
        }).then(function (response) {
            return response.json();
        });
    }

    function consultarCarrito() {
        return fetch('/cliente/api/carrito', { headers: { Accept: 'application/json' } })
            .then(function (response) {
                return response.json();
            })
            .then(function (data) {
                renderizarCarrito(data);
                return data;
            })
            .catch(function () {
                mostrarToast('No se pudo actualizar el carrito. Intenta nuevamente.', 'error');
            });
    }

    function agregarProducto(productoId, cantidad, boton) {
        if (boton) {
            boton.disabled = true;
        }
        peticionPost('/cliente/api/carrito/agregar', { productoId: productoId, cantidad: cantidad || 1 })
            .then(function (data) {
                renderizarCarrito(data);
                if (data.exito) {
                    mostrarToast(data.mensaje, 'exito');
                    abrirPanel();
                } else if (data.mensaje) {
                    mostrarToast(data.mensaje, 'advertencia');
                }
            })
            .catch(function () {
                mostrarToast('No se pudo actualizar el carrito. Intenta nuevamente.', 'error');
            })
            .finally(function () {
                if (boton) {
                    boton.disabled = false;
                }
            });
    }

    function actualizarCantidadProducto(productoId, cantidad, boton) {
        if (boton) {
            boton.disabled = true;
        }
        peticionPost('/cliente/api/carrito/actualizar', { productoId: productoId, cantidad: cantidad })
            .then(function (data) {
                renderizarCarrito(data);
                if (!data.exito && data.mensaje) {
                    mostrarToast(data.mensaje, 'advertencia');
                }
            })
            .catch(function () {
                mostrarToast('No se pudo actualizar el carrito. Intenta nuevamente.', 'error');
            })
            .finally(function () {
                if (boton) {
                    boton.disabled = false;
                }
            });
    }

    function eliminarProducto(productoId, boton) {
        if (boton) {
            boton.disabled = true;
        }
        peticionPost('/cliente/api/carrito/eliminar', { productoId: productoId })
            .then(function (data) {
                renderizarCarrito(data);
            })
            .catch(function () {
                mostrarToast('No se pudo actualizar el carrito. Intenta nuevamente.', 'error');
            })
            .finally(function () {
                if (boton) {
                    boton.disabled = false;
                }
            });
    }

    function abrirPanel() {
        var panelEl = document.getElementById('carritoPanel');
        if (!panelEl || typeof bootstrap === 'undefined') {
            return;
        }
        var instancia = bootstrap.Offcanvas.getOrCreateInstance(panelEl);
        instancia.show();
    }

    document.addEventListener('DOMContentLoaded', function () {
        if (!document.getElementById('carritoPanel')) {
            return;
        }

        consultarCarrito();

        var botonCarrito = document.getElementById('btnAbrirCarrito');
        if (botonCarrito) {
            botonCarrito.addEventListener('click', abrirPanel);
        }

        document.querySelectorAll('.lp-form-agregar-carrito').forEach(function (formulario) {
            formulario.addEventListener('submit', function (evento) {
                evento.preventDefault();
                var boton = formulario.querySelector('.lp-btn-agregar-carrito');
                var productoId = formulario.querySelector('input[name="productoId"]').value;
                agregarProducto(productoId, 1, boton);
            });
        });

        var cuerpo = document.getElementById('carritoItems');
        if (cuerpo) {
            cuerpo.addEventListener('click', function (evento) {
                var boton = evento.target.closest('button[data-accion]');
                if (!boton) {
                    return;
                }
                var fila = boton.closest('.lp-cart-item');
                if (!fila) {
                    return;
                }
                var productoId = fila.getAttribute('data-producto-id');
                var accion = boton.getAttribute('data-accion');

                if (accion === 'eliminar') {
                    eliminarProducto(productoId, boton);
                    return;
                }

                var spanCantidad = fila.querySelector('.lp-qty-control span');
                var cantidadActual = spanCantidad ? parseInt(spanCantidad.textContent, 10) : 1;
                var nuevaCantidad = accion === 'sumar' ? cantidadActual + 1 : cantidadActual - 1;
                actualizarCantidadProducto(productoId, nuevaCantidad, boton);
            });
        }
    });
})();
