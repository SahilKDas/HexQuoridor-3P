// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
import { spawn } from 'node:child_process';
import { prepare } from './java.mjs';
import { fileURLToPath } from 'node:url';
const root=fileURLToPath(new URL('../',import.meta.url));
const children=[];
let stopping=false;
function stop(code=0) {
  if(stopping) return; stopping=true;
  for(const child of children) child.kill();
  process.exitCode=code;
}
try {
  const cp=await prepare();
  const java=spawn('java',['-Xmx128m','-cp',cp,'hexquoridor.Server'],{cwd:root,stdio:'inherit'});
  // Run Vite's JavaScript entry directly to avoid shell quoting and Windows .cmd wrappers.
  const frontend=spawn(process.execPath,['node_modules/vite/bin/vite.js','--host','127.0.0.1'],{cwd:root+'/client',stdio:'inherit'});
  children.push(java,frontend);
  for(const child of children) {
    child.on('error',error=>{console.error(error.message);stop(1);});
    child.on('exit',code=>{if(!stopping) stop(code||0);});
  }
  process.on('SIGINT',()=>stop());process.on('SIGTERM',()=>stop());
} catch(error) { console.error(error.message);stop(1); }
