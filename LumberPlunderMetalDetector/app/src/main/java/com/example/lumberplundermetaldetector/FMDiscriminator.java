package com.example.lumberplundermetaldetector;

public class FMDiscriminator {

    private double prevIAllen = 0;
    private double prevQAllen = 0;
    private int fmGainAllen;

    public FMDiscriminator() {
        fmGainAllen = 25000;
    }

    public FMDiscriminator(int fmGain) {
        fmGainAllen = fmGain;
    }

    //could be sped up more by only calculating denom occasionally, and switching to all integer math (maybe)
    public double FMDiscrimAllenFast(double I, double Q) {
        //todo: limiting

        //Limiting?
        double m = Math.sqrt(I * I + Q * Q);
        if (m > 0.0) {
            m = 1.0 / m;
            I = I * m;
            Q = Q * m;
        }


        double dI = I - prevIAllen;
        double dQ = Q - prevQAllen;

        double denom = (I * I + Q * Q);
        if (denom == 0) return 0;

        double nom = (I * dQ - Q * dI);

        double result = (nom / denom * fmGainAllen);

        prevIAllen = I;
        prevQAllen = Q;

        return result;
    }
}
