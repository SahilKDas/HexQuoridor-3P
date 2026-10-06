// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
package hexquoridor;

/** An engine action. Search copies never bypass live action validation. */
public record BotAction(boolean wall, int id) {
    public void apply(GameState state) { if(wall) state.place(id); else state.move(new Move(id)); }
    public int key() { return wall ? 91+id : id; }
}
