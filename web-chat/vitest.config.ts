import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    // sanitizeChatHtml parses with DOMParser and rebuilds nodes, so the tests need a DOM
    // implementation. jsdom is a dev dependency of this workspace; nothing here is bundled into
    // `vite build`, and `vitest.config.ts` sits outside `src` so `tsc --noEmit` ignores it.
    environment: 'jsdom',
    include: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
  },
});
