# CNN, NNUE, and self-play teachers

HexQuoridor includes two **trained** neural value evaluators and three original handcrafted teacher bots. All inference and training run on the CPU in Java, with no Python, PyTorch, GPU, native ML library, or browser inference dependency.

These are experimental local bots. Advanced search techniques do **not** establish state-of-the-art strength; there is no external competitor benchmark or ELO claim. Exact results and limitations are published rather than inferred from architecture names.

## Play

Start the app with pnpm dev, or pnpm build followed by pnpm start.

- **Play against CNN + NNUE** creates a local table with P0 Human, P1 Hex CNN, and P2 NNUE.
- Each **P0/P1/P2 controller** dropdown can assign Human, CNN, NNUE, one of the teachers, or the greedy baseline.
- All three seats can be bots to watch a match.
- **Pause bots** returns all seats to Human and invalidates pending automatic actions.
- **One-turn assistant** selects an evaluator for Let a bot take this turn.
- Thinking and the last bot's elapsed time are visible in the HUD.
- Bots are available only in local mode. Online rooms continue to require three human connections.

The server schedules automatic turns on a daemon worker, computes on an isolated game copy, and checks the room's revision and cancellation ticket before committing an action. Leaving a room stops its bots. Live engine validation remains authoritative. Missing, corrupt, or untrained neural checkpoints are reported as unavailable; random weights are never substituted.

## Architecture

All inputs rotate cube coordinates and cyclically relabel players so the evaluation perspective is P0. The CNN and NNUE estimate a three-entry utility distribution. A selective MaxN search chooses an exact legal pawn move or wall, using learned evaluations at leaves. The engine handles immediate wins exactly.

### Hex CNN

- 91 valid hex cells, **19 input channels**: three pawn planes, three target planes, six blocked-edge planes, one valid-cell plane, three normalized wall-inventory planes, three side-to-move planes.
- A learned seven-position convolution uses the center and six hex neighbors.
- **19 → 6** hex convolution + ReLU, then **6 → 8** hex convolution + ReLU.
- Global mean and max pooling concatenate 16 values with nine small scalar features.
- **25 → 24 → 3** dense head, ReLU then softmax.
- Scalar features: three BFS goal distances / 20, three inventories / 6, three relative turn offsets / 3.
- **1,847 trainable parameters**, approximately 7.5 KB on disk.
- Search: depth 3, beam 3, root up to 6, 150-node limit.

This is an actual spatial hex convolution, not a dense network renamed CNN. Boundary neighbors outside the board contribute zero.

### NNUE

- **537 sparse binary features**: 273 player-square features, 240 blocked-edge features, 21 wall-inventory one-hot features, and three turn features.
- Sparse features sum into a **64-unit accumulator**, with clipped ReLU in [0,1].
- The same nine scalar features concatenate with the accumulator.
- **73 → 32 → 3** dense head, ReLU then softmax.
- **36,899 trainable parameters**, approximately 148 KB on disk.
- Search: depth 4, beam 3, root up to 6, 450-node limit.

Search starts from a root accumulator. A merge of sorted old/new active-feature indices adds weights for inserted features and subtracts weights for removed features. Unchanged feature rows are not recomputed. The BFS scalar suffix is recomputed per position; the sparse neural accumulator is efficiently updated. Pawn moves, walls, inventory, and side-to-move all participate in updates. Tests compare incremental results with full refreshes across moves and wall placements.

This is a floating-point NNUE adapted to three-player HexQuoridor; it is not a Stockfish-compatible checkpoint and does not claim Stockfish strength or integer quantization.

## Handcrafted teachers

**Racer, Guardian, and Architect** share an original search engine but use distinct race-temperature and wall-reserve evaluation preferences.

- Iterative deepening **MaxN**, where each player maximizes its own utility rather than assuming a two-player zero-sum game.
- Depth 4, selective beam 3, root up to 6, bounded 450-node search.
- Deterministic action ordering and principal-variation ordering.
- Zobrist hashing and a per-search transposition table.
- Goal-distance and turn-arrival estimates, legal jump opportunities, wall reserves, and mobility.
- Shortest-route wall candidates, urgency near rival goals, exact fair-path validation.
- Progress-preserving pawn candidates avoid an unnecessary retreat. This is selective search: some legal detours are intentionally omitted.
- A wall should improve or preserve the race margin against the earliest rival; selected setup walls may be explored when they preserve the placing player's route and enough inventory remains for a follow-up.
- Terminal wins override all learned and handcrafted values.

CNN, NNUE, and teachers share handcrafted candidate selection. Their neural versions rank/search candidates using different learned value functions. They are hybrid search bots, not unrestricted end-to-end policy networks. This candidate filtering improves practical play but can miss strategic sacrifices, long detours, and walls outside the selective set. No two-player alpha-beta correctness claim is made for MaxN.

## Training

From the repository root:

```sh
pnpm bots:train
```

Defaults: 180 complete teacher self-play games, seed 20261005, 24 epochs per network, up to 9,000 distinct training examples, and 12 games in each independent benchmark lineup.

Override settings:

```sh
pnpm bots:train --games 360 --epochs 40 --positions 18000 --seed 20261006 --benchmark 24
```

The training corpus uses all three teachers, cyclically changing their seats between games. Seeded opening prefixes contain 2–10 legal actions with a 35% chance of a randomly selected legal wall; other opening actions come from the selective candidate set. During teacher play, 7% of actions explore among top candidates.

Each state records the teacher's backed-up MaxN utility. After the actual self-play outcome is known, the target is:

```text
target[p] = 0.75 × teacherUtility[p] + 0.25 × actualOutcome[p]
```

Actual outcome is one-hot for a winner; training-only draws use a uniform three-entry distribution. All three evaluation perspectives are encoded for each state, including every relative side to move.

Whole games split by game index modulo 10: eight training groups, one validation group, one test group. Exact canonical duplicates are removed across later splits. Validation selects checkpoints; test positions do not select weights. Seeded sampling caps the size of each split conservatively.

Training uses original Java backpropagation, mini-batch AdamW (32), learning rate 0.002 decaying by 0.97 per epoch, weight decay 0.0001, and 25% hidden-layer dropout. Both models must improve validation cross-entropy relative to their seeded untrained initialization. Best-validation checkpoints are selected, rather than the final epoch.

Self-play and arena runs stop after third repetition or a fixed ply cap to prevent infinite training matches. These are **training/evaluation adjudications only**; live rules gain no pass or draw action.

Retraining replaces files in models/. Train while no neural match is running, then restart the server. Checkpoint integrity hashes are validated on load. The final manifest is written only after both networks meet the validation improvement requirement.

## Artifacts and audit

Read [the published training and benchmark report](../models/REPORT.md) for checkpoint losses, measured results, and current strength limitations.

The repository includes:

- models/cnn.hqnn and models/nnue.hqnn: real trained binary weights with format/version/shape checks.
- models/selfplay.jsonl: raw positions, wall IDs, inventory, acting teacher, search utilities, and mixed training targets.
- models/manifest.json: seeds, game counts, split counts, architecture sizes, losses, optimizer settings, SHA-256 checksums, and elapsed time.
- models/arena.json: independent tournament results, per-agent wins/draws, ply counts, and observed inference time.

Self-play positions and model weights are original project artifacts, distributed under the root BSD 3-Clause license. The corpus contains only synthetic game states.

```sh
pnpm bots:verify
pnpm bots:arena 24 20262008
pnpm test
pnpm test:browser
./gradlew test
pnpm size
```

Verification covers numerical finite-difference gradients, probability normalization, save/load round trips, corruption rejection, canonical rotation, search-copy isolation, deterministic decisions, legal actions, exact immediate wins, and NNUE incremental/full equivalence.

Network and browser suites exercise both trained models, one-turn assistance, human-vs-two-bot automatic turns, seat configuration, cancellation, and online restrictions. The independent arena uses unseen seeds, randomized legal wall/move opening prefixes, and cyclic seat rotation. The published small tournament is descriptive; it is not a statistically robust rating or evidence of state-of-the-art strength.

## Design references

- [Stockfish's NNUE architecture and accumulator notes](https://github.com/official-stockfish/nnue-pytorch/blob/master/docs/nnue.md): background on sparse accumulator evaluation.
- [Sturtevant: A Comparison of Algorithms for Multi-Player Games](https://cs.du.edu/~sturtevant/papers/comparison_algorithms.pdf): multiplayer search background.
- [Prob-Maxn: Playing N-Player Games with Opponent Models](https://cdn.aaai.org/AAAI/2006/AAAI06-166.pdf): limitations of assumptions in multiplayer search.

The project's feature encodings, teacher rules, network implementations, optimizer, training pipeline, and bot integration are original implementations; no external engine, weights, or game corpus is used.
