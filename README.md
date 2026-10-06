# HexQuoridor-3P

A deterministic three-player hexagonal Quoridor game, built with Java 21+, a small HTTP/WebSocket server, and SolidJS + TypeScript + Vite. BSD 3-Clause.

Play on one device, create a three-seat online room, or play against trained CNN and NNUE bots. The server owns all rules, turns, legal actions, inventory, and victory detection. Accounts and ratings are intentionally outside this release. See [the bot guide](docs/BOTS.md) for architectures, original handcrafted teachers, reproducible self-play training, and measured limitations.

## Run

Requirements: JDK **21 or newer** (including javac), Node **22.12+**, and pnpm **10+**, on PATH. No Maven, global Gradle, database, browser downloads, or heavyweight framework is needed. First-time dependency installation needs internet access.

From the repository root:

```sh
pnpm install --frozen-lockfile
pnpm dev
```

**pnpm dev is the single development command:** it compiles Java, downloads four small jars when needed, starts the backend, and starts Vite with WebSocket proxying. Open the Vite URL printed in your terminal (normally http://127.0.0.1:5173). Stop with Ctrl+C.

For a production build served by Java:

```sh
pnpm build
pnpm start
```

Open http://127.0.0.1:8080. The Java process uses a 128 MB heap in the lightweight launcher. Each launcher downloads Java dependencies to .tools/java and compiles with --release 21.

Alternatively, the **standard Gradle wrapper** builds the client and runs the app in one command:

```sh
./gradlew run
```

Windows PowerShell:

```powershell
.\gradlew.bat run
```

Gradle 9.6.1 is pinned. Its distribution and dependency cache live in the standard user Gradle directory, outside the repository. The lighter pnpm launcher avoids downloading a Gradle distribution altogether.

## Play

- **Local:** the app opens a fresh pass-and-play table. Give the device to the next player after each action.
- **Online:** select Create online room. Share Copy invite or the room code. The three connections get P0, P1, P2 in arrival order. All three must be connected for play.
- **Neural opponents:** Play against CNN + NNUE assigns P0 Human, P1 CNN, P2 NNUE. Assign any local seat through its controller dropdown; bots automatically take their turns. Pause bots cancels pending actions and returns every seat to Human.
- **Bot testing:** One-turn assistant selects CNN, NNUE, a handcrafted teacher, or the original greedy baseline. Let a bot take this turn plays one legal action, then gives control back. The greedy baseline still uses shortest graph distance and stable cell-index ties.
- **Walls:** choose Place wall, hover or tap a board vertex, then click to place. Press R or scroll to rotate; 1, 2, 3 select an orientation. Illegal previews turn red.
- **Keyboard:** M selects movement, W walls, R rotates, 1–3 select orientation. Legal cells and wall vertices can be activated with Tab then Enter or Space. Escape closes the rules dialog.
- **Reconnection:** a seat token lives in sessionStorage for that browser tab and room. Reloading preserves the room URL and seat; a disconnected player automatically retries with bounded backoff. A lost tab/token cannot reclaim an occupied reserved seat; start a new room.
- **Victory:** entering your target immediately ends the game and opens the winner dialog. Review the final board or start a new local match. To play online again, create another room.

Room codes contain 1–32 letters, digits, underscores, or hyphens. Online rooms reserve seats for reconnects and pause when anyone disconnects. No turn is skipped.

## Network configuration

Defaults: HTTP 127.0.0.1:8080 and WebSocket 127.0.0.1:8081. Online rooms work across browsers on the same host. For other devices on your LAN, set HOST=0.0.0.0 and open the host's LAN address. Invite links use the current browser origin.

```powershell
$env:HOST = '0.0.0.0'
pnpm start
```

PORT and WS_PORT configure production ports. When changing the WebSocket address or serving HTTPS, set VITE_WS_URL at build time, for example wss://your-host.example/ws, and route /ws to the WebSocket server through your TLS reverse proxy. The default production client uses its hostname on WebSocket port 8081; the Vite development proxy uses port 8081. This release has in-memory, invite-code rooms and no account authentication; use a trusted host/network.

Rooms are capped at 256. Empty rooms expire after 30 minutes and are removed lazily on the next join. Restarting the server clears all games and seats. Room mutations are serialized, preventing simultaneous actions from racing.

## Mathematical model

### Board and symmetry

Cells are cube coordinates (q,r,s) with q+r+s=0 and max(|q|,|r|,|s|)<=5. There are 3R(R+1)+1 = **91** cells. Enumerating q then r in ascending order creates a stable dense index 0–90.

The six cube directions, clockwise in the pointy-topped SVG, are:

```text
(1,0,-1), (0,1,-1), (-1,1,0), (-1,0,1), (0,-1,1), (1,-1,0)
```

| Player | Spawn | Target |
| --- | --- | --- |
| P0 Amber | (0,5,-5) | (5,-5,0) |
| P1 Tide | (5,-5,0) | (-5,0,5) |
| P2 Iris | (-5,0,5) | (0,5,-5) |

The rotation (q,r,s)->(r,s,q) maps each player to the next player and preserves the rules. Pawn location and walls completely determine all legal actions. No randomness occurs in the game engine.

### Movement

Each action is either a pawn move or one wall. Passing is rejected. An adjacent empty hex is reachable only over an open shared edge.

For an adjacent opponent in direction d, the straight destination is that opponent's neighbor in direction d. If it is in bounds, empty, and the edge is open, only the straight jump is available through that opponent. Otherwise, either forward flanking neighbor in direction d-1 or d+1 modulo 6 is allowed if empty, in bounds, and connected by an open edge. The initial edge to the opponent must also be open. A second pawn is an obstruction, never another jump in a chain.

### Agreed wall geometry

**The original request for two contiguous collinear regular-hex boundary edges is geometrically impossible:** three boundaries meet at 120 degrees at each hex vertex. The user selected the implementation with **two connected boundary edges and a 120-degree bend**.

At each internal vertex, three edges meet. A wall selects two edges; there are **three orientations per vertex**, **150 internal vertices**, and **450 unique candidate walls**. This catalog is generated once. Each candidate has a stable ID, two undirected edge IDs, central vertex, and orientation. Undirected edges encode min(a,b)*91+max(a,b). Each player starts with six walls.

A candidate is rejected if it overlaps any placed edge or uses an existing wall's central vertex. All wall geometry follows actual cell boundaries, so distinct edges cannot geometrically cross in their interiors. Endpoint touching that neither overlaps an edge nor shares central vertices is allowed.

### Path invariant and complexity

Every candidate temporarily blocks its two edges and runs BFS from each pawn to that pawn's target. Pawns are ignored in path validation, as they can move later. BFS has a fixed 91-entry queue, a visited/distance array, six precomputed neighbors per cell, and early exit on reaching the target.

Each path check is **O(V+E)**, at most 91 vertices and 240 undirected edges; all three paths must exist. Rejected walls leave the state and inventory untouched. Successful actions increment a monotonic revision and advance the cyclic turn, except that a winner ends the game instantly. Generating all legal wall previews checks the bounded 450-candidate catalog.

## Structure

```text
server/src/main/java/hexquoridor/
  CubeCoord.java            Cube coordinate validation
  HexBoard.java             Dense cells, neighbors, spawns, wall catalog
  GameState.java            Deterministic state, inventory, actions, victory
  Move.java / Wall.java     Action and wall geometry records
  MoveGenerator.java        Steps, straight jumps, deflections, wall legality
  PathFinder.java           Early-exit BFS for all three targets
  GameSessionManager.java   Rooms, seats, reconnect tokens, turn/revision checks
  Server.java               JDK HttpServer + Java-WebSocket
  BotSearch.java            Bounded multiplayer MaxN and handcrafted teachers
  CnnNetwork.java           Trainable spatial hex CNN
  NnueNetwork.java          Sparse efficiently updated neural evaluator
  BotTraining.java          Seeded self-play and grouped validation/test splits
  BotArena.java             Independent balanced-seat tournament
  BotService.java           Verified checkpoint loading and bot registry
server/src/test/java/hexquoridor/EngineTest.java
client/src/
  HexGrid.tsx               Responsive SVG, pawns, goals, walls, previews
  useGameEngine.ts          WebSocket lifecycle and Solid signals
  App.tsx                   HUD, rooms, action controls, rules, victory dialog
scripts/                    Lightweight launchers and verification
models/                     Trained weights, synthetic self-play, measured reports
docs/BOTS.md                Bot design, training, audit, and limitations
```

## JSON WebSocket protocol

Endpoint: /ws on port 8081 (or through the development proxy).

Client first joins:

```json
{"type":"JOIN","room":"friday-night","mode":"online","token":""}
```

WELCOME returns the seat, reconnect token, 91 coordinates, target indices, and the wall catalog. STATE follows, including ready, connected, and:

```json
{"pawns":[0,1,2],"remaining":[6,6,6],"walls":[],"turn":0,"winner":-1,"revision":0,"legalMoves":[],"legalWalls":[]}
```

The above state is a schema example, not the initial game coordinates. Subsequent actions use the current revision:

```json
{"type":"MOVE_PAWN","destination":42,"revision":0}
{"type":"PLACE_WALL","wallId":17,"revision":1}
{"type":"BOT_MOVE","revision":2}
{"type":"BOT_MOVE","bot":"cnn","revision":2}
{"type":"SET_BOTS","controllers":["human","cnn","nnue"],"revision":2}
```

BOT_MOVE and SET_BOTS are local-only. BOT_MOVE defaults to the greedy baseline; optional bot IDs are cnn, nnue, racer, guardian, architect, greedy. Local JOIN may include a three-entry controllers array. WELCOME includes available bots; state broadcasts include controllers, thinking, botError, and lastBot. Configuring controllers does not consume a game turn. A human action cannot override an automatic bot's turn; returning its seat to Human cancels pending work. Local JOIN uses mode local and one connection controls the table. The server derives player identity from membership, never from a client-supplied player number. Stale revisions, illegal actions, wrong turns, invalid payloads, and actions before all online seats connect return ERROR without mutation. Winning actions broadcast GAME_OVER with the final state. There is no pass action.

## Verification

```sh
pnpm test          # Java engine assertions + HTTP/WebSocket integration
pnpm bots:verify   # Neural gradients, checkpoints, legal decisions, NNUE updates
pnpm bots:train    # Handcrafted teacher self-play, CNN/NNUE training, tournament
pnpm bots:arena 24 # Independent tournament, 24 games per lineup
pnpm check         # Strict TypeScript checking
pnpm build         # Production bundle + repository footprint guard
pnpm test:browser  # Installed Chromium/Edge/Chrome, no browser downloads
pnpm size          # Recursively count every local repository file
./gradlew test     # Same engine suite through Gradle
```

For browser testing, set BROWSER_PATH if your Chromium browser is not in a standard location. The browser suite starts a local server if needed, expects an existing production build, and checks desktop/mobile layout, real pawn/wall actions, rotation, wall counters, rules dialog, three simultaneous online players, seat recovery after reload, and disconnect pause. Screenshots are saved under .tools/screenshots.

The engine suite covers 91-cell topology, reciprocal neighbors, all 450 walls, straight and deflected jumps, pawn/wall/edge obstructions, no chained jumps, overlap/intersection rejection, blocking any player's route, six-wall inventories, instant wins, terminal rejection, deterministic randomized legal play, and rotational symmetry. Network tests cover malformed and stale actions, wrong turns, identical broadcasts, room capacity, reconnects, and a full bot game.

### Footprint checklist: strictly under 1 GB

- [x] Minimal pnpm workspace with a committed lockfile.
- [x] pnpm store lives inside .tools/pnpm-store, so its size is included.
- [x] No database, Spring distribution, heavy UI framework, or browser binaries.
- [x] CPU-only Java CNN/NNUE training and inference; no heavyweight ML runtime.
- [x] Lightweight Java launcher uses only four jars.
- [x] Production client bundle is approximately 42 KB before gzip.
- [x] Size script includes .git, node_modules, .tools, .gradle, build and dist.
- [x] Hard-linked files are counted conservatively per path; symbolic link targets are not traversed.
- [x] Installation and production build run the size guard automatically.
- [x] Guard fails at **1,000,000,000 bytes**, not at 1 GiB.

The development repository, including the browser-testing package, dependency store, Gradle project cache, compiled artifacts, screenshots, trained weights, and self-play corpus, is approximately **150 MB**, leaving more than 840 MB of headroom. Run pnpm size for the current exact total. Do not download full Gradle distributions, browser binaries, heavyweight ML runtimes, or unrelated caches into this repository.

## License

Original source files contain SPDX BSD-3-Clause headers. See LICENSE for the full BSD 3-Clause terms and THIRD_PARTY_NOTICES.md for dependency licenses. Generated Gradle wrapper files retain their original upstream Apache license headers.
