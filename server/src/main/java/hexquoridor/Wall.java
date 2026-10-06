// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
package hexquoridor;

import java.util.Arrays;

/** Two edges sharing an internal vertex, with a 120-degree bend. */
public record Wall(int id, int anchor, int direction, int edgeA, int edgeB, String vertex, int orientation) {
    static Wall at(int anchor,int direction,int a,int b) {
        int[] triple={anchor,a,b}; Arrays.sort(triple);
        String vertex=triple[0]+":"+triple[1]+":"+triple[2];
        int orientation=Arrays.binarySearch(triple,anchor);
        return new Wall(-1,anchor,direction,HexBoard.edge(anchor,a),HexBoard.edge(anchor,b),vertex,orientation);
    }
    public String key() { return Math.min(edgeA,edgeB)+":"+Math.max(edgeA,edgeB); }
    public boolean conflicts(Wall other) {
        return vertex.equals(other.vertex) || edgeA==other.edgeA || edgeA==other.edgeB
            || edgeB==other.edgeA || edgeB==other.edgeB;
    }
}
