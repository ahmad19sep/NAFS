import { defineConfig } from 'vitest/config'
import path from 'node:path'

// Unit tests for the pure logic in src/lib — scoring, aggregation and the
// report contract. No DOM or Next runtime is involved, so the default node
// environment is enough; the alias just mirrors tsconfig's "@/*" paths.
export default defineConfig({
  resolve: {
    alias: { '@': path.resolve(__dirname, 'src') },
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
})
