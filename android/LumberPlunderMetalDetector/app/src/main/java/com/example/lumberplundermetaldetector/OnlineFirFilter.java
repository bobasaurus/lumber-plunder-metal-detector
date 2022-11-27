package com.example.lumberplundermetaldetector;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

public class OnlineFirFilter {

    /*private double[] coeffs;
    private int N;

    public FIRFilter(double[] coefficients) {
        coeffs = coefficients;
        N = coeffs.length;
    }

    private int n = 0;
    private double[] x = new double[N];

    public double filter(double x_in) {
        double y = 0.0;

        // Store the current input, overwriting the oldest input
        x[n] = x_in;

        // Multiply the filter coefficients by the previous inputs and sum
        for (int i = 0; i < N; i++) {
            y += coeffs[i] * x[((N - i) + n) % N];
        }

        // Increment the input buffer index to the next location
        n = (n + 1) % N;

        return y;
    }*/


    private double[] _coefficients;
    private double[] _buffer;
    private int _offset;
    private int _size;

    /// <summary>
    /// Finite Impulse Response (FIR) Filter.
    /// </summary>
    public OnlineFirFilter(double[] coefficients)
    {
        _size = coefficients.length;
        _buffer = new double[_size];
        _coefficients = new double[_size << 1];
        for (int i = 0; i < _size; i++)
        {
            _coefficients[i] = _coefficients[_size + i] = coefficients[i];
        }
    }

    /// <summary>
    /// Process a single sample.
    /// </summary>
    public double ProcessSample(double sample)
    {
        _offset = (_offset != 0) ? _offset - 1 : _size - 1;
        _buffer[_offset] = sample;

        double acc = 0;
        for (int i = 0, j = _size - _offset; i < _size; i++, j++)
        {
            acc += _buffer[i] * _coefficients[j];
        }

        return acc;
    }

    /// <summary>
    /// Reset internal state (not coefficients!).
    /// </summary>
    public void Reset()
    {
        for (int i = 0; i < _buffer.length; i++)
        {
            _buffer[i] = 0d;
        }
    }



    //todo: these all default to the blackman harris window, mabe make it configurable in the future
    /**
     * Calculate a lowpass FIR filter using Math.NET Filtering (https://www.mathdotnet.com/)
     */
    public static double[] CalcLowpassFilter(double sampleRate, double cutoffFrequency, int filterLength) {
        double[] mathNetCoeffs = LowPass(sampleRate, cutoffFrequency, 1.0, filterLength / 2);
        double[] windowArray = BlackmanHarris(mathNetCoeffs.length);
        for (int i = 0; i < mathNetCoeffs.length; i++) {
            mathNetCoeffs[i] *= windowArray[i];
        }

        return mathNetCoeffs;
    }

    /**
     * Calculate a highpass FIR filter using Math.NET Filtering (https://www.mathdotnet.com/)
     */
    public static double[] CalcHighpassFilter(double sampleRate, double cutoffFrequency, int filterLength) {
        double[] mathNetCoeffs = HighPass(sampleRate, cutoffFrequency, filterLength / 2);
        double[] windowArray = BlackmanHarris(mathNetCoeffs.length);
        for (int i = 0; i < mathNetCoeffs.length; i++) {
            mathNetCoeffs[i] *= windowArray[i];
        }

        return mathNetCoeffs;
    }

    /**
     * Calculate a bandpass FIR filter using Math.NET Filtering (https://www.mathdotnet.com/)
     */
    public static double[] CalcBandpassFilter(double sampleRate, double cutoffFrequencyLow, double cutoffFrequencyHigh, int filterLength) {
        double[] mathNetCoeffs = BandPass(sampleRate, cutoffFrequencyLow, cutoffFrequencyHigh, filterLength / 2);
        double[] windowArray = BlackmanHarris(mathNetCoeffs.length);
        for (int i = 0; i < mathNetCoeffs.length; i++) {
            mathNetCoeffs[i] *= windowArray[i];
        }

        return mathNetCoeffs;
    }

    private static double SumDoubleArr(double[] arr) {
        double result = 0;
        for (double a : arr) {
            result += a;
        }
        return result;
    }


    /// <summary>
    /// Calculates FIR LowPass Filter Coefficients.
    /// </summary>
    /// <param name="samplingRate">Samples per unit.</param>
    /// <param name="cutoff">Desired cutoff frequency in samples per unit.</param>
    /// <param name="dcGain">Desired DC gain. Defaults to 1.0.</param>
    /// <param name="halforder">half-order Q, so that Order N = 2*Q+1. 0 for default order.</param>
    /// <returns>The calculated filter coefficients.</returns>
    private static double[] LowPass(double samplingRate, double cutoff, double dcGain /*= 1.0*/, int halforder /*= 0*/) {
        // Default filter order
        if (halforder == 0) {
            double TRANSWINDRATIO = 0.25;
            double maxDf = samplingRate / 2 - cutoff;
            double df = (cutoff * TRANSWINDRATIO > 2) ? cutoff * TRANSWINDRATIO : 2;
            df = (df < maxDf) ? df : maxDf;
            halforder = (int) Math.ceil(3.3 / (df / samplingRate) / 2);
        }

        int order = 2 * halforder + 1;
        double nu = 2d * cutoff / samplingRate; // normalized frequency

        double[] c = new double[order];
        c[halforder] = nu;

        for (int i = 0, n = halforder; i < halforder; i++, n--) {
            double npi = n * Math.PI;
            c[i] = Math.sin(npi * nu) / npi;
            c[n + halforder] = c[i];
        }

        // Unity gain at DC
        double actualDcGain = SumDoubleArr(c);// DoubleStream.of(c).sum();// Arrays.stream(c).sum();
        double scaling = dcGain / actualDcGain;
        for (int i = 0; i < c.length; i++) {
            c[i] *= scaling;
        }

        return c;
    }

    /// <summary>
    /// Calculates FIR HighPass Filter Coefficients.
    /// </summary>
    /// <param name="samplingRate">Samples per unit.</param>
    /// <param name="cutoff">Cutoff frequency in samples per unit.</param>
    /// <param name="halforder">half-order Q, so that Order N = 2*Q+1. 0 for default order.</param>
    /// <returns>The calculated filter coefficients.</returns>
    private static double[] HighPass(double samplingRate, double cutoff, int halforder /*= 0*/) {
        // Default filter order
        if (halforder == 0) {
            double TRANSWINDRATIO = 0.25;
            double maxDf = cutoff;
            double df = (maxDf * TRANSWINDRATIO > 2) ? maxDf * TRANSWINDRATIO : 2;
            df = (df < maxDf) ? df : maxDf;
            halforder = (int) Math.ceil(3.3 / (df / samplingRate) / 2);
        }

        int order = 2 * halforder + 1;
        double nu = 2d * cutoff / samplingRate; // normalized frequency

        double[] c = new double[order];
        c[halforder] = 1 - nu;

        for (int i = 0, n = halforder; i < halforder; i++, n--) {
            double npi = n * Math.PI;
            c[i] = -Math.sin(npi * nu) / npi;
            c[n + halforder] = c[i];
        }

        return c;
    }

    /// <summary>
    /// Calculates FIR Bandpass Filter Coefficients.
    /// </summary>
    /// <param name="samplingRate">Samples per unit.</param>
    /// <param name="cutoffLow">Low Cutoff frequency in samples per unit.</param>
    /// <param name="cutoffHigh">High Cutoff frequency in samples per unit.</param>
    /// <param name="halforder">half-order Q, so that Order N = 2*Q+1. 0 for default order.</param>
    /// <returns>The calculated filter coefficients.</returns>
    private static double[] BandPass(double samplingRate, double cutoffLow, double cutoffHigh, int halforder /*= 0*/) {
        // Default filter order
        if (halforder == 0) {
            double TRANSWINDRATIO = 0.25;
            double maxDf = (cutoffLow < samplingRate / 2 - cutoffHigh) ? cutoffLow : samplingRate / 2 - cutoffHigh;
            double df = (cutoffLow * TRANSWINDRATIO > 2) ? cutoffLow * TRANSWINDRATIO : 2;
            df = (df < maxDf) ? df : maxDf;
            halforder = (int) Math.ceil(3.3 / (df / samplingRate) / 2);
        }

        int order = 2 * halforder + 1;
        double nu1 = 2d * cutoffLow / samplingRate; // normalized low frequency
        double nu2 = 2d * cutoffHigh / samplingRate; // normalized high frequency

        double[] c = new double[order];
        c[halforder] = nu2 - nu1;

        for (int i = 0, n = halforder; i < halforder; i++, n--) {
            double npi = n * Math.PI;
            c[i] = (Math.sin(npi * nu2) - Math.sin(npi * nu1)) / npi;
            c[n + halforder] = c[i];
        }

        return c;
    }


    /// <summary>
    /// Blackman-Harris window.
    /// </summary>
    private static double[] BlackmanHarris(int width)
    {
            double a = 0.35875;
            double b = -0.48829;
            double c = 0.14128;
            double d = -0.01168;

        int last = width - 1;
        double e = 2.0*Math.PI/last;
        double f = 2.0*e;
        double g = 3.0*e;

        double[] w = new double[width];
        for (int i = 0; i < w.length; i++)
        {
            w[i] = a
                    + b*Math.cos(e*i)
                    + c*Math.cos(f*i)
                    + d*Math.cos(g*i);
        }
        return w;
    }
}
