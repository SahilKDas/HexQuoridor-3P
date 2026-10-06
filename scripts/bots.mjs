// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
import { prepare, command } from './java.mjs';
const task=process.argv[2] || 'train';
const names={train:'hexquoridor.BotTraining',arena:'hexquoridor.BotArena',verify:'hexquoridor.BotTest'};
if(!names[task])throw new Error('Use train, arena, or verify');
const args=process.argv.slice(3).filter(arg=>arg!=='--');
const cp=await prepare();
await command('java',['-Xmx512m','-cp',cp,names[task],...args]);
await import('./check-size.mjs');
