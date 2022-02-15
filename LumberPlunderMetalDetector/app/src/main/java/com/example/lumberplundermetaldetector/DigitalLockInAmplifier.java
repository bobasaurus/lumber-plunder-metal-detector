package com.example.lumberplundermetaldetector;


//import be.tarsos.dsp.filters.LowPassFS;

import org.apache.commons.math3.util.FastMath;

public class DigitalLockInAmplifier  {

    private IDataAvailableIQ dataAvailableIQ;

    //public delegate void ProcessedDataAvailableDelegate(double i, double q);

    private int sampleRate;
    private double frequency;
    //TODO: examine uint vs int, overflow
    private long nSample = 0;
    private long decimFactor1, decimFactor2;
    private long decimCount1 = 0;
    private long decimCount2 = 0;

    private OnlineFirFilter inputFilter;
    private OnlineFirFilter preDecimFilterI, preDecimFilterQ;
    private OnlineFirFilter preDecimFilter2I, preDecimFilter2Q;
    private OnlineFirFilter postDecimFilterI, postDecimFilterQ;

    //private FMDiscriminator fmDiscrim = new FMDiscriminator(100000);

    private ISampleCollector sampleCollectorPhase, sampleCollectorMag;

    private final static double TwoPi = 2 * FastMath.PI;

    public double PhaseOffset;
    public double MagOffset;

    //private OnlineFirFilter phaseFilter, magFilter;

    public DigitalLockInAmplifier(int sampleRate, double frequency, int decimFactor1, int decimFactor2, double postDecim2Cutoff, ISampleCollector sampleCollectorPhase, ISampleCollector sampleCollectorMag)//, float referenceFrequency)
    {
        this.sampleRate = sampleRate;
        this.frequency = frequency;
        this.decimFactor1 = decimFactor1;
        this.decimFactor2 = decimFactor2;
        //if (sampleRate >= Int32.MaxValue) throw new Exception("Sample rate too high: " + sampleRate);
        this.sampleCollectorPhase = sampleCollectorPhase;
        this.sampleCollectorMag = sampleCollectorMag;

        double inputFilterFreq = frequency + 4000;
        if (inputFilterFreq > (((double)sampleRate)/2)) inputFilterFreq = ((double)sampleRate)/2;
        inputFilter = new OnlineFirFilter(OnlineFirFilter.CalcLowpassFilter(
                sampleRate,
                inputFilterFreq,
                101));

        //filter out frequencies more than half the post-decim sample rate before decimating for anti-aliasing
        double[] preDiscrimCoeffs = OnlineFirFilter.CalcLowpassFilter(
                sampleRate,
                (((double)sampleRate)/decimFactor1) / 2,
                101);
        preDecimFilterI = new OnlineFirFilter(preDiscrimCoeffs);
        preDecimFilterQ = new OnlineFirFilter(preDiscrimCoeffs);

        double[] preDiscrimCoeffs2 = OnlineFirFilter.CalcLowpassFilter(
                sampleRate / decimFactor1,
                (((double)sampleRate)/decimFactor1/decimFactor2) / 2,
                101);
        preDecimFilter2I = new OnlineFirFilter(preDiscrimCoeffs2);
        preDecimFilter2Q = new OnlineFirFilter(preDiscrimCoeffs2);

        //final filter after decimation, cutoff freq specified by caller
        double[] postDiscrimCoeffs = OnlineFirFilter.CalcLowpassFilter(
                ((double)sampleRate) / decimFactor1 / decimFactor2,
                postDecim2Cutoff,
                201);
        postDecimFilterI = new OnlineFirFilter(postDiscrimCoeffs);
        postDecimFilterQ = new OnlineFirFilter(postDiscrimCoeffs);

        /*phaseFilter = new OnlineFirFilter(OnlineFirFilter.CalcLowpassFilter(
                ((double)sampleRate) / decimFactor,
                10,
                1001));

        magFilter = new OnlineFirFilter(OnlineFirFilter.CalcLowpassFilter(
                ((double)sampleRate) / decimFactor,
                10,
                1001));*/

        PhaseOffset = 0;
        MagOffset = 0;
    }

    public void AddSample(double inputSample)//, float referenceInPhase, float referenceQuadrature)
    {
        double multiple = TwoPi * frequency / sampleRate;
        double gain = 1;
        double referenceInPhase = gain * FastMath.sin(nSample * multiple + PhaseOffset);
        double referenceQuadrature = gain * FastMath.cos(nSample * multiple + PhaseOffset);
        nSample++;

        inputSample = inputFilter.ProcessSample(inputSample);

        double valueI = inputSample * referenceInPhase;
        double valueQ = inputSample * referenceQuadrature;
        valueI = preDecimFilterI.ProcessSample(valueI);
        valueQ = preDecimFilterQ.ProcessSample(valueQ);

        //TODO: does this screw up on overflow?
        if (decimCount1++ % decimFactor1 == 0)
        {
            valueI = preDecimFilter2I.ProcessSample(valueI);
            valueQ = preDecimFilter2Q.ProcessSample(valueQ);

            if (decimCount2++ % decimFactor2 == 0) {

                valueI = postDecimFilterI.ProcessSample(valueI);
                valueQ = postDecimFilterQ.ProcessSample(valueQ);

                //TODO: use fast atan2
                double phaseRad = FastMath.atan2(valueQ, valueI);//angle in radians
                double phaseDeg = 180.0 / FastMath.PI * phaseRad;
                //var phaseDegNormalized = phaseDeg;
                //while (phaseDegNormalized < 0) phaseDegNormalized += 360;

                double mag = FastMath.sqrt(valueI * valueI + valueQ * valueQ) - MagOffset;

                //double phaseSmooth = phaseFilter.Process((float)phaseDeg);
                //double magSmooth = magFilter.Process((float)mag);
                //phaseStats.Push(phaseSmooth);
                //magStats.Push(magSmooth);
                //phaseSmooth = phaseStats.Mean;
                //magSmooth = magStats.Mean;
                //var tid = (mag == 0) ? 0 : phaseSmooth / (magSmooth / 1_000_000);

                //double freqDev = fmDiscrim.FMDiscrimAllenFast(valueI, valueQ);

                //if (sampleCollectorFreqDev != null) sampleCollectorFreqDev.AddSample(freqDev);
                if (sampleCollectorPhase != null) sampleCollectorPhase.AddSample(phaseDeg);
                if (sampleCollectorMag != null) sampleCollectorMag.AddSample(mag);

                //ProcessedDataAvailable?.Invoke(valueI, valueQ);
                //dataAvailableIQ.DLIA_DataAvailableCallback();
            }
        }
    }



}
