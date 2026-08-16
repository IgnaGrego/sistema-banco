import globals from 'globals';
import tseslint from 'typescript-eslint';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';

// Flat config (ESLint 9): typescript-eslint recommended + reglas de hooks de
// React + fast refresh de Vite (solo en src/). Los archivos de test y el
// contexto de sesión (componente + hook en el mismo archivo) quedan exentos
// de la regla de fast refresh (solo-export-components).
export default tseslint.config(
  { ignores: ['dist', 'node_modules', 'coverage'] },
  {
    files: ['**/*.{ts,tsx}'],
    extends: [...tseslint.configs.recommended],
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'warn',
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
    },
  },
  {
    files: ['**/*.test.{ts,tsx}', 'src/test/**', 'src/store/auth-context.tsx'],
    rules: {
      'react-refresh/only-export-components': 'off',
    },
  },
);
