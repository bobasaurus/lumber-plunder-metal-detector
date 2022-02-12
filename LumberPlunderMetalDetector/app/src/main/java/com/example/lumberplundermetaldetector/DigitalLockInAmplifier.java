package com.example.lumberplundermetaldetector;


//import be.tarsos.dsp.filters.LowPassFS;

import org.apache.commons.math3.util.FastMath;

public class DigitalLockInAmplifier  {

    private IDataAvailableIQ dataAvailableIQ;

    //public delegate void ProcessedDataAvailableDelegate(double i, double q);

    private int sampleRate;
    private double frequency;
    private long decimFactor;
    //TODO: examine uint vs int, overflow
    private long decimCount = 0;
    private long nSample = 0;

    private OnlineFirFilter preDecimFilterI, preDecimFilterQ;
    private OnlineFirFilter postDecimFilterI, postDecimFilterQ;

    private FMDiscriminator fmDiscrim = new FMDiscriminator(100000);

    private ISampleCollector sampleCollectorPhase, sampleCollectorMag, sampleCollectorFreqDev;

    private final static double TwoPi = 2 * Math.PI;

    public double PhaseOffset;
    public double MagOffset;

    private OnlineFirFilter phaseFilter, magFilter;
    //private MathNet.Numerics.Statistics.MovingStatistics phaseStats = new MathNet.Numerics.Statistics.MovingStatistics(500);
    //private MathNet.Numerics.Statistics.MovingStatistics magStats = new MathNet.Numerics.Statistics.MovingStatistics(500);

    public DigitalLockInAmplifier(int sampleRate, double frequency, int decimFactor, double finalLPCutoffFrequency, ISampleCollector sampleCollectorPhase, ISampleCollector sampleCollectorMag, ISampleCollector sampleCollectorFreqDev)//, float referenceFrequency)
    {
        this.sampleRate = sampleRate;
        this.frequency = frequency;
        this.decimFactor = decimFactor;
        //if (sampleRate >= Int32.MaxValue) throw new Exception("Sample rate too high: " + sampleRate);
        this.sampleCollectorPhase = sampleCollectorPhase;
        this.sampleCollectorMag = sampleCollectorMag;
        this.sampleCollectorFreqDev = sampleCollectorFreqDev;

        //filter out frequencies more than half the post-decim sample rate before decimating for anti-aliasing
        double[] preDiscrimCoeffs = OnlineFirFilter.CalcLowpassFilter(
                sampleRate,
                (((double)sampleRate)/decimFactor) / 2,
                101);
        preDecimFilterI = new OnlineFirFilter(preDiscrimCoeffs);
        preDecimFilterQ = new OnlineFirFilter(preDiscrimCoeffs);

        //final filter after decimation, cutoff freq specified by caller
        double[] postDiscrimCoeffs = OnlineFirFilter.CalcLowpassFilter(
                ((double)sampleRate) / decimFactor,
                finalLPCutoffFrequency,
                1001);
        postDecimFilterI = new OnlineFirFilter(postDiscrimCoeffs);
        postDecimFilterQ = new OnlineFirFilter(postDiscrimCoeffs);

        phaseFilter = new OnlineFirFilter(OnlineFirFilter.CalcLowpassFilter(
                ((double)sampleRate) / decimFactor,
                10,
                1001));

        magFilter = new OnlineFirFilter(OnlineFirFilter.CalcLowpassFilter(
                ((double)sampleRate) / decimFactor,
                10,
                1001));

        PhaseOffset = 0;
        MagOffset = 0;
    }

    public void AddSample(double inputSample)//, float referenceInPhase, float referenceQuadrature)
    {
        double multiple = TwoPi * frequency / sampleRate;
        double gain = 1;
        double referenceInPhase = gain * Math.sin(nSample * multiple + PhaseOffset);
        double referenceQuadrature = gain * Math.cos(nSample * multiple + PhaseOffset);
        nSample++;

        double valueI = inputSample * referenceInPhase;
        double valueQ = inputSample * referenceQuadrature;
        valueI = preDecimFilterI.ProcessSample(valueI);
        valueQ = preDecimFilterQ.ProcessSample(valueQ);

        //TODO: does this screw up on overflow?
        if (decimCount++ % decimFactor == 0)
        {
            valueI = postDecimFilterI.ProcessSample(valueI);
            valueQ = postDecimFilterQ.ProcessSample(valueQ);

            //TODO: use fast atan2
            double phaseRad = FastMath.atan2(valueQ, valueI);//angle in radians
            double phaseDeg = 180.0 / Math.PI * phaseRad;
            //var phaseDegNormalized = phaseDeg;
            //while (phaseDegNormalized < 0) phaseDegNormalized += 360;

            double mag = Math.sqrt(valueI * valueI + valueQ * valueQ) - MagOffset;

            //double phaseSmooth = phaseFilter.Process((float)phaseDeg);
            //double magSmooth = magFilter.Process((float)mag);
            //phaseStats.Push(phaseSmooth);
            //magStats.Push(magSmooth);
            //phaseSmooth = phaseStats.Mean;
            //magSmooth = magStats.Mean;
            //var tid = (mag == 0) ? 0 : phaseSmooth / (magSmooth / 1_000_000);

            double freqDev = fmDiscrim.FMDiscrimAllenFast(valueI, valueQ);

            if (sampleCollectorFreqDev != null) sampleCollectorFreqDev.AddSample(freqDev);
            if (sampleCollectorPhase != null) sampleCollectorPhase.AddSample(phaseDeg);
            if (sampleCollectorMag != null) sampleCollectorMag.AddSample(mag);

            //ProcessedDataAvailable?.Invoke(valueI, valueQ);
            //dataAvailableIQ.DLIA_DataAvailableCallback();
        }
    }



}
