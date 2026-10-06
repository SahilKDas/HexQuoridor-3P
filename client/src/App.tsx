// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
import { createSignal, For, onCleanup, onMount, Show } from 'solid-js';
import HexGrid, { COLORS } from './HexGrid';
import { useGameEngine } from './useGameEngine';

const NAMES = ['Amber', 'Tide', 'Iris'];
const randomRoom = () => 'hex-' + crypto.randomUUID().slice(0, 8);
export default function App() {
  const game = useGameEngine();
  const [wallMode, setWallMode] = createSignal(false);
  const [orientation, setOrientation] = createSignal(0);
  const [roomInput, setRoomInput] = createSignal('');
  const [rules, setRules] = createSignal(false);
  const [copied, setCopied] = createSignal(false);
  const [dismissed, setDismissed] = createSignal(false);
  const rotate = (delta: number) => setOrientation(value => (value + delta + 3) % 3);
  function startLocal() {
    const url = new URL(location.href); url.searchParams.delete('room'); history.replaceState(null, '', url);
    setWallMode(false); setDismissed(false); game.join(randomRoom(), 'local');
  }
  function online(id: string) {
    if (!/^[a-zA-Z0-9_-]{1,32}$/.test(id)) return;
    const url = new URL(location.href); url.searchParams.set('room', id); history.replaceState(null, '', url);
    setWallMode(false); setDismissed(false); game.join(id, 'online');
  }
  function key(event: KeyboardEvent) {
    if ((event.target as HTMLElement)?.matches('input, textarea, select') || event.ctrlKey || event.metaKey || event.altKey) return;
    if (event.key.toLowerCase() === 'r') rotate(1);
    if (event.key.toLowerCase() === 'w') setWallMode(true);
    if (event.key.toLowerCase() === 'm') setWallMode(false);
    if (['1','2','3'].includes(event.key)) setOrientation(Number(event.key) - 1);
    if (event.key === 'Escape') { setRules(false); setWallMode(false); }
  }
  onMount(() => {
    const requested = new URLSearchParams(location.search).get('room');
    if (requested && /^[a-zA-Z0-9_-]{1,32}$/.test(requested)) online(requested); else startLocal();
    window.addEventListener('keydown', key);
  });
  onCleanup(() => window.removeEventListener('keydown', key));
  async function copy() {
    try {
      const url = new URL(location.href); url.search = ''; url.searchParams.set('room', game.room());
      await navigator.clipboard.writeText(url.toString()); setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch { setRoomInput(game.room()); }
  }
  const turn = () => game.state()?.turn ?? 0;
  const wallUnavailable = () => !game.canAct() || game.state()?.remaining[turn()] === 0;
  return <div class="app">
    <header class="topbar">
      <a class="brand" href="./" aria-label="HexQuoridor home"><span class="brand-symbol">⬡</span><span>HEXQUORIDOR<span class="brand-suffix"> / 3P</span></span></a>
      <div class="header-right"><span class="edition">THREE PLAYER STRATEGY</span><button class="text-button" onClick={() => setRules(true)}>How to play <span>↗</span></button></div>
    </header>
    <main>
      <section class="intro"><div><p class="eyebrow">A DIFFERENT KIND OF CHASE</p><h1>Three paths.<br/>One way to win.</h1><p class="intro-copy">Race to the next player's corner.<br/>Place a wall. Change their plans.</p></div>
        <div class="game-meta"><span class="meta-number">91</span><span>HEXES</span><i/><span class="meta-number">18</span><span>WALLS</span><i/><span class="meta-number">3</span><span>RIVALS</span></div>
      </section>
      <div class="game-layout">
        <section class="board-panel" aria-label="Game area">
          <div class="board-toolbar"><div><span class="live-dot" classList={{online: game.ready()}}/>{game.status()}</div>
            <span class="round-label">TURN { (game.state()?.revision ?? 0) + 1 }</span></div>
          <Show when={game.state()} fallback={<div class="connecting"><span class="loading-hex">⬡</span><p>{game.status()}</p><small>Start the Java server with pnpm start.</small><button onClick={startLocal}>Retry connection</button></div>}>
            {state => <HexGrid cells={game.cells()} catalog={game.catalog()} targets={game.targets()} state={state()}
              wallMode={wallMode() && !wallUnavailable()} orientation={orientation()} canAct={game.canAct()}
              rotate={rotate} move={id => game.action('MOVE_PAWN', {destination: id})}
              wall={id => game.action('PLACE_WALL', {wallId: id})}/>}
          </Show>
          <div class="board-footer"><span>{wallMode() && !wallUnavailable() ? 'Choose a vertex · R or scroll to rotate' : game.canAct() ? 'Choose a highlighted hex to move' : game.ready() ? 'Waiting for your turn' : 'Connect all three players to begin'}</span><span>RADIUS 5 · CUBE GRID</span></div>
        </section>
        <aside class="sidebar">
          <div class="turn-card" style={{'--accent': COLORS[turn()]}}><p class="eyebrow">{game.state()?.winner !== undefined && game.state()!.winner >= 0 ? 'WINNER' : 'UP NEXT'}</p>
            <h2><span class="player-badge" style={{background: COLORS[turn()]}}>P{turn()}</span>{NAMES[turn()]}</h2>
            <p>{game.mode() === 'local' ? 'Pass the device after your move.' : game.seat() === turn() ? 'Your turn. Make it count.' : 'You are P' + game.seat() + ' · Watch the board.'}</p>
          </div>
          <section class="players"><div class="section-label"><h3>The chase</h3><span>WALLS LEFT</span></div>
            <For each={NAMES}>{(name, p) => <div class="player-row" classList={{current: turn() === p()}} style={{'--accent': COLORS[p()]}}>
              <span class="player-dot" style={{background: COLORS[p()]}}/><div><strong>P{p()} · {name}</strong><small>Goal → P{(p()+1)%3}'s corner{game.mode() === 'online' ? (game.connected()[p()] ? ' · online' : ' · offline') : ''}</small></div>
              <div class="wall-count"><strong>{game.state()?.remaining[p()] ?? 6}</strong><span>/ 6</span></div>
            </div>}</For>
          </section>
          <section class="actions"><h3>Your move</h3><div class="mode-toggle">
            <button classList={{selected: !wallMode() || wallUnavailable()}} onClick={() => setWallMode(false)}><span>↗</span> Move pawn <kbd>M</kbd></button>
            <button disabled={wallUnavailable()} classList={{selected: wallMode() && !wallUnavailable()}} onClick={() => setWallMode(true)}><span>⌁</span> Place wall <kbd>W</kbd></button>
          </div><Show when={wallMode() && !wallUnavailable()}><div class="orientation"><span>Orientation</span><For each={[0,1,2]}>{i => <button classList={{selected: orientation() === i}} onClick={() => setOrientation(i)} aria-label={'Orientation ' + (i+1)}>{['⌜','⌝','⌞'][i]}<small>{i+1}</small></button>}</For></div></Show>
            <Show when={game.mode() === 'local'}><button class="bot-button" disabled={!game.canAct()} onClick={() => game.action('BOT_MOVE')}>Let a bot take this turn <span>→</span></button></Show>
          </section>
          <section class="room-panel"><div class="section-label"><h3>{game.mode() === 'local' ? 'Local table' : 'Online room'}</h3><span>{game.mode() === 'local' ? 'PASS & PLAY' : 'YOU: P' + game.seat()}</span></div>
            <Show when={game.mode() === 'online'}><div class="room-code"><code>{game.room()}</code><button onClick={copy}>{copied() ? 'Copied!' : 'Copy invite'}</button></div></Show>
            <form onSubmit={event => { event.preventDefault(); online(roomInput().trim()); }}><label for="room">Join friends by room code</label><div class="join-row"><input id="room" maxlength="32" placeholder="e.g. friday-night" pattern="[a-zA-Z0-9_-]{1,32}" required value={roomInput()} onInput={e => setRoomInput(e.currentTarget.value)}/><button type="submit">Join</button></div></form>
            <div class="room-buttons"><button onClick={() => online(randomRoom())}>Create online room</button><button onClick={startLocal}>New local game</button></div>
          </section>
          <Show when={game.error()}><div class="error" role="alert">{game.error()}</div></Show>
        </aside>
      </div>
      <footer><span>NO DICE. NO LUCK. JUST THE NEXT MOVE.</span><span>HexQuoridor-3P · Open source · BSD 3-Clause</span></footer>
    </main>
    <Show when={rules()}><div class="modal-overlay" onClick={e => { if (e.target === e.currentTarget) setRules(false); }}><section class="modal" role="dialog" aria-modal="true" aria-label="How to play"><button class="close-button" autofocus onClick={() => setRules(false)}>Close ×</button><p class="eyebrow">THE RULES</p><h2>Keep your path open.</h2>
      <ol><li><strong>Chase the next corner.</strong> P0 goes to P1's start, P1 to P2's, P2 to P0's. First to arrive wins.</li><li><strong>One action per turn.</strong> Move one hex in any of six directions, or place one of your six walls. No passing.</li><li><strong>Jump your rivals.</strong> A clear straight jump is mandatory when you enter an opponent's direction. If blocked by a pawn, edge, or wall, use either neighboring forward flank. Never jump two pawns.</li><li><strong>Change the route.</strong> A wall covers two connected boundaries with a 120° bend. Walls cannot share an edge or central vertex. Every player must retain a path to their goal.</li></ol>
      <p class="modal-note">Select Place wall, then hover a vertex. Press R, scroll, or use 1–3 to rotate. Red previews are illegal. Online games pause when a player disconnects; reopening the same room in the same tab restores their seat.</p>
      <button class="primary" onClick={() => setRules(false)}>Back to the board</button>
    </section></div></Show>
    <Show when={game.state() && game.state()!.winner >= 0 && !dismissed()}><div class="modal-overlay"><section class="modal winner-modal" role="dialog" aria-modal="true" aria-label="Game winner"><p class="eyebrow">THE CHASE IS OVER</p><div class="winner-token" style={{color: COLORS[game.state()!.winner]}}>⬡</div><h2>{NAMES[game.state()!.winner]} wins.</h2><p>P{game.state()!.winner} reached their target in {game.state()!.revision} total turns.</p><button class="primary" autofocus onClick={startLocal}>Play a new local game</button><button class="text-button" onClick={() => setDismissed(true)}>Review the final board</button></section></div></Show>
  </div>;
}
