package com.example.lumberplundermetaldetector;

import org.apache.commons.math3.util.FastMath;

public class SinewaveGenerator {
    // create a pure tone of the given frequency for the given duration
    /*public static double[] tone(double hz, double duration)
    {
        int n = (int) (SAMPLE_RATE_TX * duration);
        double[] a = new double[n+1];
        for (int i = 0; i <= n; i++)
        {
            a[i] = Math.sin(2 * Math.PI * i * hz / SAMPLE_RATE_TX);
        }
        return a;
    }*/

    public static short[] GenerateSinewave(int sampleRate, double frequency) {
        return GenerateSinewave(sampleRate, frequency, Short.MAX_VALUE, 10);
    }

    public static short[] GenerateSinewave(int sampleRate, double frequency, double amplitude, int numPeriods) {
        //generate x periods worth of samples
        int numSamples = (int)FastMath.ceil(1.0/frequency * sampleRate * numPeriods); //[sec] * [samples/sec] = [samples]

        short[] txBuffer = new short[numSamples];
        double timeStep = 1.0 / sampleRate;

        double time = 0;
        for (int i = 0; i < numSamples; i++) {
            double sample = FastMath.sin(2.0 * FastMath.PI * frequency * time);//Math.sin(2.0 * Math.PI * i / (SAMPLE_RATE_TX)); // Sine wave
            txBuffer[i] = (short) (sample * amplitude);  // Higher amplitude increases volume
            time += timeStep;
        }

        return txBuffer;
    }

    public static short[] GenerateSinewave(int sampleRate, double frequencyHz, double amplitude, double phaseOffsetRad, int numPeriods) {
        //generate x periods worth of samples
        int numSamples = (int)FastMath.ceil(1.0/frequencyHz * sampleRate * numPeriods); //[sec] * [samples/sec] = [samples]

        short[] txBuffer = new short[numSamples];
        double timeStep = 1.0 / sampleRate;

        double time = 0;
        for (int i = 0; i < numSamples; i++) {
            double sample = FastMath.sin(2.0 * FastMath.PI * frequencyHz * time + phaseOffsetRad);//Math.sin(2.0 * Math.PI * i / (SAMPLE_RATE_TX)); // Sine wave
            txBuffer[i] = (short) (sample * amplitude);  // Higher amplitude increases volume
            time += timeStep;
        }

        return txBuffer;
    }
}
