// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
package hexquoridor;

import java.util.*;

public final class MoveGenerator {
    public static List<Integer> pawnMoves(GameState game) {
        if(game.winner()>=0) return List.of();
        HexBoard board=HexBoard.INSTANCE; Set<Integer> result=new TreeSet<>();
        int from=game.pawn(game.turn());
        for(int d=0;d<6;d++) {
            int adjacent=board.neighbor(from,d);
            if(!board.open(from,adjacent,game.blocked())) continue;
            if(!game.occupied(adjacent)) { result.add(adjacent); continue; }
            int behind=board.neighbor(adjacent,d);
            if(board.open(adjacent,behind,game.blocked()) && !game.occupied(behind)) {
                result.add(behind);
            } else for(int flank:new int[]{(d+5)%6,(d+1)%6}) {
                int destination=board.neighbor(adjacent,flank);
                if(board.open(adjacent,destination,game.blocked()) && !game.occupied(destination))
                    result.add(destination);
            }
        }
        return List.copyOf(result);
    }
    public static boolean legalWall(GameState game,int id) {
        HexBoard board=HexBoard.INSTANCE;
        if(game.winner()>=0 || game.remaining(game.turn())<=0 || id<0 || id>=board.walls().size()) return false;
        Wall candidate=board.walls().get(id);
        for(int placed:game.placed()) if(candidate.conflicts(board.walls().get(placed))) return false;
        Set<Integer> blocked=new HashSet<>(game.blocked());
        if(blocked.contains(candidate.edgeA()) || blocked.contains(candidate.edgeB())) return false;
        blocked.add(candidate.edgeA()); blocked.add(candidate.edgeB());
        return PathFinder.all(game,blocked);
    }
    public static List<Integer> wallMoves(GameState game) {
        List<Integer> result=new ArrayList<>();
        for(Wall wall:HexBoard.INSTANCE.walls()) if(legalWall(game,wall.id())) result.add(wall.id());
        return List.copyOf(result);
    }
}
