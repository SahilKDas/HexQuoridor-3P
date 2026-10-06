// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
package hexquoridor;

import java.util.*;

/** Bounded iterative-deepening MaxN: each of the three players maximizes its own utility. */
public final class BotSearch {
    public interface Evaluator { float[] evaluate(GameState state); }
    public record Choice(BotAction action,float[] value,int nodes,int depth) {}
    public enum Style { RACER, GUARDIAN, ARCHITECT }
    private static final HexBoard B=HexBoard.INSTANCE;
    private final Evaluator evaluator;
    private final int maxDepth, beam, budget;
    private final Map<Long,Entry> cache=new HashMap<>();
    private int nodes, completed;
    private record Entry(int depth,float[] value) {}
    private record Ranked(BotAction action,float score) {}
    private static final long[][] PAWN_KEYS=new long[3][91], WALL_KEYS=new long[1][450], INVENTORY_KEYS=new long[3][7];
    private static final long[] TURN_KEYS=new long[3];
    static {
        Random random=new Random(0x48455833L);
        for(long[] row:PAWN_KEYS) for(int i=0;i<row.length;i++) row[i]=random.nextLong();
        for(long[] row:WALL_KEYS) for(int i=0;i<row.length;i++) row[i]=random.nextLong();
        for(long[] row:INVENTORY_KEYS) for(int i=0;i<row.length;i++) row[i]=random.nextLong();
        for(int i=0;i<3;i++) TURN_KEYS[i]=random.nextLong();
    }
    public BotSearch(Evaluator evaluator,int depth,int beam,int budget) {
        this.evaluator=evaluator;this.maxDepth=depth;this.beam=beam;this.budget=budget;
    }
    public static float[] heuristic(GameState state,Style style) {
        if(state.winner()>=0) {float[] result=new float[3];result[state.winner()]=1;return result;}
        float[] logits=new float[3];
        float temperature=switch(style){case RACER->.27f;case GUARDIAN->.32f;case ARCHITECT->.23f;};
        float reserve=switch(style){case RACER->.045f;case GUARDIAN->.09f;case ARCHITECT->.025f;};
        for(int p=0;p<3;p++) {
            int[] distance=PathFinder.distances(B.target(p),state.blocked());
            int steps=distance[state.pawn(p)];
            GameState view=state.viewTurn(p);
            List<Integer> moves=MoveGenerator.pawnMoves(view);
            for(int destination:moves) steps=Math.min(steps,1+distance[destination]);
            float arrival=steps==0?0:3*(steps-1)+(p-state.turn()+3)%3+1;
            logits[p]=-temperature*arrival+reserve*state.remaining(p)*Math.min(steps/4f,1f)
                +(moves.isEmpty()?-.8f:.02f*moves.size());
        }
        return NeuralMath.softmax(logits);
    }
    public Choice choose(GameState state) {
        if(state.winner()>=0) throw new IllegalArgumentException("Game already over");
        nodes=0;completed=0;cache.clear();
        List<BotAction> roots=candidates(state,Math.max(beam,6));
        if(roots.isEmpty()) throw new IllegalArgumentException("No legal action");
        int active=state.turn();
        BotAction best=roots.get(0);float[] value=evaluator.evaluate(state);
        for(BotAction action:roots) if(!action.wall() && action.id()==B.target(active))
            return new Choice(action,terminal(active),0,1);
        for(int depth=1;depth<=maxDepth;depth++) {
            BotAction iteration=best;float[] iterationValue=null;boolean complete=true;
            // Principal variation first, deterministic ties by original ordered candidates.
            List<BotAction> ordered=new ArrayList<>(roots);ordered.remove(best);ordered.add(0,best);
            for(BotAction action:ordered) {
                if(nodes>=budget){complete=false;break;}
                GameState child=state.copy();action.apply(child);
                float[] candidate=search(child,depth-1);
                if(iterationValue==null || candidate[active]>iterationValue[active]+1e-6) {
                    iterationValue=candidate;iteration=action;
                }
            }
            if(complete && iterationValue!=null){best=iteration;value=iterationValue;completed=depth;}
            else break;
        }
        return new Choice(best,value,nodes,completed);
    }
    private float[] search(GameState state,int depth) {
        nodes++;
        if(state.winner()>=0) return terminal(state.winner());
        if(depth==0 || nodes>=budget) return evaluator.evaluate(state);
        long key=hash(state);
        Entry cached=cache.get(key);
        if(cached!=null && cached.depth()==depth) return cached.value();
        int player=state.turn();float[] best=null;
        for(BotAction action:candidates(state,beam)) {
            if(!action.wall() && action.id()==B.target(player)) return terminal(player);
            GameState child=state.copy();action.apply(child);
            float[] score=search(child,depth-1);
            if(best==null || score[player]>best[player]+1e-6) best=score;
            if(nodes>=budget) break;
        }
        if(best==null) best=evaluator.evaluate(state);
        // Never cache a partially searched node.
        if(nodes<budget) cache.put(key,new Entry(depth,best));
        return best;
    }
    private static float[] terminal(int winner){float[] value=new float[3];value[winner]=1;return value;}
    public static long hash(GameState state) {
        long hash=TURN_KEYS[state.turn()];
        for(int p=0;p<3;p++) hash^=PAWN_KEYS[p][state.pawn(p)]^INVENTORY_KEYS[p][state.remaining(p)];
        for(int wall:state.placed()) hash^=WALL_KEYS[0][wall];
        return hash;
    }
    /** Selective walls: edges on shortest routes, imminent wins, and the closest rival's path. */
    public static List<BotAction> candidates(GameState state,int limit) {
        if(state.winner()>=0) return List.of();
        int player=state.turn();
        List<Ranked> ranked=new ArrayList<>();
        List<Integer> legalMoves=MoveGenerator.pawnMoves(state);
        int[] ownRoute=PathFinder.distances(B.target(player),state.blocked());
        int shortest=legalMoves.stream().mapToInt(c->ownRoute[c]).min().orElse(Integer.MAX_VALUE);
        float bestMoveMargin=Float.NEGATIVE_INFINITY;
        for(int destination:legalMoves) {
            // Progress-preserving selective search: learning ranks routes, never an avoidable retreat.
            if(ownRoute[destination]>shortest)continue;
            BotAction action=new BotAction(false,destination);
            GameState child=state.copy();action.apply(child);
            bestMoveMargin=Math.max(bestMoveMargin,raceMargin(child,player));
            ranked.add(new Ranked(action,child.winner()>=0?2:heuristic(child,Style.GUARDIAN)[player]+.0001f));
        }
        if(state.remaining(player)>0) {
            int[][] from=new int[3][],goal=new int[3][];
            for(int p=0;p<3;p++){from[p]=PathFinder.distances(state.pawn(p),state.blocked());goal[p]=PathFinder.distances(B.target(p),state.blocked());}
            List<Ranked> prospects=new ArrayList<>();
            for(Wall wall:B.walls()) {
                if(state.blocked().contains(wall.edgeA()) || state.blocked().contains(wall.edgeB())) continue;
                float score=0;
                for(int p=0;p<3;p++) {
                    int length=goal[p][state.pawn(p)];
                    int edges=onPath(wall.edgeA(),from[p],goal[p],length)+onPath(wall.edgeB(),from[p],goal[p],length);
                    score+=edges*(p==player?-.65f:1f+(length<=3?4f:0f));
                }
                if(score>0) prospects.add(new Ranked(new BotAction(true,wall.id()),score));
            }
            prospects.sort(Comparator.comparingDouble((Ranked r)->-r.score()).thenComparingInt(r->r.action().key()));
            int inspected=0;
            for(Ranked prospect:prospects) {
                if(inspected++>=12) break;
                if(!MoveGenerator.legalWall(state,prospect.action().id())) continue;
                GameState child=state.copy();prospect.action().apply(child);
                // Paying a turn for a wall must improve the race against the earliest rival,
                // rather than merely punish a player already far behind.
                boolean setup=state.remaining(player)>=3 && goal[player][state.pawn(player)]>=4
                    && PathFinder.distance(child.pawn(player),B.target(player),child.blocked())==goal[player][state.pawn(player)]
                    && prospect.score()>=3;
                if(raceMargin(child,player)>=bestMoveMargin-.001f || setup)
                    ranked.add(new Ranked(prospect.action(),heuristic(child,Style.GUARDIAN)[player]));
            }
        }
        ranked.sort(Comparator.comparingDouble((Ranked r)->-r.score()).thenComparingInt(r->r.action().key()));
        // Include all immediate pawn wins regardless of selective beam.
        return ranked.stream().limit(limit).map(Ranked::action).toList();
    }
    private static float raceMargin(GameState state,int player) {
        if(state.winner()>=0)return state.winner()==player?1000:-1000;
        float own=0,earliest=Float.POSITIVE_INFINITY;
        for(int p=0;p<3;p++) {
            int distance=PathFinder.distance(state.pawn(p),B.target(p),state.blocked());
            float arrival=3*(distance-1)+(p-state.turn()+3)%3+1;
            if(p==player)own=arrival;else earliest=Math.min(earliest,arrival);
        }
        return earliest-own;
    }
    private static int onPath(int edge,int[] from,int[] goal,int length) {
        int a=edge/91,b=edge%91;
        return from[a]>=0 && goal[b]>=0 && from[a]+1+goal[b]==length ||
            from[b]>=0 && goal[a]>=0 && from[b]+1+goal[a]==length ?1:0;
    }
}
