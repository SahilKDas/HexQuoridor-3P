// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
import { readdir, lstat } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';
const root=fileURLToPath(new URL('../',import.meta.url));
const limit=1_000_000_000;
async function size(dir) {
  let bytes=0;
  for(const entry of await readdir(dir,{withFileTypes:true})) {
    const path=join(dir,entry.name), stat=await lstat(path);
    if(stat.isSymbolicLink()) bytes+=stat.size;
    else if(entry.isDirectory()) bytes+=await size(path);
    else bytes+=stat.size;
  }
  return bytes;
}
const total=await size(root);
console.log('Repository including .git, dependencies, caches and artifacts: '+total.toLocaleString()+' bytes ('+(total/1e6).toFixed(2)+' MB)');
if(total>=limit) {console.error('FAIL: repository must stay strictly under 1,000,000,000 bytes');process.exitCode=1;}
else console.log('PASS: '+(100*(1-total/limit)).toFixed(1)+'% of the 1 GB budget remains.');
