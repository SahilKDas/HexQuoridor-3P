// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
package hexquoridor;

import java.util.*;

public final class HexBoard {
    public static final int RADIUS = 5, SIZE = 91;
    // Clockwise in pointy-top SVG coordinates.
    public static final CubeCoord[] DIRECTIONS = {
        new CubeCoord(1,0), new CubeCoord(0,1), new CubeCoord(-1,1),
        new CubeCoord(-1,0), new CubeCoord(0,-1), new CubeCoord(1,-1)
    };
    public static final HexBoard INSTANCE = new HexBoard();
    private final CubeCoord[] cells = new CubeCoord[SIZE];
    private final int[][] neighbors = new int[SIZE][6];
    private final Map<CubeCoord,Integer> indices = new HashMap<>();
    private final List<Wall> walls;
    private final int[] spawns;
    private HexBoard() {
        int i=0;
        for (int q=-5;q<=5;q++) for (int r=-5;r<=5;r++) {
            CubeCoord c=new CubeCoord(q,r);
            if (c.inBounds()) { cells[i]=c; indices.put(c,i++); }
        }
        for (i=0;i<SIZE;i++) for(int d=0;d<6;d++)
            neighbors[i][d]=index(cells[i].add(DIRECTIONS[d]));
        spawns=new int[]{index(new CubeCoord(0,5)),index(new CubeCoord(5,-5)),index(new CubeCoord(-5,0))};
        // At each internal vertex three edges meet. Choose each possible pair once.
        Map<String,Wall> unique = new TreeMap<>();
        for(i=0;i<SIZE;i++) for(int d=0;d<6;d++) {
            int a=neighbor(i,d), b=neighbor(i,(d+1)%6);
            if(a<0 || b<0) continue;
            Wall wall=Wall.at(i,d,a,b);
            unique.put(wall.key(),wall);
        }
        List<Wall> list=new ArrayList<>(); i=0;
        for(Wall w:unique.values()) list.add(new Wall(i++,w.anchor(),w.direction(),w.edgeA(),w.edgeB(),w.vertex(),w.orientation()));
        walls=List.copyOf(list);
    }
    public CubeCoord cell(int i) { return cells[i]; }
    public List<CubeCoord> cells() { return List.of(cells); }
    public int index(CubeCoord c) { return indices.getOrDefault(c,-1); }
    public int neighbor(int i,int d) { return neighbors[i][d]; }
    public int spawn(int p) { return spawns[p]; }
    public int target(int p) { return spawns[(p+1)%3]; }
    public List<Wall> walls() { return walls; }
    public static int edge(int a,int b) { return Math.min(a,b)*SIZE+Math.max(a,b); }
    public boolean open(int a,int b,Set<Integer> blocked) { return b>=0 && !blocked.contains(edge(a,b)); }
}
