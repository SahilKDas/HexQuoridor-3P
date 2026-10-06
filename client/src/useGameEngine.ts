// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
import { createSignal, onCleanup } from 'solid-js';
import type { Cell, Wall, GameState } from './types';

export function useGameEngine() {
  const [cells, setCells] = createSignal<Cell[]>([]);
  const [catalog, setCatalog] = createSignal<Wall[]>([]);
  const [targets, setTargets] = createSignal<number[]>([]);
  const [state, setState] = createSignal<GameState>();
  const [status, setStatus] = createSignal('Choose your game');
  const [error, setError] = createSignal('');
  const [ready, setReady] = createSignal(false);
  const [seat, setSeat] = createSignal(-1);
  const [mode, setMode] = createSignal<'local' | 'online'>('local');
  const [room, setRoom] = createSignal('');
  const [connected, setConnected] = createSignal<boolean[]>([]);
  const [joined, setJoined] = createSignal(false);
  const [controllers, setControllers] = createSignal<string[]>(['human','human','human']);
  const [botCatalog, setBotCatalog] = createSignal<{id: string; name: string; available: boolean}[]>([]);
  const [thinking, setThinking] = createSignal(false);
  const [lastBot, setLastBot] = createSignal<{id?: string; millis?: number}>({});
  let socket: WebSocket | undefined;
  let retry: ReturnType<typeof setTimeout> | undefined;
  let disposed = false, generation = 0, retryCount = 0;
  function open(id: string, gameMode: 'local' | 'online', epoch: number, initialControllers?: string[]) {
    const url = import.meta.env.VITE_WS_URL || (location.port === '8080'
      ? (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.hostname + ':8081/ws'
      : (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws');
    const ws = new WebSocket(url); socket = ws;
    const key = 'hexquoridor-seat:' + id;
    ws.onopen = () => {
      if (epoch !== generation) return;
      setStatus('Joining room…');
      ws.send(JSON.stringify({type: 'JOIN', room: id, mode: gameMode, token: sessionStorage.getItem(key) || '', ...(initialControllers ? {controllers: initialControllers} : {})}));
    };
    ws.onmessage = (event) => {
      if (epoch !== generation) return;
      try {
        const data = JSON.parse(event.data);
        if (data.type === 'WELCOME') {
          setCells(data.cells); setCatalog(data.wallCatalog); setTargets(data.targets);
          setBotCatalog(data.bots || []);
          setSeat(data.seat); setJoined(true); retryCount = 0;
          if (data.token) sessionStorage.setItem(key, data.token);
        } else if (data.type === 'STATE' || data.type === 'GAME_OVER') {
          setState(data.state); setReady(data.ready); setConnected(data.connected);
          setControllers(data.controllers || ['human','human','human']);setThinking(!!data.thinking);setLastBot(data.lastBot || {});
          if(data.botError)setError(data.botError);
          setStatus(data.state.winner >= 0 ? 'Game complete' : data.thinking ? 'Bot thinking…' : data.ready ? 'Connected' : 'Waiting for players');
        } else if (data.type === 'ERROR') {
          setError(data.message);
          if (!joined()) { ws.onclose = null; ws.close(); setStatus('Unable to join'); }
        }
      } catch { setError('Invalid server response'); }
    };
    ws.onerror = () => setStatus('Connection unavailable');
    ws.onclose = () => {
      if (disposed || epoch !== generation) return;
      setReady(false); setStatus('Disconnected · reconnecting…');
      retry = setTimeout(() => open(id, gameMode, epoch), Math.min(1000 * 2 ** retryCount++, 10000));
    };
  }
  function join(id: string, gameMode: 'local' | 'online', initialControllers?: string[]) {
    generation++; clearTimeout(retry);
    if (socket) { socket.onclose = null; socket.close(); }
    setState(undefined); setCells([]); setJoined(false); setReady(false); setError('');
    setRoom(id); setMode(gameMode); setStatus('Connecting…'); retryCount = 0;
    setControllers(['human','human','human']);setThinking(false);setLastBot({});
    open(id, gameMode, generation, initialControllers);
  }
  const canAct = () => joined() && ready() && state()?.winner === -1 && (mode() === 'local' ? controllers()[state()!.turn] === 'human' : seat() === state()?.turn);
  function action(type: string, fields: Record<string, number | string | string[]> = {}) {
    if ((type === 'SET_BOTS' ? !joined() || mode() !== 'local' : !canAct()) || socket?.readyState !== WebSocket.OPEN) return;
    setError(''); socket.send(JSON.stringify({type, revision: state()!.revision, ...fields}));
  }
  function leave() {
    generation++; clearTimeout(retry);
    if (socket) { socket.onclose = null; socket.close(); }
    setJoined(false); setReady(false); setState(undefined); setStatus('Choose your game'); setError('');
  }
  onCleanup(() => { disposed = true; leave(); });
  return { cells, catalog, targets, state, status, error, ready, seat, mode, room, connected, joined, controllers, botCatalog, thinking, lastBot, canAct, join, leave, action };
}
