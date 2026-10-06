// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
package hexquoridor;

import com.google.gson.Gson;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Reproducible handcrafted-teacher self-play, grouped holdouts, AdamW distillation. */
public final class BotTraining {
    private static final Gson JSON=new Gson();
    record Position(int[] pawns,int[] remaining,List<Integer> walls,int turn) {
        static Position of(GameState g) {
            int[] pawns=new int[3],remaining=new int[3];
            for(int p=0;p<3;p++){pawns[p]=g.pawn(p);remaining[p]=g.remaining(p);}
            return new Position(pawns,remaining,g.placed(),g.turn());
        }
    }
    record Sample(int game,String teacher,Position position,float[] searchTarget,float[] target) {}
    record Example(Sample sample,BotFeatures.Encoded input,float[] target) {}
    record Metrics(double crossEntropy,double brier,double targetAgreement,int examples) {}
    record Fit(String architecture,int parameters,double initialValidationLoss,Metrics validation,Metrics test,int bestEpoch,String sha256) {}
    public static void main(String[] args) throws Exception {
        Map<String,String> options=new HashMap<>();
        for(int i=0;i<args.length;i+=2) {
            if(i+1>=args.length || !Set.of("--games","--epochs","--seed","--positions","--benchmark").contains(args[i]))
                throw new IllegalArgumentException("Use --games N --epochs N --seed N --positions N --benchmark N");
            options.put(args[i],args[i+1]);
        }
        int games=number(options,"--games",180,20,5000),epochs=number(options,"--epochs",24,1,200);
        int cap=number(options,"--positions",9000,100,50000),benchmark=number(options,"--benchmark",12,0,300);
        long seed=Long.parseLong(options.getOrDefault("--seed","20261005"));
        long started=System.nanoTime();
        Path directory=Path.of("models");Files.createDirectories(directory);
        Random random=new Random(seed);
        List<List<Example>> splits=List.of(new ArrayList<>(),new ArrayList<>(),new ArrayList<>());
        int[] wins=new int[3];int draws=0,generated=0;
        Path dataset=directory.resolve("selfplay.jsonl");
        try(BufferedWriter writer=Files.newBufferedWriter(dataset)) {
            for(int gameId=0;gameId<games;gameId++) {
                GameState state=new GameState();
                // Seeded varied legal opening prefixes: different matches, not one repeated opening.
                int opening=2+random.nextInt(9);
                for(int i=0;i<opening && state.winner()<0;i++) {
                    if(random.nextDouble()<.35 && state.remaining(state.turn())>0) {
                        List<Integer> walls=MoveGenerator.wallMoves(state);
                        if(!walls.isEmpty()){state.place(walls.get(random.nextInt(walls.size())));continue;}
                    }
                    List<BotAction> actions=BotSearch.candidates(state,6);
                    if(actions.isEmpty())break;
                    actions.get(random.nextInt(actions.size())).apply(state);
                }
                List<Sample> history=new ArrayList<>();
                List<GameState> states=new ArrayList<>();
                Map<Long,Integer> repetitions=new HashMap<>();
                BotSearch.Style[] styles=new BotSearch.Style[3];
                for(int p=0;p<3;p++)styles[p]=BotSearch.Style.values()[(p+gameId)%3];
                for(int turn=0;turn<120 && state.winner()<0;turn++) {
                    if(repetitions.merge(BotSearch.hash(state),1,Integer::sum)>=3)break;
                    BotSearch.Style style=styles[state.turn()];
                    BotSearch search=new BotSearch(g->BotSearch.heuristic(g,style),4,3,450);
                    BotSearch.Choice choice=search.choose(state);
                    history.add(new Sample(gameId,style.name(),Position.of(state),choice.value(),null));
                    states.add(state.copy());
                    BotAction action=choice.action();
                    if(random.nextDouble()<.07) {
                        List<BotAction> exploration=BotSearch.candidates(state,4);
                        action=exploration.get(random.nextInt(exploration.size()));
                    }
                    action.apply(state);
                }
                int winner=state.winner();if(winner>=0)wins[winner]++;else draws++;
                int split=gameId%10<8?0:gameId%10==8?1:2;
                for(int i=0;i<history.size();i++) {
                    Sample old=history.get(i);float[] target=new float[3];
                    for(int p=0;p<3;p++)target[p]=.75f*old.searchTarget()[p]+.25f*(winner<0?1f/3:p==winner?1:0);
                    Sample sample=new Sample(gameId,old.teacher(),old.position(),old.searchTarget(),target);
                    writer.write(JSON.toJson(sample));writer.newLine();generated++;
                    GameState g=states.get(i);
                    // Train every player's perspective and every relative side-to-move encoding.
                    for(int perspective=0;perspective<3;perspective++)
                        splits.get(split).add(new Example(sample,BotFeatures.encode(g,perspective),BotFeatures.relative(target,perspective)));
                }
                if((gameId+1)%5==0 || gameId==games-1)System.out.printf(Locale.ROOT,"self-play %d/%d games; %d positions; %d draws%n",gameId+1,games,generated,draws);
            }
        }
        // Entire games are split first; exact canonical positions are then removed from later splits.
        Set<String> seen=new HashSet<>();int[] before=new int[3];
        for(int split=0;split<3;split++) {
            List<Example> values=splits.get(split);before[split]=values.size();
            Collections.shuffle(values,new Random(seed+split*991));
            values.removeIf(e->!seen.add(signature(e.input())));
            if(values.size()>cap)values.subList(cap,values.size()).clear();
            if(values.isEmpty())throw new IllegalStateException("Not enough distinct held-out positions; use more games");
        }
        System.out.printf("disjoint canonical positions: train=%d validation=%d test=%d%n",splits.get(0).size(),splits.get(1).size(),splits.get(2).size());
        Map<String,Fit> fits=new LinkedHashMap<>();
        for(NeuralNetwork network:List.of(new CnnNetwork(seed+10),new NnueNetwork(seed+20))) {
            fits.put(network.kind(),fit(network,splits,epochs,seed,directory));
        }
        Map<String,Object> report=new LinkedHashMap<>();
        report.put("format",1);report.put("seed",seed);report.put("games",games);report.put("generatedPositions",generated);
        report.put("selfPlayWinsBySeat",wins);report.put("selfPlayDraws",draws);report.put("drawRule","Training only: 120 plies or third repetition; live rules unchanged");
        report.put("teachers",List.of("RACER","GUARDIAN","ARCHITECT"));report.put("teacherSearch","iterative-deepening MaxN, depth 4, beam 3 (root 6), 450-node budget, Zobrist transpositions, tempo-aware walls and progress-preserving pawn selection");
        report.put("opening","2-10 seeded legal actions; 35% random legal walls, otherwise selective actions");
        report.put("target","75% teacher MaxN utility + 25% actual self-play outcome; draws uniform");
        report.put("exploration",.07);report.put("split","whole game id modulo 10: 80/10/10; canonical duplicate removal across splits");
        report.put("augmentation","all three evaluation perspectives per state, including all relative sides to move");
        report.put("splitBeforeDeduplication",before);report.put("trainPositions",splits.get(0).size());report.put("validationPositions",splits.get(1).size());report.put("testPositions",splits.get(2).size());
        report.put("optimizer","AdamW minibatch 32, initial learning rate 0.002, decay 0.97 per epoch, weight decay 0.0001, hidden dropout 25%");
        report.put("datasetSha256",sha256(dataset));report.put("models",fits);
        report.put("uniformCrossEntropy",Math.log(3));report.put("elapsedSeconds",(System.nanoTime()-started)/1e9);
        report.put("strengthStatus","Experimental, locally trained; no state-of-the-art or ELO claim.");
        Files.writeString(directory.resolve("manifest.json"),JSON.toJson(report));
        System.out.println("Saved trained CNN and NNUE checkpoints and provenance in models/");
        if(benchmark>0) BotArena.run(benchmark,seed+1_000_003);
    }
    private static int number(Map<String,String> options,String key,int fallback,int min,int max) {
        int value=Integer.parseInt(options.getOrDefault(key,Integer.toString(fallback)));
        if(value<min || value>max)throw new IllegalArgumentException(key+" must be "+min+".."+max);return value;
    }
    static String signature(BotFeatures.Encoded input) {
        return Arrays.toString(input.active())+Arrays.toString(input.scalars());
    }
    private static Fit fit(NeuralNetwork model,List<List<Example>> splits,int epochs,long seed,Path dir) throws Exception {
        List<Example> train=splits.get(0),validation=splits.get(1),test=splits.get(2);
        double initial=metrics(model,validation).crossEntropy(),best=Double.POSITIVE_INFINITY;int bestEpoch=0,step=0;
        Path checkpoint=dir.resolve(model.kind()+".hqnn");Random random=new Random(seed^model.kind().hashCode());
        for(int epoch=1;epoch<=epochs;epoch++) {
            model.training(true);
            Collections.shuffle(train,random);double loss=0;
            for(int start=0;start<train.size();start+=32) {
                int end=Math.min(start+32,train.size());model.clearGradients();
                for(int i=start;i<end;i++){Example e=train.get(i);loss+=NeuralMath.loss(model.forward(e.input()),e.target());model.backward(e.target());}
                model.update(++step,(float)(.002*Math.pow(.97,epoch-1)),end-start);
            }
            model.training(false);
            Metrics metrics=metrics(model,validation);
            if(metrics.crossEntropy()<best){best=metrics.crossEntropy();bestEpoch=epoch;model.save(checkpoint);}
            System.out.printf(Locale.ROOT,"%s epoch %d/%d train CE=%.5f held-out CE=%.5f best=%.5f%n",model.kind(),epoch,epochs,loss/train.size(),metrics.crossEntropy(),best);
        }
        NeuralNetwork bestModel=NeuralNetwork.load(checkpoint,model.kind());
        Metrics testMetrics=metrics(bestModel,test);
        if(!Double.isFinite(best) || best>=initial)throw new IllegalStateException(model.kind()+" failed to improve held-out loss");
        System.out.printf(Locale.ROOT,"%s validation %.5f -> %.5f; test CE=%.5f%n",model.kind(),initial,best,testMetrics.crossEntropy());
        return new Fit(model.kind(),model.parameterCount(),initial,metrics(bestModel,validation),testMetrics,bestEpoch,sha256(checkpoint));
    }
    private static Metrics metrics(NeuralNetwork model,List<Example> data) {
        double loss=0,brier=0;int agreement=0;
        for(Example e:data) {
            float[] prediction=model.forward(e.input());loss+=NeuralMath.loss(prediction,e.target());
            int actual=0,predicted=0;for(int p=0;p<3;p++){double d=prediction[p]-e.target()[p];brier+=d*d;if(e.target()[p]>e.target()[actual])actual=p;if(prediction[p]>prediction[predicted])predicted=p;}
            if(actual==predicted)agreement++;
        }
        return new Metrics(loss/data.size(),brier/data.size(),agreement/(double)data.size(),data.size());
    }
    public static String sha256(Path file) throws IOException,NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
