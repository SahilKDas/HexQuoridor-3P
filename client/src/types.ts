// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
export interface Cell { q: number; r: number; s: number }
export interface Wall { id: number; anchor: number; direction: number; edgeA: number; edgeB: number; vertex: string; orientation: number }
export interface GameState { pawns: number[]; remaining: number[]; walls: number[]; turn: number; winner: number; revision: number; legalMoves: number[]; legalWalls: number[] }
