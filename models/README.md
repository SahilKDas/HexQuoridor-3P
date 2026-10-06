# Trained HexQuoridor models

The CNN and NNUE checkpoints and self-play dataset are original BSD-3-Clause project artifacts. See ../LICENSE and ../docs/BOTS.md.

Run pnpm bots:train to reproduce training; inspect manifest.json for the exact seed, counts, optimizer, split policy, losses, and SHA-256 hashes. arena.json contains measured tournament results. No ELO or state-of-the-art strength is claimed.

Both checkpoints are required to run neural-bot tests and are loaded by the Java server; they are not downloaded from an external model host. selfplay.jsonl contains synthetic game states only.
