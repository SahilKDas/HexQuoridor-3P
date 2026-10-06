// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2026 HexQuoridor-3P contributors
package hexquoridor;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import hexquoridor.NeuralMath.Parameter;

public abstract class NeuralNetwork {
    protected final List<Parameter> parameters=new ArrayList<>();
    protected final Random random;
    protected boolean training;
    public abstract String kind();
    public abstract float[] forward(BotFeatures.Encoded input);
    public abstract void backward(float[] target);
    protected NeuralNetwork(long seed){random=new Random(seed);}
    protected Parameter parameter(int size,float scale) {
        Parameter p=new Parameter(size,random,scale);parameters.add(p);return p;
    }
    public void clearGradients(){for(Parameter p:parameters)p.clear();}
    public void training(boolean enabled){training=enabled;}
    protected float[] dropout(float[] input,float[] mask) {
        float[] output=input.clone();
        for(int i=0;i<input.length;i++){mask[i]=training?(random.nextFloat()<.25f?0:1f/.75f):1;output[i]*=mask[i];}
        return output;
    }
    public void update(int step,float rate,int batch){for(Parameter p:parameters)p.update(step,rate,batch);}
    public int parameterCount(){return parameters.stream().mapToInt(p->p.w.length).sum();}
    public List<Parameter> parameters(){return List.copyOf(parameters);}
    public void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        try(DataOutputStream output=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
            output.writeInt(0x48514E31);output.writeInt(1);output.writeUTF(kind());output.writeInt(parameters.size());
            for(Parameter p:parameters) {output.writeInt(p.w.length);for(float value:p.w)output.writeFloat(value);}
        }
    }
    public static NeuralNetwork load(Path path,String expectedKind) throws IOException {
        NeuralNetwork model=switch(expectedKind){case "cnn"->new CnnNetwork(0);case "nnue"->new NnueNetwork(0);default->throw new IOException("Unknown model");};
        if(Files.size(path)>2_000_000)throw new IOException("Model too large");
        try(DataInputStream input=new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            if(input.readInt()!=0x48514E31 || input.readInt()!=1 || !input.readUTF().equals(expectedKind)
                || input.readInt()!=model.parameters.size())throw new IOException("Incompatible model");
            for(Parameter p:model.parameters) {
                if(input.readInt()!=p.w.length)throw new IOException("Incompatible model dimensions");
                for(int i=0;i<p.w.length;i++){p.w[i]=input.readFloat();if(!Float.isFinite(p.w[i]))throw new IOException("Invalid model weights");}
            }
            if(input.read()!=-1)throw new IOException("Trailing model data");
        }
        return model;
    }
    public float[] evaluate(GameState state,int perspective) {
        if(state.winner()>=0){float[] result=new float[3];result[state.winner()]=1;return result;}
        return BotFeatures.absolute(forward(BotFeatures.encode(state,perspective)),perspective);
    }
}
