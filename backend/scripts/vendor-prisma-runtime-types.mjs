// Copies the type declarations of Prisma's runtime packages into src/generated.
//
// Codacy analyzes the repo without running `npm install`, so the generated client's
// imports of `@prisma/client/runtime/*` resolve to error types there. tsconfig.json
// maps those imports to these committed copies so the types resolve without node_modules.
// Runs as part of `npm run generate`; rerun it after upgrading Prisma.

import { cpSync, mkdirSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const backend = join(dirname(fileURLToPath(import.meta.url)), '..');
const modules = join(backend, 'node_modules', '@prisma');
const out = join(backend, 'src', 'generated', 'prisma-runtime');

rmSync(out, { recursive: true, force: true });
mkdirSync(join(out, 'client'), { recursive: true });

for (const file of ['client.d.ts', 'index-browser.d.ts']) {
  cpSync(join(modules, 'client', 'runtime', file), join(out, 'client', file));
}

cpSync(join(modules, 'client-runtime-utils', 'dist'), join(out, 'client-runtime-utils'), {
  recursive: true,
  filter: (src) => !/\.(js|mjs|map|d\.mts)$/.test(src),
});
