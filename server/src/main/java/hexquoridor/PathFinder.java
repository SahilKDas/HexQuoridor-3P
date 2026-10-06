// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
package hexquoridor;

import java.util.Set;

/** O(V+E), fixed-size queue, no recursion. Pawns do not block path validation. */
public final class PathFinder {
    public static int[] distances(int start,Set<Integer> blocked) {
        int[] queue=new int[91],distance=new int[91];java.util.Arrays.fill(distance,-1);
        int head=0,tail=0;queue[tail++]=start;distance[start]=0;HexBoard board=HexBoard.INSTANCE;
        while(head<tail) {
            int cell=queue[head++];
            for(int direction=0;direction<6;direction++) {
                int next=board.neighbor(cell,direction);
                if(board.open(cell,next,blocked) && distance[next]<0) {distance[next]=distance[cell]+1;queue[tail++]=next;}
            }
        }
        return distance;
    }
    public static boolean exists(int start,int target,Set<Integer> blocked) {
        return distance(start,target,blocked)>=0;
    }
    public static int distance(int start,int target,Set<Integer> blocked) {
        if(start<0 || target<0) return -1;
        int[] queue=new int[HexBoard.SIZE], distances=new int[HexBoard.SIZE];
        java.util.Arrays.fill(distances,-1);
        int head=0,tail=0; queue[tail++]=start; distances[start]=0;
        HexBoard board=HexBoard.INSTANCE;
        while(head<tail) {
            int cell=queue[head++];
            if(cell==target) return distances[cell];
            for(int d=0;d<6;d++) {
                int next=board.neighbor(cell,d);
                if(board.open(cell,next,blocked) && distances[next]<0) {
                    distances[next]=distances[cell]+1; queue[tail++]=next;
                }
            }
        }
        return -1;
    }
    public static boolean all(GameState game,Set<Integer> blocked) {
        for(int p=0;p<3;p++) if(!exists(game.pawn(p),HexBoard.INSTANCE.target(p),blocked)) return false;
        return true;
    }
}
