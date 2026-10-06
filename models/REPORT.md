# Published training and benchmark results

Original synthetic training data and checkpoints: BSD-3-Clause, HexQuoridor-3P contributors.

## Run provenance

- Seed: **20261005**
- Teachers: original Racer, Guardian, Architect; iterative-deepening selective MaxN, depth 4, beam 3, root up to 6, 450-node budget.
- Teacher self-play: **180 games**, **8,350 recorded positions**, no adjudicated draws.
- Win counts by player seat: P0 66, P1 53, P2 61.
- All three evaluation perspectives are encoded per position.
- Canonical positions used: **9,000 training**, **2,673 validation**, **2,124 test**.
- Whole games split before canonical duplicate removal. Test positions do not select weights.
- AdamW, batch 32, 24 epochs attempted per architecture, validation-selected checkpoints, 25% hidden dropout.
- Raw corpus: selfplay.jsonl, SHA-256 **203123c0e0012942c2424c51e1362010385acd714c5f0ea5848cbf5683ccc5f9**.

## Neural checkpoints

Cross-entropy measures fit to the mixed teacher/outcome utility target, not playing ELO or calibrated win probability.

| Model | Parameters | Initial validation CE | Selected validation CE | Test CE | Selected epoch |
| --- | ---: | ---: | ---: | ---: | ---: |
| Hex CNN | 1,847 | 1.156585 | 1.071748 | 1.047192 | 4 |
| NNUE | 36,899 | 1.110608 | 1.100451 | 1.055594 | 1 |

Uniform-output CE is 1.098612. Both improve their seeded untrained initialization; CNN beats uniform on validation, while NNUE's validation loss remains slightly above uniform. Both beat uniform on this held-out test split. NNUE still shows overfitting, so its first-epoch checkpoint was retained. More training epochs alone are not evidence of improvement.

- CNN checkpoint SHA-256: **3d04611c6b65f20e10604d41722d09ccc35b30795c749f700c0e88fd9bc5fa6b**
- NNUE checkpoint SHA-256: **59f7f6080be5d178d517a71f17f7eb14e30377578e9e75dedb055b8cec229ec8**

The server verifies these against manifest.json before loading. The models are real trained binary weights, not initialization or a renamed heuristic.

## Independent tournament

Seed: **21261008**. Four lineups, 12 games each, cyclic seat rotation, independent wall/move opening prefixes. The neural models use exact legal actions, shared handcrafted candidate selection, and their learned evaluation heads. CNN searches to depth 3; NNUE and Guardian to depth 4. Third repetition or 150 plies is an arena-only draw.

| Lineup | Wins, in listed order | Draws |
| --- | --- | ---: |
| CNN / NNUE / Guardian | 2 / 4 / 6 | 0 |
| CNN / Greedy A / Greedy B | 5 / 3 / 4 | 0 |
| NNUE / Greedy A / Greedy B | 5 / 2 / 5 | 0 |
| Guardian / Greedy A / Greedy B | 5 / 2 / 5 | 0 |

CNN and NNUE each won **5/12** games in their respective two-greedy-opponent lineups. Guardian won the most games in the direct three-bot lineup. Different lineups use different seeded opening sequences; these are small descriptive results, not a paired statistical experiment or proof of superiority.

Observed average action times in the direct lineup were approximately **34 ms CNN**, **75 ms NNUE**, and **67 ms Guardian**, on the development machine. These are measurements, not device-independent latency guarantees.

No ELO, external state-of-the-art comparison, or grandmaster-strength claim is made.

## Verification

- 5,547 engine assertions.
- 3,349 neural/search assertions, including numerical gradients, incremental/full NNUE equivalence, checkpoints, rotated edge channels, deterministic actions, immediate wins, and defensive walls against an immediate rival win.
- HTTP/WebSocket tests: both trained models, automatic opponents, controller changes, cancellation, exact revision checks, room synchronization, and online bot restrictions.
- Browser tests: desktop/mobile UI, bot selectors, human-vs-CNN-and-NNUE, automatic turns, pause, one-turn model assistance, winner dialog, and existing multiplayer flows.
- Gradle build/test and strict TypeScript build pass.
- Repository remains about 150 MB, including dependency stores, build caches, weights, and self-play data.

See manifest.json and arena.json for exact machine-readable values, and [the bot guide](../docs/BOTS.md) for reproduction commands and architectural limits.
