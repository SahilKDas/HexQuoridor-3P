// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
package hexquoridor;

import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;

/** Independent held-out starts, balanced seats, explicit draws; no inferred rating. */
public final class BotArena {
    public static void main(String[] args) throws Exception {run(args.length>0?Integer.parseInt(args[0]):12,args.length>1?Long.parseLong(args[1]):20262008L);}
    public static void run(int gamesPerLineup,long seed) throws Exception {
        if(gamesPerLineup<1 || gamesPerLineup>300)throw new IllegalArgumentException("Arena games must be 1..300 per lineup");
        BotService bots=new BotService();
        if(!bots.available("cnn") || !bots.available("nnue"))throw new IllegalArgumentException("Train both models first");
        List<String[]> lineups=List.of(new String[]{"cnn","nnue","guardian"},new String[]{"cnn","greedy","greedy"},new String[]{"nnue","greedy","greedy"},new String[]{"guardian","greedy","greedy"});
        List<Map<String,Object>> results=new ArrayList<>();
        long start=System.nanoTime();Random random=new Random(seed);
        for(String[] lineup:lineups) {
            int[] wins=new int[3];int draws=0,totalPlies=0;long[] think=new long[3];int[] actions=new int[3];
            for(int game=0;game<gamesPerLineup;game++) {
                GameState state=new GameState();int[] agents=new int[3];
                for(int seat=0;seat<3;seat++)agents[seat]=(seat+game)%3;
                for(int i=0,n=3+random.nextInt(7);i<n && state.winner()<0;i++) {
                    if(random.nextDouble()<.35 && state.remaining(state.turn())>0) {
                        List<Integer> walls=MoveGenerator.wallMoves(state);
                        if(!walls.isEmpty()){state.place(walls.get(random.nextInt(walls.size())));continue;}
                    }
                    List<BotAction> opening=BotSearch.candidates(state,6);
                    if(opening.isEmpty())break;opening.get(random.nextInt(opening.size())).apply(state);
                }
                Map<Long,Integer> repetitions=new HashMap<>();
                for(int ply=0;ply<150 && state.winner()<0;ply++) {
                    if(repetitions.merge(BotSearch.hash(state),1,Integer::sum)>=3)break;
                    int agent=agents[state.turn()];long t=System.nanoTime();
                    BotSearch.Choice choice=bots.choose(lineup[agent],state);
                    think[agent]+=System.nanoTime()-t;actions[agent]++;choice.action().apply(state);totalPlies++;
                }
                if(state.winner()>=0)wins[agents[state.winner()]]++;else draws++;
                System.out.printf("arena %s game %d/%d winner=%s%n",Arrays.toString(lineup),game+1,gamesPerLineup,state.winner()<0?"draw":lineup[agents[state.winner()]]);
            }
            double[] latency=new double[3];for(int i=0;i<3;i++)latency[i]=actions[i]==0?0:think[i]/1e6/actions[i];
            results.add(Map.of("lineup",lineup,"games",gamesPerLineup,"winsByAgent",wins,"draws",draws,"totalPlies",totalPlies,"meanActionMillis",latency));
        }
        Map<String,Object> report=Map.of("seed",seed,"gamesPerLineup",gamesPerLineup,"results",results,"elapsedSeconds",(System.nanoTime()-start)/1e9,
            "method","Independent seeded legal opening prefixes (35% random walls), cyclic seat rotation, third repetition/150-ply draw. CNN depth 3, NNUE/Guardian depth 4; bounded selective MaxN. Small local benchmark, not an ELO or state-of-the-art certification.");
        Files.writeString(Path.of("models","arena.json"),new Gson().toJson(report));
        System.out.println("Arena results saved to models/arena.json");
    }
}
