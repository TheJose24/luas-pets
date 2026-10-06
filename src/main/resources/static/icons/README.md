# Iconos de LUAS Pets

Reutilizan `bi-heart-pulse-fill`, el símbolo existente en navbar/sidebar, con
`#0f766e` y blanco de `css/luaspets.css`; no se introduce una identidad nueva.
Fuente oficial Bootstrap Icons **1.11.3**, la misma versión del layout:
https://github.com/twbs/icons/blob/v1.11.3/icons/heart-pulse-fill.svg
La fuente original está en `heart-pulse-fill.svg` y su licencia MIT completa
se conserva en `LICENSE-bootstrap-icons.txt`.

- `icon-192.png`: 192 × 192, manifest `any` y favicon.
- `icon-512.png`: 512 × 512, manifest `any`.
- `icon-maskable-512.png`: 512 × 512, manifest `maskable`.
- `apple-touch-icon.png`: 180 × 180, Apple Touch Icon.

Todos tienen fondo opaco. El maskable limita el símbolo al cuadrado central
280 × 280: sus esquinas quedan a 198 px del centro, dentro del radio seguro
204.8 px (40% de 512). Los SVG derivados conservan geometría y colores.

Generación local reproducible con librsvg (`rsvg-convert`), sin dependencias
ni llamadas externas en la aplicación:

```sh
rsvg-convert -w 192 -h 192 icon-512.svg -o icon-192.png
rsvg-convert -w 512 -h 512 icon-512.svg -o icon-512.png
rsvg-convert -w 512 -h 512 icon-maskable-512.svg -o icon-maskable-512.png
rsvg-convert -w 180 -h 180 icon-512.svg -o apple-touch-icon.png
```

La instalación requiere HTTPS (localhost en desarrollo), navegador compatible
y sus opciones de instalación. El manifest solicita modo `standalone`.
Los SVG/licencia/README son fuentes; el Worker cachea solo los PNG explícitos.
