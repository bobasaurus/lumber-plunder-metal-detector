package com.example.lumberplundermetaldetector;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;

import com.anychart.AnyChart;

import com.anychart.AnyChartView;
import com.anychart.chart.common.dataentry.DataEntry;
import com.anychart.chart.common.dataentry.ValueDataEntry;
import com.anychart.charts.Cartesian;
import com.anychart.charts.Pie;
import com.anychart.core.cartesian.series.Line;
import com.anychart.data.Mapping;
import com.anychart.data.Set;
import com.anychart.chart.common.dataentry.DataEntry;
import com.anychart.chart.common.dataentry.ValueDataEntry;
import com.anychart.enums.Anchor;
import com.anychart.enums.MarkerType;
import com.anychart.enums.SelectionMode;

//import com.github.mikephil.charting.charts.*;
//import com.github.mikephil.charting.components.Description;
//import com.github.mikephil.charting.data.Entry;
//import com.github.mikephil.charting.data.LineData;
//import com.github.mikephil.charting.data.LineDataSet;

import android.media.AudioRecord;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import uk.me.berndporr.kiss_fft.KISSFastFourierTransformer;
import org.apache.commons.math3.complex.Complex;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MainActivity extends AppCompatActivity {
    //private TextView textView;
    private Button buttonStart;
    private AnyChartView fftAnyChartView;
    private Cartesian fftCartesianChart;
    private List<DataEntry> fftDataEntryList;
    private Line fftLineSeries;

    //private LineChart fftChart, phaseChart, freqMagChart;
    //private LinkedList<Entry> phaseEntryList, magEntryList;
    //private LineData fftLineData, phaseLineData, magLineData;
    private int phaseIndex = 0;
    //private int magIndex = 0;
    //private final int PHASE_GRAPH_POINTS = 1000;
    //private final int MAG_GRAPH_POINTS = 1000;


    private boolean threadQuit = false;
    private Thread thread;
    private ReentrantLock reentrantLockFFT, reentrantLockPhase, reentrantLockMag;

    private AudioRecord audioRecord;
    private static final int SAMPLE_RATE = 6000;//192000 is the max on my pixel 3a

    private AudioTrack audioTrack;
    private static final double TX_FREQ_HZ = 1000;
    private static final int SAMPLE_RATE_TX = 44100;
    private short txBuffer[];// = new short[SAMPLE_RATE_TX];
    private boolean isPlaying = false;

    private KISSFastFourierTransformer kfft;
    private static final int FFT_SIZE = 2048;
    private int fftBufferIndex = 0;
    private double[] bufferForFFT = new double[FFT_SIZE];

    private DigitalLockInAmplifier dlia;

    private ISampleCollector phaseCollector, magCollector, freqCollector;

    // create a pure tone of the given frequency for the given duration
    public static double[] tone(double hz, double duration)
    {
        int n = (int) (SAMPLE_RATE_TX * duration);
        double[] a = new double[n+1];
        for (int i = 0; i <= n; i++)
        {
            a[i] = Math.sin(2 * Math.PI * i * hz / SAMPLE_RATE_TX);
        }
        return a;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {


            //textView = findViewById(R.id.textViewBlah);

            fftAnyChartView = findViewById(R.id.fftChart);

            fftCartesianChart = AnyChart.line();
            fftCartesianChart.animation(false);
            fftCartesianChart.title("weoo");
            fftCartesianChart.interactivity().selectionMode("none");

            fftDataEntryList = new ArrayList<>();
            fftDataEntryList.add(new ValueDataEntry(phaseIndex++, 5));//can't be empty
            fftLineSeries = fftCartesianChart.line(fftDataEntryList);
            fftLineSeries.tooltip().enabled(false);
            fftLineSeries.selectionMode("none");

            fftAnyChartView.setChart(fftCartesianChart);

            // simulate real-time update
            /*final int delayMillis = 500;
            final Handler handler = new Handler();
            final Runnable runnable = new Runnable() {
                public void run() {
// create new data List
                    //List<DataEntry> data = new ArrayList<>();
                    fftDataEntryList.add(new ValueDataEntry(phaseIndex++, new Random().nextDouble() * 10));
                    // apply new data to the series
                    fftLineSeries.data(fftDataEntryList);

                    handler.postDelayed(this, delayMillis);
                }
            };
            handler.postDelayed(runnable, delayMillis);*/







            //fftChart = findViewById(R.id.fftChart);
            //phaseChart = findViewById(R.id.phaseChart);
            //freqMagChart = findViewById(R.id.freqMagChart);

            //request audio recording permissions in a loop until granted
            while (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            }

            //setup fft
            kfft = new KISSFastFourierTransformer();

            //setup audio output
            {
                int duration = 10; // duration of sound [sec]
                int numSamples = duration * SAMPLE_RATE_TX; //[sec] * [samples/sec] = [samples]

                txBuffer = new short[numSamples];
                double timeStep = 1.0 / SAMPLE_RATE_TX;

                double time = 0;
                for (int i = 0; i < numSamples; i++) {
                    double sample = Math.sin(2.0 * Math.PI * TX_FREQ_HZ * time);//Math.sin(2.0 * Math.PI * i / (SAMPLE_RATE_TX)); // Sine wave
                    txBuffer[i] = (short) (sample * Short.MAX_VALUE);  // Higher amplitude increases volume
                    time += timeStep;
                }
            }

            //todo: try using audioTrack.setNotificationMarkerPosition to track exact transmit position/phase for dlia sync?
            //https://stackoverflow.com/a/6655260/268399
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE_TX, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, txBuffer.length,
                    AudioTrack.MODE_STATIC);

            int loopSuccess = audioTrack.setLoopPoints(0, txBuffer.length / 2, -1);
            if (loopSuccess != AudioTrack.SUCCESS) {
                SetErrorMessage("Could not start loop, error value: " + loopSuccess);
            }

            audioTrack.write(txBuffer, 0, txBuffer.length);

            buttonStart = (Button) findViewById(R.id.startStopButton);
            buttonStart.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    if (isPlaying) {
                        audioTrack.stop();
                        isPlaying = false;
                    } else {
                        audioTrack.play();
                        isPlaying = true;
                    }
                }
            });


            //setup audio recording
            int audioChannelConfig = AudioFormat.CHANNEL_IN_MONO;
            int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
            int bufferSizeInBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, audioChannelConfig, audioEncoding);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, audioChannelConfig, audioEncoding, bufferSizeInBytes);


            //setup fft graph
            //fftLineData = new LineData();
            //fftChart.setData(fftLineData);
            /*fftChart.getXAxis().setTextColor(Color.GREEN);
            fftChart.getAxisLeft().setTextColor(Color.GREEN);
            fftChart.getAxisRight().setTextColor(Color.GREEN);
            fftChart.getLegend().setTextColor(Color.GREEN);
            fftChart.getLegend().setEnabled(false);
            Description des = new Description();
            des.setText("mic fft power, FFT_SIZE=" + FFT_SIZE + " SAMP_RATE=" + SAMPLE_RATE);
            des.setTextColor(Color.YELLOW);
            fftChart.setDescription(des);
            fftChart.invalidate();*/

            //setup phase graph
            //LineDataSet phaseLDS = new LineDataSet(new ArrayList<Entry>(), "phase");
            //phaseLDS.setColor(Color.RED);
            //phaseLDS.setDrawValues(false);
            //phaseLineData = new LineData(phaseLDS);
            //phaseChart.setData(phaseLineData);
            //phaseChart.invalidate();

            //setup mag graph
            //LineDataSet magLDS = new LineDataSet(new ArrayList<Entry>(), "mag");
            //magLDS.setColor(Color.BLUE);
            //magLDS.setDrawValues(false);
            //magLineData = new LineData(magLDS);
            //freqMagChart.setData(magLineData);
            //freqMagChart.invalidate();



            //setup the digital lock-in amplifier
            phaseCollector = new ISampleCollector() {
                @Override
                public void AddSample(double sample) {

                    //fftDataEntryList.add(new ValueDataEntry(phaseIndex++, sample));
                    //if (fftDataEntryList.size() > 20) fftDataEntryList.remove(0);

                    //fftLineSeries.data(fftDataEntryList);




                        /*phaseChart.post(new Runnable() {
                            @Override
                            public void run() {
                                phaseEntryList.add(new Entry(phaseIndex++, (float)sample));
                                if (phaseEntryList.size() > PHASE_GRAPH_POINTS) phaseEntryList.removeFirst();

                                //ArrayList<Entry> dataCopy = new ArrayList<Entry>();
                                //for (Entry entry : phaseEntryList) dataCopy.add(new Entry(entry.getX(), entry.getY()));

                                phaseChart.clear();
                                phaseChart.setData(new LineData(new LineDataSet(phaseEntryList, "phase")));
                                phaseChart.setAutoScaleMinMaxEnabled(true);
                                phaseChart.postInvalidate();
                            }
                        });

                        //AddScrollingChartData(phaseLineData, reentrantLockPhase, PHASE_GRAPH_POINTS, phaseIndex++, (float) sample);
                        //UpdateChartThreadSafe(phaseChart, reentrantLockPhase, true);

                    }
                    catch (Exception ex)
                    {
                        SetErrorMessage("phase processing error: " + ex.getMessage());
                    }*/
                }
            };
            magCollector = new ISampleCollector() {
                @Override
                public void AddSample(double sample) {
                }
            };
            freqCollector = new ISampleCollector() {
                @Override
                public void AddSample(double sample) {
                    //ignoring for now
                }
            };
            dlia = new DigitalLockInAmplifier(SAMPLE_RATE, TX_FREQ_HZ, 4, 50,
                    phaseCollector, magCollector, freqCollector);

            //setup worker thread for polling/collecting audio recording bytes
            //todo: exception handling
            reentrantLockFFT = new ReentrantLock();
            reentrantLockPhase = new ReentrantLock();
            reentrantLockMag = new ReentrantLock();

            //reentrantLock.unlock();
            thread = new Thread(new Runnable() {
                public void run() {
                    try {
                        //audioRecord.registerAudioRecordingCallback();
                        short[] buffer = new short[bufferSizeInBytes / 4];
                        audioRecord.startRecording();

                        while (!threadQuit) {
                            int readResult = audioRecord.read(buffer, 0, bufferSizeInBytes / 4);
                            if (readResult == AudioRecord.ERROR_INVALID_OPERATION) {
                                SetErrorMessage("Invalid operation in thread");
                            } else if (readResult == AudioRecord.ERROR_BAD_VALUE) {
                                SetErrorMessage("Bad value in thread");
                            } else if (readResult == AudioRecord.ERROR_DEAD_OBJECT) {
                                SetErrorMessage("Dead object in thread");
                            } else if (readResult == AudioRecord.ERROR) {
                                SetErrorMessage("General audio error in thread");
                            } else {
                                for (int sampleNum = 0; sampleNum < readResult; sampleNum++) {
                                    ProcessMicSample(buffer[sampleNum]);
                                }
                            }
                        }
                    } catch (Exception ex) {
                        SetErrorMessage("thread error: " + ex.getMessage());
                    }
                }
            });

            thread.start();
        }
        catch (Exception ex){
            //todo: better error/status logging
            SetErrorMessage("Init error: " + ex.getMessage());
        }
    }

    private void ProcessMicSample(short sample) throws InterruptedException {
        dlia.AddSample(sample);

        if (fftBufferIndex < FFT_SIZE){
            bufferForFFT[fftBufferIndex++] = sample;
        }
        else {

            fftBufferIndex = 0;

            Complex[] fftResult = kfft.transform(bufferForFFT);


                //ld.clearValues();

                ArrayList<DataEntry> newEntryList = new ArrayList<DataEntry>();

                //int outputSpectrumIndex = 0;
                double CarrierFrequencykHz = 0;
                //add the negative frequencies (relative to the carrier) to the plot
                for (int fftindex = (fftResult.length / 2 + 1); fftindex < fftResult.length; fftindex++) {
                    double frequencykHz = (fftindex - FFT_SIZE) * ((double) SAMPLE_RATE) / FFT_SIZE / 1000; //((double)fftindex - fftResult.length) / (fftResult.length / 2) * ((SAMPLE_RATE / 2) / 1e3);
                    frequencykHz += CarrierFrequencykHz;// * 1e6;

                    double power = (fftResult[fftindex].getReal() * fftResult[fftindex].getReal() + fftResult[fftindex].getImaginary() * fftResult[fftindex].getImaginary());
                    double amplitude = Math.sqrt(power);
                    double db = 10 * Math.log10(amplitude);

                    //ld.getDataSetByIndex(0).getEntryForIndex(outputSpectrumIndex).setY((float)db);
                    newEntryList.add(new ValueDataEntry(frequencykHz, db));//new Entry((float) frequencykHz, (float) db));

                    //outputSpectrumIndex++;
                }

                //add the positive frequencies (relative to the carrier) to the plot
                for (int fftindex = 0; fftindex <= fftResult.length / 2; fftindex++) {
                    double frequencykHz = fftindex * ((double) SAMPLE_RATE) / FFT_SIZE / 1000;//((double)fftindex) / (fftResult.length / 2) * ((SAMPLE_RATE / 2) / 1e3);
                    frequencykHz += CarrierFrequencykHz;// * 1e6;

                    //need to do peak detection off this raw power value if the ratio is going to work right according to dad
                    double power = (fftResult[fftindex].getReal() * fftResult[fftindex].getReal() + fftResult[fftindex].getImaginary() * fftResult[fftindex].getImaginary());
                    double amplitude = Math.sqrt(power);
                    double db = 10 * Math.log10(amplitude);

                    //ld.getDataSetByIndex(0).getEntryForIndex(outputSpectrumIndex).setY((float)db);
                    newEntryList.add(new ValueDataEntry(frequencykHz, db));//new Entry((float) frequencykHz, (float) db));

                    //outputSpectrumIndex++;
                }

                /*fftAnyChartView.post(new Runnable() {
                    @Override
                    public void run() {
                        fftLineSeries.data(newEntryList);
                    }
                });*/

                fftLineSeries.data(newEntryList);


            /*if (reentrantLockFFT.tryLock(1000, TimeUnit.MILLISECONDS)) {
                try {
                    fftLineData.removeDataSet(0);
                    LineDataSet lds = new LineDataSet(newEntryList, "blah");
                    fftLineData.addDataSet(lds);
                } finally {
                    reentrantLockFFT.unlock();
                }
            }*/

            //UpdateChartThreadSafe(fftChart, reentrantLockFFT, true);
        }
    }

    /*private void AddScrollingChartData(LineData lineData,Lock lock, int maxPoints, float xValue, float yValue) throws InterruptedException {

        lineData.addEntry(new Entry(xValue, yValue), 0);
        if (lineData.getEntryCount() > maxPoints)
            lineData.getDataSetByIndex(0).removeFirst();

        if (lock.tryLock(1000, TimeUnit.MILLISECONDS)) {
            try {
                lineData.addEntry(new Entry(xValue, yValue), 0);
                if (lineData.getEntryCount() > maxPoints)
                    lineData.getDataSetByIndex(0).removeFirst();
            } finally {
                lock.unlock();
            }
        }
    }*/

    /*private void UpdateChartThreadSafe(LineChart chart, Lock lock, boolean autoScale)
    {
        this.runOnUiThread(new Runnable() {
                               @Override
                               public void run() {
                                   try {

                                       chart.setAutoScaleMinMaxEnabled(autoScale);
                                       chart.notifyDataSetChanged();
                                       chart.invalidate();
                                   }
                                   catch (Exception ex){
                                       SetErrorMessage(ex.getMessage());
                                   }
                               }
                           });

        chart.post(new Runnable() {
            @Override
            public void run() {
                chart.setAutoScaleMinMaxEnabled(autoScale);
                chart.notifyDataSetChanged();
                chart.invalidate();
            }
        });

        //thread-safe way to update the graph
        chart.post(new Runnable() {
            public void run() {

                try {
                    if (lock.tryLock(1000, TimeUnit.MILLISECONDS)) {
                        try {
                            chart.setAutoScaleMinMaxEnabled(autoScale);
                            chart.notifyDataSetChanged();//note: needs thread sync, the dataset can be updated before the graph renders causing array index exceptions
                            chart.invalidate();
                        } finally {
                            lock.unlock();
                        }
                    }
                }
                catch (InterruptedException ex){
                    SetErrorMessage("lock interrupted during chart update: " + ex.getMessage());
                }
                catch (Exception ex){
                    SetErrorMessage(ex.getMessage());
                }
            }
        });
    }*/

    private void SetErrorMessage(String message)
    {
        //textView.post(new Runnable() {public void run() {textView.setText("Err: " + message);}});
    }

    private void SetStatusMessage(String message)
    {
        //textView.post(new Runnable() {public void run() {textView.setText(message);}});
    }
}