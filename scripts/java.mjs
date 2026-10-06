// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
import { spawn } from 'node:child_process';
import { readFile, mkdir } from 'node:fs/promises';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
const root = fileURLToPath(new URL('../', import.meta.url));
const deps = [
  ['org/java-websocket/Java-WebSocket/1.6.0/Java-WebSocket-1.6.0.jar','websocket.jar'],
  ['com/google/code/gson/gson/2.13.2/gson-2.13.2.jar','gson.jar'],
  ['org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar','slf4j-api.jar'],
  ['org/slf4j/slf4j-simple/2.0.17/slf4j-simple-2.0.17.jar','slf4j-simple.jar'],
];
const hashes = {
  'websocket.jar':'eae29213e4f16515639c28957200f011b3967fffcada1962cf0255d24919c22f',
  'gson.jar':'dd0ce1b55a3ed2080cb70f9c655850cda86c206862310009dcb5e5c95265a5e0',
  'slf4j-api.jar':'7b751d952061954d5abfed7181c1f645d336091b679891591d63329c622eb832',
  'slf4j-simple.jar':'ddfea59ac074c6d3e24ac2c38622d2d963895e17f70b38ed4bdae4d780be6964'
};
function verify(data,name) {
  if(createHash('sha256').update(data).digest('hex')!==hashes[name]) throw new Error('Dependency checksum mismatch: '+name);
}
export async function prepare() {
  const { writeFile, readdir } = await import('node:fs/promises');
  const dir = join(root,'.tools','java'); await mkdir(dir,{recursive:true});
  for (const [path,name] of deps) {
    const dest=join(dir,name);
    try { verify(await readFile(dest),name); } catch {
      const response=await fetch('https://repo.maven.apache.org/maven2/'+path);
      if (!response.ok) throw new Error('Dependency download failed: '+response.status);
      const data=Buffer.from(await response.arrayBuffer());
      if(data[0]!==0x50 || data[1]!==0x4b) throw new Error('Invalid jar: '+name);
      verify(data,name);
      await writeFile(dest,data);
    }
  }
  const cp=deps.map(([,name])=>join(dir,name)).join(process.platform==='win32'?';':':');
  const out=join(root,'server','build','classes'); await mkdir(out,{recursive:true});
  async function javaFiles(dir) {
    const result=[];
    for (const entry of await readdir(dir,{withFileTypes:true})) {
      const p=join(dir,entry.name);
      if(entry.isDirectory()) result.push(...await javaFiles(p)); else if(p.endsWith('.java')) result.push(p);
    }
    return result;
  }
  const files=[...await javaFiles(join(root,'server','src','main','java')),...await javaFiles(join(root,'server','src','test','java'))];
  await command('javac',['--release','21','-cp',cp,'-d',out,...files]);
  return out+(process.platform==='win32'?';':':')+cp;
}
export function command(binary,args,options={}) {
  return new Promise((resolve,reject)=>{
    const child=spawn(binary,args,{cwd:root,stdio:'inherit',...options});
    child.on('error',reject); child.on('exit',code=>code===0?resolve():reject(new Error(binary+' exited '+code)));
  });
}
export async function launch(task='run') {
  const cp=await prepare();
  const name=task==='test'?'hexquoridor.EngineTest':'hexquoridor.Server';
  if(task==='build') return;
  await command('java',['-Xmx128m','-cp',cp,name]);
}
if(process.argv[1]===fileURLToPath(import.meta.url)) {
  launch(process.argv[2]).catch(error=>{console.error(error.message);process.exitCode=1;});
}
