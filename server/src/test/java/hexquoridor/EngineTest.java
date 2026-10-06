// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
package hexquoridor;

import java.util.*;

public final class EngineTest {
    private static int checks;
    private static final HexBoard B=HexBoard.INSTANCE;
    private static int c(int q,int r) { return B.index(new CubeCoord(q,r)); }
    private static void check(boolean ok,String label) { checks++; if(!ok) throw new AssertionError(label); }
    private static void reject(Runnable action,String label) {
        boolean rejected=false; try { action.run(); } catch(IllegalArgumentException e) { rejected=true; }
        check(rejected,label);
    }
    private static GameState fixture(int q,int r,int oq,int or,int tq,int tr) {
        GameState g=new GameState(); g.setup(new int[]{c(q,r),c(oq,or),c(tq,tr)},0); return g;
    }
    public static void main(String[] args) {
        check(B.cells().size()==91,"91 cells");
        check(new HashSet<>(B.cells()).size()==91,"dense unique mapping");
        reject(()->new CubeCoord(1,1,1),"cube sum");
        for(int i=0;i<91;i++) {
            check(B.index(B.cell(i))==i,"index inverse");
            for(int d=0;d<6;d++) {
                int n=B.neighbor(i,d);
                if(n>=0) check(B.neighbor(n,(d+3)%6)==i,"symmetric neighbor");
            }
        }
        for(int p=0;p<3;p++) {
            check(B.target(p)==B.spawn((p+1)%3),"counterclockwise targets");
            check(PathFinder.distance(B.spawn(p),B.target(p),Set.of())==10,"spawn distance");
        }
        GameState initial=new GameState();
        check(MoveGenerator.pawnMoves(initial).size()==3,"corner has 3 base moves");
        reject(()->initial.move(new Move(initial.pawn(0))),"no pass");
        check(initial.revision()==0 && initial.turn()==0,"rejection atomic");
        GameState straight=fixture(0,0,1,0,-4,0);
        List<Integer> moves=MoveGenerator.pawnMoves(straight);
        check(moves.contains(c(2,0)),"straight jump");
        check(!moves.contains(c(1,0)),"occupied landing excluded");
        check(!moves.contains(c(1,1)) && !moves.contains(c(2,-1)),"flank forbidden while straight open");
        straight.block(c(1,0),c(2,0));
        moves=MoveGenerator.pawnMoves(straight);
        check(!moves.contains(c(2,0)),"jump blocked by wall");
        check(moves.contains(c(1,1)) && moves.contains(c(2,-1)),"wall permits both flanks");
        straight.block(c(1,0),c(1,1));
        check(!MoveGenerator.pawnMoves(straight).contains(c(1,1)),"flanking wall respected");
        GameState two=fixture(0,0,1,0,2,0);
        moves=MoveGenerator.pawnMoves(two);
        check(!moves.contains(c(2,0)) && !moves.contains(c(3,0)),"no double jump");
        check(moves.contains(c(1,1)) && moves.contains(c(2,-1)),"pawn obstruction permits flanks");
        GameState edge=fixture(4,-1,5,-1,-4,0);
        check(MoveGenerator.pawnMoves(edge).contains(c(5,0)),"edge permits inbounds flank");
        GameState approach=fixture(0,0,1,0,-4,0);
        approach.block(c(0,0),c(1,0));
        check(!MoveGenerator.pawnMoves(approach).contains(c(2,0)),"cannot jump over approach wall");

        check(B.walls().size()==450,"150 internal vertices times three orientations");
        Set<String> unique=new HashSet<>();
        Map<String,Set<Integer>> orientations=new HashMap<>();
        for(Wall w:B.walls()) {
            check(unique.add(w.key()),"unique wall geometry");
            check(w.edgeA()!=w.edgeB(),"two distinct edges");
            orientations.computeIfAbsent(w.vertex(),key->new HashSet<>()).add(w.orientation());
            check(MoveGenerator.legalWall(initial,w.id()),"all initial walls preserve paths");
        }
        for(Set<Integer> variants:orientations.values()) check(variants.equals(Set.of(0,1,2)),"three vertex orientations");
        Wall first=B.walls().get(0); initial.place(first.id());
        check(initial.remaining(0)==5 && initial.turn()==1 && initial.revision()==1,"wall consumes turn and inventory");
        for(Wall w:B.walls()) if(w.conflicts(first)) check(!MoveGenerator.legalWall(initial,w.id()),"overlap/cross rejected");
        reject(()->initial.place(first.id()),"duplicate wall rejected");
        reject(()->initial.place(-1),"invalid wall id");
        check(initial.revision()==1,"illegal walls atomic");

        for(int player=0;player<3;player++) {
            GameState g=new GameState();
            int spawn=g.pawn(player);
            Wall sealing=B.walls().stream().filter(w->w.anchor()==spawn).findFirst().orElseThrow();
            for(int d=0;d<6;d++) {
                int n=B.neighbor(spawn,d);
                if(n>=0) {
                    int edgeId=HexBoard.edge(spawn,n);
                    if(edgeId!=sealing.edgeA() && edgeId!=sealing.edgeB()) g.block(spawn,n);
                }
            }
            check(PathFinder.all(g,g.blocked()),"path exists before final seal");
            check(!MoveGenerator.legalWall(g,sealing.id()),"cannot seal any player's path");
        }
        GameState inventory=new GameState();
        for(int round=0;round<6;round++) for(int p=0;p<3;p++) {
            List<Integer> walls=MoveGenerator.wallMoves(inventory);
            check(!walls.isEmpty(),"wall available before exhaustion");
            inventory.place(walls.get(0));
            check(PathFinder.all(inventory,inventory.blocked()),"all paths after placement");
        }
        check(inventory.placed().size()==18,"18 maximum walls");
        check(MoveGenerator.wallMoves(inventory).isEmpty(),"exhausted inventory cannot place");
        reject(()->inventory.place(0),"inventory enforced");
        inventory.move(new Move(MoveGenerator.pawnMoves(inventory).get(0)));
        check(inventory.revision()==19,"must move after exhaustion");

        GameState victory=new GameState();
        int target=B.target(0);
        victory.setup(new int[]{B.neighbor(target,2),c(-3,0),c(0,-3)},0);
        victory.move(new Move(target));
        check(victory.winner()==0 && victory.turn()==0 && victory.revision()==1,"instant win");
        check(MoveGenerator.pawnMoves(victory).isEmpty() && MoveGenerator.wallMoves(victory).isEmpty(),"terminal state");
        reject(()->victory.move(new Move(c(0,0))),"post-win moves rejected");

        Random rng=new Random(777);
        for(int run=0;run<12;run++) {
            GameState g=new GameState();
            for(int turn=0;turn<100 && g.winner()<0;turn++) {
                List<Integer> walls=MoveGenerator.wallMoves(g);
                if(!walls.isEmpty() && rng.nextInt(4)==0) g.place(walls.get(rng.nextInt(walls.size())));
                else {
                    List<Integer> legal=MoveGenerator.pawnMoves(g);
                    check(!legal.isEmpty(),"no stranded turn");
                    g.move(new Move(legal.get(rng.nextInt(legal.size()))));
                }
                check(PathFinder.all(g,g.blocked()),"random play fair paths");
                check(g.pawn(0)!=g.pawn(1) && g.pawn(1)!=g.pawn(2) && g.pawn(0)!=g.pawn(2),"random distinct pawns");
            }
        }
        // Rotation (q,r,s)->(r,s,q) maps the cyclic chase and legal moves.
        GameState rotation=fixture(0,0,1,0,2,0);
        int[] rotated=new int[3];
        for(int p=0;p<3;p++) { CubeCoord a=B.cell(rotation.pawn(p)); rotated[(p+1)%3]=B.index(new CubeCoord(a.r(),a.s(),a.q())); }
        GameState other=new GameState(); other.setup(rotated,1);
        Set<Integer> expected=new HashSet<>();
        for(int dest:MoveGenerator.pawnMoves(rotation)) { CubeCoord a=B.cell(dest); expected.add(B.index(new CubeCoord(a.r(),a.s(),a.q()))); }
        check(expected.equals(new HashSet<>(MoveGenerator.pawnMoves(other))),"threefold movement symmetry");
        System.out.println("PASS: "+checks+" engine assertions");
    }
}
