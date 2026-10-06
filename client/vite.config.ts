// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
import { defineConfig } from 'vite';
import solid from 'vite-plugin-solid';
export default defineConfig({
  plugins: [solid()],
  server: { proxy: { '/ws': { target: 'ws://127.0.0.1:8081', ws: true } } },
  build: { target: 'es2022' }
});
