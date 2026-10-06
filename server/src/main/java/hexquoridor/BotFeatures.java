// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
package hexquoridor;

import java.util.*;

/** Rotate the board and relabel players so the evaluation perspective is always P0. */
public final class BotFeatures {
    public static final int CHANNELS=19, SPARSE=537, SCALARS=9;
    private static final HexBoard B=HexBoard.INSTANCE;
    static final int[][] ROTATION=new int[3][91];
    private static final Map<Integer,Integer> EDGE_INDEX=new HashMap<>();
    static {
        for(int rotation=0;rotation<3;rotation++) for(int i=0;i<91;i++) {
            CubeCoord cell=B.cell(i);
            for(int k=0;k<rotation;k++) cell=new CubeCoord(cell.r(),cell.s(),cell.q());
            ROTATION[rotation][i]=B.index(cell);
        }
        TreeSet<Integer> edges=new TreeSet<>();
        for(int i=0;i<91;i++) for(int d=0;d<6;d++){int n=B.neighbor(i,d);if(n>=0)edges.add(HexBoard.edge(i,n));}
        int i=0;for(int edge:edges) EDGE_INDEX.put(edge,i++);
        if(i!=240) throw new IllegalStateException("Unexpected topology");
    }
    public record Encoded(int[] active,float[] grid,float[] scalars,int perspective) {}
    public static Encoded encode(GameState state,int perspective) {
        boolean[] active=new boolean[SPARSE];float[] grid=new float[91*CHANNELS],scalar=new float[SCALARS];
        int rotation=(3-perspective)%3,relativeTurn=(state.turn()-perspective+3)%3;
        for(int p=0;p<3;p++) {
            int original=(perspective+p)%3,cell=ROTATION[rotation][state.pawn(original)];
            active[p*91+cell]=true;grid[cell*CHANNELS+p]=1;
            grid[B.target(p)*CHANNELS+3+p]=1;
            active[513+p*7+state.remaining(original)]=true;
            scalar[p]=PathFinder.distance(state.pawn(original),B.target(original),state.blocked())/20f;
            scalar[3+p]=state.remaining(original)/6f;
            scalar[6+p]=(p-relativeTurn+3)%3/3f;
        }
        active[534+relativeTurn]=true;
        for(int i=0;i<91;i++) {
            int canonical=ROTATION[rotation][i],offset=canonical*CHANNELS;
            grid[offset+12]=1;
            for(int p=0;p<3;p++)grid[offset+13+p]=state.remaining((perspective+p)%3)/6f;
            grid[offset+16+relativeTurn]=1;
            for(int d=0;d<6;d++){
                int next=B.neighbor(i,d);
                if(next>=0 && state.blocked().contains(HexBoard.edge(i,next)))
                    grid[offset+6+(d+4*rotation)%6]=1;
            }
        }
        for(int edge:state.blocked()) {
            int a=ROTATION[rotation][edge/91],b=ROTATION[rotation][edge%91];
            active[273+EDGE_INDEX.get(HexBoard.edge(a,b))]=true;
        }
        int count=0;for(boolean present:active) if(present)count++;
        int[] features=new int[count];count=0;for(int i=0;i<SPARSE;i++)if(active[i])features[count++]=i;
        return new Encoded(features,grid,scalar,perspective);
    }
    public static float[] relative(float[] absolute,int perspective) {
        float[] result=new float[3];for(int p=0;p<3;p++)result[p]=absolute[(perspective+p)%3];return result;
    }
    public static float[] absolute(float[] relative,int perspective) {
        float[] result=new float[3];for(int p=0;p<3;p++)result[(perspective+p)%3]=relative[p];return result;
    }
}
