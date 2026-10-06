// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
import { createMemo, createSignal, For, Show } from 'solid-js';
import type { Cell, Wall, GameState } from './types';

const SIZE = 31;
export const COLORS = ['#efb86a', '#80c7c2', '#b6a1ea'];
export function center(c: Cell) { return {x: Math.sqrt(3) * SIZE * (c.q + c.r / 2), y: 1.5 * SIZE * c.r}; }
function polygon(c: Cell) {
  const p = center(c);
  return Array.from({length: 6}, (_, i) => {
    const angle = Math.PI / 180 * (60 * i - 30);
    return (p.x + (SIZE - 1.8) * Math.cos(angle)) + ',' + (p.y + (SIZE - 1.8) * Math.sin(angle));
  }).join(' ');
}
export function wallPoints(w: Wall, cells: Cell[]) {
  function edge(id: number) {
    const a = center(cells[Math.floor(id / 91)]), b = center(cells[id % 91]);
    const dx = b.x - a.x, dy = b.y - a.y, length = Math.hypot(dx, dy);
    const mid = {x: (a.x + b.x) / 2, y: (a.y + b.y) / 2};
    return [{x: mid.x - dy / length * SIZE / 2, y: mid.y + dx / length * SIZE / 2},
      {x: mid.x + dy / length * SIZE / 2, y: mid.y - dx / length * SIZE / 2}];
  }
  const a = edge(w.edgeA), b = edge(w.edgeB);
  let ai = 0, bi = 0, best = Infinity;
  for (let i = 0; i < 2; i++) for (let j = 0; j < 2; j++) {
    const distance = Math.hypot(a[i].x - b[j].x, a[i].y - b[j].y);
    if (distance < best) { best = distance; ai = i; bi = j; }
  }
  const vertex = {x: (a[ai].x + b[bi].x) / 2, y: (a[ai].y + b[bi].y) / 2};
  return {vertex, points: [a[1 - ai], vertex, b[1 - bi]].map(p => p.x + ',' + p.y).join(' ')};
}
interface Props {
  cells: Cell[]; catalog: Wall[]; targets: number[]; state: GameState;
  wallMode: boolean; orientation: number; canAct: boolean;
  rotate: (delta: number) => void; move: (id: number) => void; wall: (id: number) => void;
}
export default function HexGrid(props: Props) {
  const [hover, setHover] = createSignal<string>();
  const vertices = createMemo(() => {
    const grouped = new Map<string, Wall[]>();
    for (const w of props.catalog) { const list = grouped.get(w.vertex) || []; list.push(w); grouped.set(w.vertex, list); }
    return [...grouped.entries()].map(([key, walls]) => ({key, walls, ...wallPoints(walls[0], props.cells).vertex}));
  });
  const selected = createMemo(() => vertices().find(v => v.key === hover())?.walls.find(w => w.orientation === props.orientation));
  const legal = () => !!selected() && props.state.legalWalls.includes(selected()!.id);
  const preview = () => selected() ? wallPoints(selected()!, props.cells).points : '';
  return <svg class="hex-board" viewBox="-330 -275 660 550" role="group" aria-label="Hexagonal Quoridor game board"
    onWheel={event => { if (props.wallMode) { event.preventDefault(); props.rotate(event.deltaY > 0 ? 1 : -1); } }}
    onPointerLeave={() => setHover(undefined)}>
    <defs><radialGradient id="board-glow"><stop offset="0%" stop-color="#22362d"/><stop offset="100%" stop-color="#142019"/></radialGradient></defs>
    <circle r="257" fill="url(#board-glow)" opacity=".7"/>
    <For each={props.cells}>{(cell, id) => {
      const targetPlayer = () => props.targets.indexOf(id());
      const isLegal = () => !props.wallMode && props.canAct && props.state.legalMoves.includes(id());
      return <g>
        <polygon points={polygon(cell)} classList={{hex: true, legal: isLegal(), target: targetPlayer() >= 0}}
          style={{'--target-color': targetPlayer() >= 0 ? COLORS[targetPlayer()] : undefined}}
          tabindex={isLegal() ? 0 : undefined} role={isLegal() ? 'button' : undefined}
          aria-label={'Move to ' + cell.q + ', ' + cell.r + ', ' + cell.s}
          onClick={() => isLegal() && props.move(id())}
          onKeyDown={event => { if (isLegal() && (event.key === 'Enter' || event.key === ' ')) { event.preventDefault(); props.move(id()); } }}>
          <title>{'(' + cell.q + ', ' + cell.r + ', ' + cell.s + ')' + (targetPlayer() >= 0 ? ' · P' + targetPlayer() + ' target' : '')}</title>
        </polygon>
        <Show when={targetPlayer() >= 0}><g transform={'translate(' + center(cell).x + ' ' + center(cell).y + ')'} class="target-mark">
          <circle r="17" fill="none" stroke={COLORS[targetPlayer()]} stroke-dasharray="3 4"/>
          <text y="4" fill={COLORS[targetPlayer()]} font-size="10">P{targetPlayer()} GOAL</text>
        </g></Show>
        <Show when={isLegal()}><circle cx={center(cell).x} cy={center(cell).y} r="5" class="move-dot"/></Show>
      </g>;
    }}</For>
    <For each={props.state.walls}>{id => <polyline points={wallPoints(props.catalog[id], props.cells).points} class="placed-wall"/>}</For>
    <For each={props.state.pawns}>{(cell, player) => <g transform={'translate(' + center(props.cells[cell]).x + ' ' + center(props.cells[cell]).y + ')'} class="pawn">
      <circle cy="3" r="17" fill="#000" opacity=".35"/>
      <circle r="17" fill={COLORS[player()]} stroke="#162019" stroke-width="3"/>
      <circle r="12" fill="none" stroke="#142019" opacity=".25"/>
      <text y="5" fill="#172019">P{player()}</text>
      <Show when={props.state.turn === player() && props.state.winner < 0}><circle r="22" fill="none" stroke={COLORS[player()]} stroke-width="1.5" class="active-ring"/></Show>
    </g>}</For>
    <Show when={props.wallMode && props.canAct}>
      <For each={vertices()}>{vertex => {
        const wall = () => vertex.walls.find(w => w.orientation === props.orientation)!;
        const valid = () => props.state.legalWalls.includes(wall().id);
        return <circle cx={vertex.x} cy={vertex.y} r="11" class="wall-hit" role="button" tabindex={valid() ? 0 : undefined}
          aria-label={'Place orientation ' + (props.orientation + 1) + ' wall at vertex ' + vertex.key}
          onPointerEnter={() => setHover(vertex.key)} onFocus={() => setHover(vertex.key)}
          onClick={() => { if (valid()) props.wall(wall().id); }}
          onKeyDown={event => { if (valid() && (event.key === 'Enter' || event.key === ' ')) { event.preventDefault(); props.wall(wall().id); } }}>
          <title>{valid() ? 'Place wall · R to rotate' : 'Illegal wall placement'}</title>
        </circle>;
      }}</For>
      <Show when={selected()}><polyline points={preview()} class="wall-preview" stroke={legal() ? COLORS[props.state.turn] : '#eb7777'}/></Show>
    </Show>
  </svg>;
}
