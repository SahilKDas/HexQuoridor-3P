// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
package hexquoridor;

import java.util.*;
import hexquoridor.NeuralMath.Parameter;

/** Sparse 537 -> 64 clipped-ReLU accumulator; (64+9) -> 32 ReLU -> 3 softmax. */
public final class NnueNetwork extends NeuralNetwork {
    static final int HIDDEN=64,DENSE=32;
    private final Parameter featureWeight,featureBias,denseWeight,denseBias,outputWeight,outputBias;
    private BotFeatures.Encoded cached;
    private float[] accumulator,denseInput,hidden,hiddenRaw,probability;
    private final float[] dropoutMask=new float[DENSE];
    public record Accumulator(int[] features,float[] values,int perspective) {}
    public NnueNetwork(long seed) {
        super(seed);
        featureWeight=parameter(BotFeatures.SPARSE*HIDDEN,.035f);featureBias=parameter(HIDDEN,0);
        Arrays.fill(featureBias.w,.2f);
        denseWeight=parameter((HIDDEN+9)*DENSE,(float)Math.sqrt(2d/(HIDDEN+9)));denseBias=parameter(DENSE,0);
        outputWeight=parameter(DENSE*3,.1f);outputBias=parameter(3,0);
    }
    @Override public String kind(){return "nnue";}
    private float[] accumulate(int[] features) {
        float[] values=featureBias.w.clone();
        for(int feature:features)for(int h=0;h<HIDDEN;h++)values[h]+=featureWeight.w[feature*HIDDEN+h];
        return values;
    }
    public Accumulator accumulator(GameState state,int perspective) {
        BotFeatures.Encoded encoded=BotFeatures.encode(state,perspective);
        return new Accumulator(encoded.active(),accumulate(encoded.active()),perspective);
    }
    public Accumulator transition(Accumulator base,BotFeatures.Encoded next) {
        if(base.perspective()!=next.perspective())throw new IllegalArgumentException("Accumulator perspective changed");
        float[] values=base.values().clone();int i=0,j=0;int[] before=base.features(),after=next.active();
        while(i<before.length || j<after.length) {
            if(i<before.length && j<after.length && before[i]==after[j]) {i++;j++;continue;}
            boolean remove=j==after.length || i<before.length && before[i]<after[j];
            int feature=remove?before[i++]:after[j++];float sign=remove?-1:1;
            for(int h=0;h<HIDDEN;h++)values[h]+=sign*featureWeight.w[feature*HIDDEN+h];
        }
        return new Accumulator(after,values,next.perspective());
    }
    public float[] predict(Accumulator base,BotFeatures.Encoded next) {
        return finish(next,transition(base,next).values());
    }
    private float[] finish(BotFeatures.Encoded input,float[] values) {
        cached=input;accumulator=values;
        denseInput=new float[HIDDEN+9];
        for(int h=0;h<HIDDEN;h++)denseInput[h]=Math.max(0,Math.min(1,values[h]));
        System.arraycopy(input.scalars(),0,denseInput,HIDDEN,9);
        hiddenRaw=NeuralMath.dense(denseInput,denseWeight,denseBias,DENSE,true);
        hidden=dropout(hiddenRaw,dropoutMask);
        probability=NeuralMath.softmax(NeuralMath.dense(hidden,outputWeight,outputBias,3,false));
        return probability.clone();
    }
    @Override public float[] forward(BotFeatures.Encoded input){return finish(input,accumulate(input.active()));}
    @Override public void backward(float[] target) {
        float[] gradient=new float[3];for(int p=0;p<3;p++)gradient[p]=probability[p]-target[p];
        float[] dh=NeuralMath.backwardDense(hidden,probability,gradient,outputWeight,outputBias,false);
        for(int i=0;i<DENSE;i++)dh[i]*=dropoutMask[i];
        float[] dacc=NeuralMath.backwardDense(denseInput,hiddenRaw,dh,denseWeight,denseBias,true);
        for(int h=0;h<HIDDEN;h++) {
            float delta=accumulator[h]>0 && accumulator[h]<1?dacc[h]:0;
            featureBias.g[h]+=delta;
            for(int feature:cached.active()) featureWeight.g[feature*HIDDEN+h]+=delta;
        }
    }
}
