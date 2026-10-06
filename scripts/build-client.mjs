// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
const root=fileURLToPath(new URL('../',import.meta.url));
function pnpm(args) {
  return new Promise((resolve,reject)=>{
    // All arguments are fixed build commands; user data is never passed to a shell.
    const child=process.platform==='win32'
      ? spawn(process.env.ComSpec || 'cmd.exe',['/d','/s','/c','pnpm '+args.join(' ')],{cwd:root,stdio:'inherit',windowsHide:true})
      : spawn('pnpm',args,{cwd:root,stdio:'inherit'});
    child.on('error',reject);child.on('exit',code=>code===0?resolve():reject(new Error('pnpm exited '+code)));
  });
}
await pnpm(['install','--frozen-lockfile']);
await pnpm(['--dir','client','build']);
await import('./check-size.mjs');
