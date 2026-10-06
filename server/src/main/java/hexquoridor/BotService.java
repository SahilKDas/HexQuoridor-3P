// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
package hexquoridor;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Trained model inference with exact legal actions; never substitutes random weights. */
public final class BotService {
    public static final Set<String> IDS=Set.of("human","greedy","racer","guardian","architect","cnn","nnue");
    private final Map<String,NeuralNetwork> models=new HashMap<>();
    private final Map<String,Long> timestamps=new HashMap<>();
    private static final HexBoard B=HexBoard.INSTANCE;
    private NeuralNetwork model(String kind) {
        try {
            Path path=Path.of("models",kind+".hqnn");long stamp=Files.getLastModifiedTime(path).toMillis();
            if(!models.containsKey(kind) || !Objects.equals(timestamps.get(kind),stamp)) {
                JsonObject manifest=JsonParser.parseString(Files.readString(Path.of("models","manifest.json"))).getAsJsonObject();
                String expected=manifest.getAsJsonObject("models").getAsJsonObject(kind).get("sha256").getAsString();
                if(!BotTraining.sha256(path).equals(expected))throw new IllegalArgumentException("Model checksum mismatch");
                models.put(kind,NeuralNetwork.load(path,kind));timestamps.put(kind,stamp);
            }
            return models.get(kind);
        } catch(Exception error) {throw new IllegalArgumentException(kind.toUpperCase()+" trained model unavailable; run pnpm bots:train");}
    }
    public synchronized boolean available(String kind) {
        if(!Set.of("cnn","nnue").contains(kind))return IDS.contains(kind);
        try{model(kind);return true;}catch(IllegalArgumentException ignored){return false;}
    }
    public synchronized List<Map<String,Object>> catalog() {
        List<Map<String,Object>> result=new ArrayList<>();
        for(String id:List.of("greedy","racer","guardian","architect","cnn","nnue")) result.add(Map.of("id",id,"available",available(id),
            "name",switch(id){case "cnn"->"Hex CNN";case "nnue"->"NNUE";case "racer"->"Racer teacher";case "guardian"->"Guardian teacher";case "architect"->"Architect teacher";default->"Greedy baseline";}));
        return result;
    }
    public synchronized BotSearch.Choice choose(String id,GameState state) {
        if(!IDS.contains(id) || id.equals("human"))throw new IllegalArgumentException("Unknown bot");
        if(id.equals("greedy")) {
            int target=B.target(state.turn());
            int move=MoveGenerator.pawnMoves(state).stream().min(Comparator.comparingInt((Integer c)->PathFinder.distance(c,target,state.blocked())).thenComparingInt(Integer::intValue))
                .orElseThrow(()->new IllegalArgumentException("No legal pawn move"));
            GameState child=state.copy();BotAction action=new BotAction(false,move);action.apply(child);
            return new BotSearch.Choice(action,BotSearch.heuristic(child,BotSearch.Style.RACER),0,1);
        }
        if(Set.of("racer","guardian","architect").contains(id)) {
            BotSearch.Style style=BotSearch.Style.valueOf(id.toUpperCase(Locale.ROOT));
            return new BotSearch(g->BotSearch.heuristic(g,style),4,3,450).choose(state);
        }
        NeuralNetwork network=model(id);int perspective=state.turn();
        BotSearch.Evaluator evaluator;
        if(network instanceof NnueNetwork nnue) {
            NnueNetwork.Accumulator base=nnue.accumulator(state,perspective);
            evaluator=g->{
                if(g.winner()>=0){float[] result=new float[3];result[g.winner()]=1;return result;}
                return BotFeatures.absolute(nnue.predict(base,BotFeatures.encode(g,perspective)),perspective);
            };
        } else evaluator=g->network.evaluate(g,perspective);
        return new BotSearch(evaluator,id.equals("cnn")?3:4,3,id.equals("cnn")?150:450).choose(state);
    }
}
