import '@testing-library/jest-dom/vitest';

// Limpieza entre tests: localStorage (sesión — A-003) y mocks de `fetch`
// (vi.stubGlobal) para evitar acoplamiento entre tests (diseño §8/§9).
beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
  vi.unstubAllGlobals();
});
