// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
import { chromium } from 'playwright-core';
import { spawn } from 'node:child_process';
import { mkdir, access } from 'node:fs/promises';
import { prepare } from './java.mjs';
import assert from 'node:assert/strict';
let server, browser;
const contexts=[];
const errors=[];
try {
  let alive=false;
  try { alive=(await fetch('http://127.0.0.1:8080/health')).ok; } catch {}
  if(!alive) {
    const cp=await prepare();
    server=spawn('java',['-Xmx128m','-cp',cp,'hexquoridor.Server'],{stdio:'ignore'});
    for(let i=0;i<60;i++) {
      try {if((await fetch('http://127.0.0.1:8080/health')).ok) {alive=true;break;}}catch{}
      await new Promise(resolve=>setTimeout(resolve,100));
    }
  }
  assert.ok(alive,'server responds');
  let executable=process.env.BROWSER_PATH;
  if(!executable) for(const candidate of [
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    '/usr/bin/chromium','/usr/bin/google-chrome'
  ]) {try{await access(candidate);executable=candidate;break;}catch{}}
  if(!executable) throw new Error('Set BROWSER_PATH to an installed Chromium browser; no browser downloads are required.');
  browser=await chromium.launch({executablePath:executable,headless:true});
  const context=await browser.newContext({viewport:{width:1440,height:1100}});
  contexts.push(context);
  const page=await context.newPage();
  page.on('pageerror',error=>errors.push(error.message));
  page.on('console',message=>{if(message.type()==='error') errors.push(message.text());});
  await page.goto('http://127.0.0.1:8080');
  await page.getByText('Connected',{exact:true}).waitFor();
  assert.equal(await page.locator('polygon.hex').count(),91);
  assert.equal(await page.locator('.pawn').count(),3);
  assert.equal(await page.locator('.hex.legal').count(),3);
  await mkdir('.tools/screenshots',{recursive:true});
  await page.screenshot({path:'.tools/screenshots/desktop.png',fullPage:true});
  await page.getByRole('button',{name:'Place wall'}).click();
  await page.locator('.wall-hit[tabindex="0"]').first().hover();
  assert.equal(await page.locator('.wall-preview').count(),1);
  await page.keyboard.press('r');
  await page.getByRole('button',{name:'Orientation 2',exact:true}).waitFor();
  assert.ok(await page.getByRole('button',{name:'Orientation 2',exact:true}).evaluate(button=>button.classList.contains('selected')));
  await page.locator('.wall-hit[tabindex="0"]').first().click();
  await page.getByText('TURN 2',{exact:true}).waitFor();
  assert.equal(await page.locator('.placed-wall').count(),1);
  assert.equal(await page.locator('.player-row').first().locator('.wall-count strong').textContent(),'5');
  await page.getByRole('button',{name:'Move pawn'}).click();
  await page.locator('.hex.legal').first().click();
  await page.getByText('TURN 3',{exact:true}).waitFor();
  await page.getByRole('button',{name:'Let a bot take this turn'}).click();
  await page.getByText('TURN 4',{exact:true}).waitFor();
  await page.getByRole('button',{name:'How to play'}).click();
  await page.getByRole('dialog',{name:'How to play'}).waitFor();
  await page.getByRole('button',{name:'Back to the board'}).click();
  await page.setViewportSize({width:390,height:844});
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),'no mobile horizontal overflow');
  await page.screenshot({path:'.tools/screenshots/mobile.png',fullPage:true});
  await page.setViewportSize({width:1440,height:1100});
  let winner=false;
  for(let i=0;i<150;i++) {
    const previous=await page.locator('.round-label').textContent();
    await page.getByRole('button',{name:'Let a bot take this turn'}).click();
    await page.waitForFunction(previous=>document.querySelector('.round-label')?.textContent!==previous,previous);
    if(await page.getByRole('dialog',{name:'Game winner'}).count()) {winner=true;break;}
  }
  assert.ok(winner,'winner dialog after a complete local game');
  await page.getByRole('button',{name:'Review the final board'}).click();
  assert.equal(await page.locator('.hex.legal').count(),0,'final board is terminal');
  await page.getByRole('button',{name:'New local game'}).click();
  await page.getByText('TURN 1',{exact:true}).waitFor();
  await page.getByRole('button',{name:'Play against CNN + NNUE'}).click();
  await page.waitForFunction(()=>document.querySelector('select[aria-label="P1 controller"]')?.value==='cnn' && document.querySelector('select[aria-label="P2 controller"]')?.value==='nnue');
  assert.equal(await page.getByLabel('P1 controller').inputValue(),'cnn');
  assert.equal(await page.getByLabel('P2 controller').inputValue(),'nnue');
  await page.locator('.hex.legal').first().click();
  await page.getByText('TURN 4',{exact:true}).waitFor();
  assert.equal(await page.getByLabel('P0 controller').inputValue(),'human');
  await page.getByRole('button',{name:'Pause bots · return all seats to Human'}).click();
  await page.waitForFunction(()=>document.querySelector('select[aria-label="P1 controller"]')?.value==='human');
  await page.locator('#single-bot').selectOption('cnn');
  await page.getByRole('button',{name:'Let a bot take this turn'}).click();
  await page.getByText('TURN 5',{exact:true}).waitFor();
  await page.locator('#single-bot').selectOption('nnue');
  await page.getByRole('button',{name:'Let a bot take this turn'}).click();
  await page.getByText('TURN 6',{exact:true}).waitFor();
  assert.ok(await page.getByText(/^Last move: NNUE/).count());
  await page.getByRole('button',{name:'Create online room'}).click();
  await page.getByText('YOU: P0',{exact:true}).waitFor();
  const room=await page.locator('.room-code code').textContent();
  const other=[];
  for(let p=1;p<3;p++) {
    const ctx=await browser.newContext({viewport:{width:1280,height:1000}});contexts.push(ctx);
    const tab=await ctx.newPage();other.push(tab);tab.on('pageerror',error=>errors.push(error.message));
    await tab.goto('http://127.0.0.1:8080/?room='+room);
    await tab.getByText('YOU: P'+p,{exact:true}).waitFor();
  }
  await page.getByText('Connected',{exact:true}).waitFor();
  assert.equal(await other[0].locator('.hex.legal').count(),0);
  await page.locator('.hex.legal').first().click();
  await other[0].getByText('TURN 2',{exact:true}).waitFor();
  assert.ok(await other[0].locator('.hex.legal').count()>0,'active online player has moves');
  await page.reload();
  await page.getByText('YOU: P0',{exact:true}).waitFor();
  await page.getByText('Connected',{exact:true}).waitFor();
  await page.getByText('TURN 2',{exact:true}).waitFor();
  assert.equal(await page.locator('.hex.legal').count(),0,'reconnect retains P0 seat');
  await other[1].close();
  await page.getByText('Waiting for players',{exact:true}).waitFor();
  assert.equal(await other[0].locator('.hex.legal').count(),0,'disconnect pauses online play');
  assert.deepEqual(errors,[],'no browser exceptions or console errors');
  console.log('PASS: desktop/mobile rendering, 91 hexes, pawn moves, wall hover/rotation/placement, HUD, full bot game, winner dialog, final board, new game, rules dialog, CNN/NNUE opponents and controls, online three-player sync, seat reconnect and pause');
  console.log('Screenshots: .tools/screenshots/desktop.png and mobile.png');
} finally {
  for(const ctx of contexts) await ctx.close();
  await browser?.close();
  server?.kill();
}
