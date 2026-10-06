// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
package hexquoridor;

import java.util.*;

/** Small CPU training primitives; no native ML runtime or external tensor dependencies. */
public final class NeuralMath {
    private NeuralMath() {}
    public static float[] softmax(float[] logits) {
        float max=Float.NEGATIVE_INFINITY;for(float x:logits) max=Math.max(max,x);
        float[] result=new float[logits.length];float sum=0;
        for(int i=0;i<result.length;i++){result[i]=(float)Math.exp(logits[i]-max);sum+=result[i];}
        for(int i=0;i<result.length;i++) result[i]/=sum;
        return result;
    }
    public static float loss(float[] predicted,float[] target) {
        float loss=0;for(int i=0;i<3;i++) loss-=target[i]*(float)Math.log(Math.max(1e-7,predicted[i]));return loss;
    }
    public static final class Parameter {
        final float[] w,g,m,v;
        public Parameter(int size,Random random,float scale) {
            w=new float[size];g=new float[size];m=new float[size];v=new float[size];
            for(int i=0;i<size;i++) w[i]=(float)(random.nextGaussian()*scale);
        }
        public void clear(){Arrays.fill(g,0);}
        public void update(int step,float rate,int batch) {
            float correction1=1-(float)Math.pow(.9,step),correction2=1-(float)Math.pow(.999,step);
            for(int i=0;i<w.length;i++) {
                float grad=Math.max(-5,Math.min(5,g[i]/batch));
                m[i]=.9f*m[i]+.1f*grad;v[i]=.999f*v[i]+.001f*grad*grad;
                w[i]-=rate*((m[i]/correction1)/((float)Math.sqrt(v[i]/correction2)+1e-8f)+.0001f*w[i]);
            }
        }
    }
    public static float[] dense(float[] input,Parameter weight,Parameter bias,int outputs,boolean relu) {
        float[] result=new float[outputs];
        for(int o=0;o<outputs;o++){
            float value=bias.w[o];int offset=o*input.length;
            for(int i=0;i<input.length;i++) value+=weight.w[offset+i]*input[i];
            result[o]=relu?Math.max(0,value):value;
        }
        return result;
    }
    public static float[] backwardDense(float[] input,float[] output,float[] gradient,Parameter weight,Parameter bias,boolean relu) {
        float[] dx=new float[input.length];
        for(int o=0;o<output.length;o++) {
            float delta=relu && output[o]<=0?0:gradient[o];int offset=o*input.length;
            bias.g[o]+=delta;
            for(int i=0;i<input.length;i++){weight.g[offset+i]+=delta*input[i];dx[i]+=delta*weight.w[offset+i];}
        }
        return dx;
    }
}
