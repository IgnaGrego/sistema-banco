# Guía de diseño UI — sistema de tokens (SPEC-009)

**Estado:** aprobada (entregable de FR-001, SPEC-009 — issue #28).

Esta guía es la **única fuente de verdad de los tokens** del frontend (A-001):
los valores aquí definidos **deben coincidir exactamente** con las variables
CSS del bloque `:root` de `frontend/src/index.css` (FR-002, AC-002). Se define
**antes** de la implementación del rediseño de componentes y páginas
(FR-003..FR-010, AC-001).

---

## 1. Paleta

### 1.1 Azul institucional y neutros

| Token | Valor | Uso |
| --- | --- | --- |
| `--color-primario` | `#1f6feb` | acciones principales, links, botón submit, anillo de foco |
| `--color-primario-oscuro` | `#102a43` | fondo del header, títulos y montos destacados |
| `--color-fondo` | `#f5f7fa` | fondo de página (`body`) |
| `--color-superficie` | `#ffffff` | tarjetas, secciones, login, confirmaciones |
| `--color-borde` | `#d9e2ec` | bordes de superficies (tarjetas, secciones) |
| `--color-borde-input` | `#bcccdc` | bordes de inputs/selects |
| `--color-texto` | `#1f2933` | texto principal |
| `--color-texto-suave` | `#486581` | texto secundario, estados vacíos |
| `--color-deshabilitado` | `#9fb3c8` | fondo de controles deshabilitados |

### 1.2 Colores semánticos de estado (fondo / borde / texto)

| Estado | Fondo | Borde | Texto |
| --- | --- | --- | --- |
| Éxito | `#e3fcec` | `#9ae6b4` | `#1a7f37` |
| Error | `#fdecea` | `#e8a0a0` | `#8b0000` |
| Advertencia | `#fff8e6` | `#f0d78c` | `#7a4d00` |
| Info | `#e8f0fe` | `#b3c9f0` | `#1f4e8c` |

Se usan para alertas, confirmaciones y estados (p. ej. `.confirmacion` con
éxito, `.estado-error` y `.error-general` con error).

---

## 2. Tipografía

- **Familia** (`--font-familia`):
  `system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif` (stack existente).
- **Line-height base** (`--line-height-base`): `1.5`.
- **Escala de tamaños** (en `rem`, base 16 px):

| Token | Valor | Uso |
| --- | --- | --- |
| `--font-xs` | `0.75rem` (12 px) | texto pequeño, fechas, DNI |
| `--font-sm` | `0.875rem` (14 px) | errores de campo, textos secundarios |
| `--font-base` | `1rem` (16 px) | cuerpo |
| `--font-lg` | `1.125rem` (18 px) | subtítulos/secciones |
| `--font-xl` | `1.25rem` (20 px) | títulos de sección y tarjeta |
| `--font-2xl` | `1.5rem` (24 px) | títulos de página y **montos destacados** |
| `--font-3xl` | `1.875rem` (30 px) | marca del login |

---

## 3. Espaciado (base 4 px)

| Token | Valor | Uso |
| --- | --- | --- |
| `--espacio-1` | `0.25rem` (4 px) | gaps mínimos |
| `--espacio-2` | `0.5rem` (8 px) | padding internos pequeños |
| `--espacio-3` | `0.75rem` (12 px) | padding de tarjetas compactas |
| `--espacio-4` | `1rem` (16 px) | padding estándar |
| `--espacio-5` | `1.5rem` (24 px) | padding de secciones y login |
| `--espacio-6` | `2rem` (32 px) | separación entre bloques |
| `--espacio-8` | `3rem` (48 px) | márgenes de página |

---

## 4. Sombras

| Token | Valor | Uso |
| --- | --- | --- |
| `--sombra-sm` | `0 1px 2px rgba(16, 42, 67, 0.08)` | sombra sutil de tarjetas |
| `--sombra-md` | `0 4px 12px rgba(16, 42, 67, 0.12)` | sombra de login y secciones |
| `--sombra-lg` | `0 8px 24px rgba(16, 42, 67, 0.16)` | sombra de estados/elevación |

---

## 5. Redondeos

| Token | Valor | Uso |
| --- | --- | --- |
| `--radio-sm` | `4px` | inputs, botones |
| `--radio-md` | `8px` | tarjetas de cuenta, login, secciones |
| `--radio-lg` | `12px` | tarjetas destacadas |
| `--radio-xl` | `16px` | elementos hero (estados) |
| `--radio-redondo` | `50%` | elementos circulares (spinner de carga) |

---

## 6. Breakpoints mobile-first

Diseño **mobile-first** (una columna en móvil); las media queries usan
`min-width` con los valores literales (las variables CSS no pueden usarse en
condiciones `@media` — A-006):

| Breakpoint | Valor |
| --- | --- |
| `sm` | `640px` |
| `md` | `768px` |
| `lg` | `1024px` |

El layout no se rompe (sin desbordes ni solapamientos) en pantallas de
**≥ 360 px** (BR-009). Todas las medidas usan unidades relativas (`rem`).

---

## 7. Accesibilidad

- **Contraste WCAG AA** (A-007): 4.5:1 para texto normal y 3:1 para texto
  grande (≥ 18 px o ≥ 14 px bold) y componentes UI. Pares críticos verificados:

  | Par | Ratio ≈ | AA |
  | --- | --- | --- |
  | `#1f2933` sobre `#ffffff` | 14.4:1 | ✓ |
  | `#486581` sobre `#ffffff` | 6.0:1 | ✓ |
  | `#486581` sobre `#f5f7fa` | 5.6:1 | ✓ |
  | `#1f6feb` sobre `#ffffff` | 4.6:1 | ✓ (también ≥ 3:1 UI) |
  | `#ffffff` sobre `#102a43` | 14.6:1 | ✓ |
  | `#8b0000` sobre `#fdecea` | 7.5:1 | ✓ |
  | `#1a7f37` sobre `#e3fcec` | 4.7:1 | ✓ |
  | `#7a4d00` sobre `#fff8e6` | 6.9:1 | ✓ |
  | `#1f4e8c` sobre `#e8f0fe` | 7.3:1 | ✓ |

  `#9fb3c8` (deshabilitado) está exento: WCAG 1.4.3 excluye componentes
  inactivos.

- **Focus visible** (BR-007): `:focus-visible` con anillo de 2 px de
  `--color-primario` (`#1f6feb`, ≥ 3:1 sobre superficies claras) en inputs,
  selects, botones y links.
- **Labels**: asociación `label ↔ input` vía `CampoFormulario` (queries por
  `getByLabelText`).
- **ARIA**: `role="status"` en estados de carga/confirmación y `role="alert"`
  en errores y alertas de formulario (BR-006).

---

## 8. Notas de implementación

- **Única hoja de estilos**: `frontend/src/index.css` (CSS plano con variables,
  sin framework ni dependencias — ADR-008, BR-005). Todas las reglas usan las
  variables de esta guía; **no** se escriben valores de color/espaciado/radio/
  sombra literales fuera de `:root` (BR-002, AC-003).
- **Semántica**: la marca "Banco" es texto estilizado (no heading ni link —
  A-009); el `h1` único por página se conserva.