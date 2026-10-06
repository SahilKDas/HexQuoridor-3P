// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
import { spawn } from 'node:child_process';
import assert from 'node:assert/strict';
import { prepare } from './java.mjs';
const cp=await prepare();
const server=spawn('java',['-Xmx128m','-cp',cp,'hexquoridor.Server'],{
  stdio:['ignore','pipe','pipe'],env:{...process.env,PORT:'18080',WS_PORT:'18081'}
});
let output=''; server.stdout.on('data',d=>output+=d);server.stderr.on('data',d=>output+=d);
const sockets=[];
function delay(ms){return new Promise(resolve=>setTimeout(resolve,ms));}
async function connection() {
  const ws=new WebSocket('ws://127.0.0.1:18081/ws'); sockets.push(ws);
  const messages=[],waiters=[];
  ws.addEventListener('message',event=>{
    const data=JSON.parse(event.data);
    const index=waiters.findIndex(w=>w.types.includes(data.type));
    if(index>=0) {const w=waiters.splice(index,1)[0];clearTimeout(w.timer);w.resolve(data);}
    else messages.push(data);
  });
  await new Promise((resolve,reject)=>{ws.onopen=resolve;ws.onerror=reject;});
  return {ws,send:data=>ws.send(JSON.stringify(data)),next(type) {
    const types=Array.isArray(type)?type:[type];
    const i=messages.findIndex(m=>types.includes(m.type));
    if(i>=0) return Promise.resolve(messages.splice(i,1)[0]);
    return new Promise((resolve,reject)=>{
      const waiter={types,resolve,timer:setTimeout(()=>reject(new Error('Timeout waiting '+type)),5000)};
      waiters.push(waiter);
    });
  },clear(){messages.length=0;}};
}
try {
  let alive=false;
  for(let i=0;i<60;i++) {
    try {const response=await fetch('http://127.0.0.1:18080/health');if(response.ok){alive=true;break;}}catch{}
    await delay(100);
  }
  assert.ok(alive,output);
  const clients=[];
  for(let p=0;p<3;p++) {
    const client=await connection();clients.push(client);
    client.send({type:'JOIN',room:'integration',mode:'online'});
    client.welcome=await client.next('WELCOME');assert.equal(client.welcome.seat,p);
    const state=await client.next('STATE');assert.equal(state.ready,p===2);
  }
  await delay(100);clients.forEach(c=>c.clear());
  const [a,b,c]=clients;
  b.send({type:'MOVE_PAWN',destination:0,revision:0});assert.match((await b.next('ERROR')).message,/another/);
  a.send({type:'PASS',revision:0});assert.match((await a.next('ERROR')).message,/Unknown/);
  a.send({type:'MOVE_PAWN',destination:0,revision:999});assert.match((await a.next('ERROR')).message,/changed/);
  a.send({type:'MOVE_PAWN',destination:1.5,revision:0});await a.next('ERROR');
  a.send({type:'JOIN',room:'other',mode:'local'});assert.match((await a.next('ERROR')).message,/Already/);
  const fresh=await connection();fresh.send({type:'JOIN',room:'integration',mode:'online'});assert.match((await fresh.next('ERROR')).message,/full/);
  fresh.ws.close();
  // Take legal actions and verify identical broadcasts.
  const board=a.welcome.cells;
  const spawn=board.findIndex(c=>c.q===0 && c.r===5);
  const dest=board.findIndex(c=>c.q===1 && c.r===4);
  assert.notEqual(spawn,dest);
  a.send({type:'MOVE_PAWN',destination:dest,revision:0});
  const states=await Promise.all(clients.map(c=>c.next('STATE')));
  states.forEach(s=>assert.deepEqual(s.state,states[0].state));
  assert.equal(states[0].state.turn,1);assert.equal(states[0].state.revision,1);
  b.send({type:'PLACE_WALL',wallId:states[1].state.legalWalls[0],revision:1});
  const after=await Promise.all(clients.map(c=>c.next('STATE')));
  assert.equal(after[0].state.remaining[1],5);assert.equal(after[0].state.turn,2);
  c.send({type:'BOT_MOVE',revision:2});assert.match((await c.next('ERROR')).message,/local only/);
  b.ws.close();assert.equal((await a.next('STATE')).ready,false);
  c.send({type:'MOVE_PAWN',destination:after[2].state.legalMoves[0],revision:2});
  assert.match((await c.next('ERROR')).message,/Waiting/);
  const reconnect=await connection();reconnect.send({type:'JOIN',room:'integration',mode:'online',token:b.welcome.token});
  assert.equal((await reconnect.next('WELCOME')).seat,1);
  assert.equal((await reconnect.next('STATE')).ready,true);
  const local=await connection();local.send({type:'JOIN',room:'local-integration',mode:'local'});
  await local.next('WELCOME');let state=(await local.next('STATE')).state;
  local.send({type:'PLACE_WALL',wallId:state.legalWalls[0],revision:state.revision});
  state=(await local.next('STATE')).state;
  let won=false;
  for(let i=0;i<150;i++){
    local.send({type:'BOT_MOVE',revision:state.revision});
    const update=await local.next(['STATE','GAME_OVER']);
    state=update.state;
    if(state.winner>=0){won=true;break;}
  }
  assert.ok(won,'deterministic bot reaches a winner');
  local.send({type:'MOVE_PAWN',destination:0,revision:state.revision});
  await local.next('ERROR');
  console.log('PASS: HTTP health, room seats, authoritative turns, invalid/stale actions, state broadcasts, wall inventory, disconnect pause, saved-seat reconnect, bot full game, terminal actions');
} finally {
  sockets.forEach(socket=>socket.close());
  server.kill();
}
