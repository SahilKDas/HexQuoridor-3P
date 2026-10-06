// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
package hexquoridor;

import java.util.*;

public final class GameState {
    private final int[] pawns=new int[3], remaining={6,6,6};
    private final List<Integer> placed=new ArrayList<>();
    private final Set<Integer> blocked=new HashSet<>();
    private int turn=0, winner=-1;
    private long revision=0;
    public GameState() { for(int p=0;p<3;p++) pawns[p]=HexBoard.INSTANCE.spawn(p); }
    public int pawn(int p) { return pawns[p]; }
    public int turn() { return turn; }
    public int winner() { return winner; }
    public long revision() { return revision; }
    public int remaining(int p) { return remaining[p]; }
    public Set<Integer> blocked() { return Collections.unmodifiableSet(blocked); }
    public List<Integer> placed() { return List.copyOf(placed); }
    public boolean occupied(int cell) { for(int p:pawns) if(p==cell) return true; return false; }
    public void move(Move move) {
        if(winner>=0 || !MoveGenerator.pawnMoves(this).contains(move.destination()))
            throw new IllegalArgumentException("Illegal pawn move");
        pawns[turn]=move.destination();
        if(pawns[turn]==HexBoard.INSTANCE.target(turn)) winner=turn;
        advance();
    }
    public void place(int id) {
        if(!MoveGenerator.legalWall(this,id)) throw new IllegalArgumentException("Illegal wall placement");
        Wall wall=HexBoard.INSTANCE.walls().get(id);
        blocked.add(wall.edgeA()); blocked.add(wall.edgeB()); placed.add(id); remaining[turn]--;
        advance();
    }
    private void advance() { revision++; if(winner<0) turn=(turn+1)%3; }
    public Map<String,Object> snapshot() {
        List<Integer> moves=MoveGenerator.pawnMoves(this);
        List<Integer> walls=MoveGenerator.wallMoves(this);
        return Map.of("pawns",pawns.clone(),"remaining",remaining.clone(),"walls",placed(),
            "turn",turn,"winner",winner,"revision",revision,"legalMoves",moves,"legalWalls",walls);
    }
    // Package-private fixture setup for independent rules tests.
    void setup(int[] positions,int active) { System.arraycopy(positions,0,pawns,0,3); turn=active; }
    void block(int a,int b) { blocked.add(HexBoard.edge(a,b)); }
}
