import {
  defineConfig,
} from 'vitest/config';

export default defineConfig({
  test: {
    include: [
      'tests/integration/**/*.integration.spec.ts',
    ],

    environment: 'node',

    testTimeout: 15_000,

    hookTimeout: 15_000,

    passWithNoTests: false,
  },
});