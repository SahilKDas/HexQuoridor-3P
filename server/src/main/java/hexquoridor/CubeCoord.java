// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
package hexquoridor;

public record CubeCoord(int q, int r, int s) {
    public CubeCoord {
        if (q + r + s != 0) throw new IllegalArgumentException("Cube coordinates must sum to zero");
    }
    public CubeCoord(int q, int r) { this(q, r, -q-r); }
    public CubeCoord add(CubeCoord other) { return new CubeCoord(q+other.q, r+other.r, s+other.s); }
    public boolean inBounds() { return Math.max(Math.abs(q), Math.max(Math.abs(r), Math.abs(s))) <= 5; }
}
