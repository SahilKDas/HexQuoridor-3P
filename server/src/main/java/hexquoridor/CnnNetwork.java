// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
package hexquoridor;

import hexquoridor.NeuralMath.Parameter;

/** Actual two-layer hex convolution (center plus six neighbors), global mean/max pooling. */
public final class CnnNetwork extends NeuralNetwork {
    static final int FIRST=6,SECOND=8,DENSE=24;
    private static final int[][] PATCH=new int[91][7];
    static {for(int c=0;c<91;c++){PATCH[c][0]=c;for(int d=0;d<6;d++)PATCH[c][d+1]=HexBoard.INSTANCE.neighbor(c,d);}}
    private final Parameter conv1,bias1,conv2,bias2,denseWeight,denseBias,outputWeight,outputBias;
    private BotFeatures.Encoded cached;
    private float[] first,second,pooled,hidden,hiddenRaw,probability;
    private final float[] dropoutMask=new float[DENSE];
    private final int[] maxima=new int[SECOND];
    public CnnNetwork(long seed) {
        super(seed);
        conv1=parameter(19*7*FIRST,(float)Math.sqrt(2d/(19*7)));bias1=parameter(FIRST,0);
        conv2=parameter(FIRST*7*SECOND,(float)Math.sqrt(2d/(FIRST*7)));bias2=parameter(SECOND,0);
        denseWeight=parameter((SECOND*2+9)*DENSE,(float)Math.sqrt(2d/(SECOND*2+9)));denseBias=parameter(DENSE,0);
        outputWeight=parameter(DENSE*3,.1f);outputBias=parameter(3,0);
    }
    @Override public String kind(){return "cnn";}
    private static float[] convolution(float[] input,int channels,int outputs,Parameter weights,Parameter bias) {
        float[] result=new float[91*outputs];
        for(int c=0;c<91;c++)for(int o=0;o<outputs;o++) {
            float value=bias.w[o];int filter=o*channels*7;
            for(int k=0;k<7;k++){
                int neighbor=PATCH[c][k];if(neighbor<0)continue;int base=neighbor*channels;
                for(int i=0;i<channels;i++)value+=input[base+i]*weights.w[filter+i*7+k];
            }
            result[c*outputs+o]=Math.max(0,value);
        }
        return result;
    }
    @Override public float[] forward(BotFeatures.Encoded input) {
        cached=input;
        first=convolution(input.grid(),19,FIRST,conv1,bias1);
        second=convolution(first,FIRST,SECOND,conv2,bias2);
        pooled=new float[SECOND*2+9];
        for(int h=0;h<SECOND;h++) {
            float max=-1,sum=0;int argmax=0;
            for(int c=0;c<91;c++){float value=second[c*SECOND+h];sum+=value;if(value>max){max=value;argmax=c;}}
            pooled[h]=sum/91;pooled[SECOND+h]=max;maxima[h]=argmax;
        }
        System.arraycopy(input.scalars(),0,pooled,SECOND*2,9);
        hiddenRaw=NeuralMath.dense(pooled,denseWeight,denseBias,DENSE,true);
        hidden=dropout(hiddenRaw,dropoutMask);
        probability=NeuralMath.softmax(NeuralMath.dense(hidden,outputWeight,outputBias,3,false));
        return probability.clone();
    }
    private static float[] backwardConv(float[] input,float[] output,float[] gradient,int channels,int outputs,
        Parameter weights,Parameter bias,boolean needInput) {
        float[] dx=needInput?new float[input.length]:null;
        for(int c=0;c<91;c++)for(int o=0;o<outputs;o++) {
            if(output[c*outputs+o]<=0)continue;
            float delta=gradient[c*outputs+o];bias.g[o]+=delta;int filter=o*channels*7;
            for(int k=0;k<7;k++){
                int neighbor=PATCH[c][k];if(neighbor<0)continue;int base=neighbor*channels;
                for(int i=0;i<channels;i++){
                    int index=filter+i*7+k;weights.g[index]+=delta*input[base+i];
                    if(needInput)dx[base+i]+=delta*weights.w[index];
                }
            }
        }
        return dx;
    }
    @Override public void backward(float[] target) {
        float[] gradient=new float[3];for(int p=0;p<3;p++)gradient[p]=probability[p]-target[p];
        float[] dh=NeuralMath.backwardDense(hidden,probability,gradient,outputWeight,outputBias,false);
        for(int i=0;i<DENSE;i++)dh[i]*=dropoutMask[i];
        float[] dp=NeuralMath.backwardDense(pooled,hiddenRaw,dh,denseWeight,denseBias,true);
        float[] dconv2=new float[91*SECOND];
        for(int h=0;h<SECOND;h++){
            for(int c=0;c<91;c++)dconv2[c*SECOND+h]=dp[h]/91;
            dconv2[maxima[h]*SECOND+h]+=dp[SECOND+h];
        }
        float[] dconv1=backwardConv(first,second,dconv2,FIRST,SECOND,conv2,bias2,true);
        backwardConv(cached.grid(),first,dconv1,19,FIRST,conv1,bias1,false);
    }
}
