// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
package hexquoridor;

import java.nio.file.*;
import java.util.*;

public final class BotTest {
    private static int assertions=0;
    private static void check(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
    private static void near(float[] a,float[] b,float tolerance,String label){
        check(a.length==b.length,label+" size");
        for(int i=0;i<a.length;i++)check(Math.abs(a[i]-b[i])<tolerance,label+" index "+i);
    }
    public static void main(String[] args) throws Exception {
        GameState state=new GameState();
        state.place(MoveGenerator.wallMoves(state).get(120));
        state.move(new Move(MoveGenerator.pawnMoves(state).get(0)));
        state.place(MoveGenerator.wallMoves(state).get(200));
        long hash=BotSearch.hash(state);GameState copy=state.copy();
        copy.move(new Move(MoveGenerator.pawnMoves(copy).get(0)));
        check(BotSearch.hash(state)==hash && state.revision()==3,"search copies preserve live game");
        check(BotSearch.hash(copy)!=hash,"Zobrist responds to pawn/turn changes");
        for(BotSearch.Style style:BotSearch.Style.values()) {
            BotSearch search=new BotSearch(g->BotSearch.heuristic(g,style),3,3,150);
            BotSearch.Choice choice=search.choose(state);
            GameState child=state.copy();choice.action().apply(child);
            check(child.revision()==4 && PathFinder.all(child,child.blocked()),"teacher legal and path safe "+style);
            check(choice.nodes()<=150 && choice.depth()>=1,"bounded teacher search");
            check(search.choose(state).action().equals(choice.action()),"deterministic teacher");
        }
        // Equivalent cyclic orientation: initial positions rotate onto themselves; active player changes.
        BotFeatures.Encoded original=BotFeatures.encode(new GameState(),0);
        BotFeatures.Encoded rotated=BotFeatures.encode(new GameState().viewTurn(1),1);
        check(Arrays.equals(original.active(),rotated.active()),"canonical sparse rotation");
        near(original.grid(),rotated.grid(),1e-7f,"canonical convolution rotation");
        near(original.scalars(),rotated.scalars(),1e-7f,"canonical scalar rotation");
        HexBoard topology=HexBoard.INSTANCE;
        for(int rotation=0;rotation<3;rotation++)for(int cell=0;cell<91;cell++)for(int d=0;d<6;d++) {
            int neighbor=topology.neighbor(cell,d);
            if(neighbor>=0)check(BotFeatures.ROTATION[rotation][neighbor]==topology.neighbor(BotFeatures.ROTATION[rotation][cell],(d+4*rotation)%6),"rotated edge channel covariance");
        }
        for(NeuralNetwork model:List.of(new CnnNetwork(42),new NnueNetwork(43))) {
            BotFeatures.Encoded features=BotFeatures.encode(state,0);
            float[] probability=model.forward(features);float total=0;
            for(float value:probability){check(Float.isFinite(value)&&value>=0&&value<=1,"probability range");total+=value;}
            check(Math.abs(total-1)<1e-6,"probability sum");
            float[] target={.73f,.19f,.08f};
            model.clearGradients();model.forward(features);model.backward(target);
            for(NeuralMath.Parameter parameter:model.parameters()) {
                int index=0;for(int i=1;i<parameter.w.length;i++)if(Math.abs(parameter.g[i])>Math.abs(parameter.g[index]))index=i;
                float analytic=parameter.g[index],value=parameter.w[index],epsilon=.001f;
                parameter.w[index]=value+epsilon;float plus=NeuralMath.loss(model.forward(features),target);
                parameter.w[index]=value-epsilon;float minus=NeuralMath.loss(model.forward(features),target);
                parameter.w[index]=value;float numeric=(plus-minus)/(2*epsilon);
                check(Math.abs(numeric-analytic)<.002f+.03f*Math.abs(analytic),model.kind()+" finite-difference backprop: "+analytic+" vs "+numeric);
            }
            Path path=Path.of(".tools","bot-tests",model.kind()+".hqnn");model.save(path);
            near(model.forward(features),NeuralNetwork.load(path,model.kind()).forward(features),1e-7f,"checkpoint roundtrip");
            byte[] corrupted=Files.readAllBytes(path);corrupted[0]=0;Files.write(path,corrupted);
            boolean rejected=false;try{NeuralNetwork.load(path,model.kind());}catch(java.io.IOException e){rejected=true;}
            check(rejected,"corrupt checkpoint rejected");
            if(model instanceof NnueNetwork nnue) {
                for(int perspective=0;perspective<3;perspective++) {
                    NnueNetwork.Accumulator base=nnue.accumulator(state,perspective);
                    List<BotAction> actions=new ArrayList<>(BotSearch.candidates(state,8));
                    actions.add(new BotAction(true,MoveGenerator.wallMoves(state).get(0)));
                    for(BotAction action:actions) {
                        GameState child=state.copy();action.apply(child);BotFeatures.Encoded input=BotFeatures.encode(child,perspective);
                        near(nnue.forward(input),nnue.predict(base,input),2e-6f,"incremental accumulator vs refresh");
                    }
                }
            }
        }
        BotService bots=new BotService();
        check(bots.available("cnn") && bots.available("nnue"),"trained checkpoints present and verified");
        for(String kind:List.of("cnn","nnue")) {
            BotSearch.Choice choice=bots.choose(kind,state);
            GameState child=state.copy();choice.action().apply(child);
            check(child.revision()==4 && PathFinder.all(child,child.blocked()),kind+" emits legal actions");
            check(bots.choose(kind,state).action().equals(choice.action()),kind+" deterministic inference");
            // All bot types must take an immediate winning move, including unfamiliar inputs.
            HexBoard board=HexBoard.INSTANCE;GameState win=new GameState();int target=board.target(0);
            win.setup(new int[]{board.neighbor(target,2),board.index(new CubeCoord(-3,0)),board.index(new CubeCoord(0,-3))},0);
            check(bots.choose(kind,win).action().equals(new BotAction(false,target)),kind+" immediate win");
        }
        for(String kind:List.of("cnn","nnue","racer","guardian","architect")) {
            HexBoard board=HexBoard.INSTANCE;GameState threat=new GameState();
            threat.setup(new int[]{board.index(new CubeCoord(0,0)),board.index(new CubeCoord(-4,0)),board.index(new CubeCoord(-2,1))},0);
            check(MoveGenerator.pawnMoves(threat.viewTurn(1)).contains(board.target(1)),"opponent has immediate threat");
            BotAction defense=bots.choose(kind,threat).action();defense.apply(threat);
            check(defense.wall() && !MoveGenerator.pawnMoves(threat).contains(board.target(1)),kind+" blocks immediate rival win");
        }
        System.out.println("PASS: "+assertions+" neural/search assertions (gradients, checkpoints, rotation, incremental NNUE, legal actions, determinism, immediate wins)");
    }
}
