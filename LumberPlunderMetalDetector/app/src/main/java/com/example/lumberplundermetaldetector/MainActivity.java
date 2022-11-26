package com.example.lumberplundermetaldetector;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;

import android.media.AudioRecord;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.xy.*;
import com.androidplot.util.Redrawer;

import java.io.File;
import java.io.FileWriter;
import java.lang.ref.WeakReference;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    //files stored to this directory on the android file system:
    ///storage/emulated/0/Android/data/com.example.lumberplundermetaldetector/files
    //which is this directory when connected to windows:
    //This PC\Pixel 3a\Internal shared storage\Android\data\com.example.lumberplundermetaldetector\files
    private File externalFilesDir;

    private AudioManager audioManager;

    private XYPlot amplitudePlot;
    private Redrawer amplitudeRedrawer;
    private SimpleXYSeries amplitudeSeries, fixedPhaseSeries;
    
    private XYPlot metalSignalPlot;
    private Redrawer metalSignalRedrawer;
    private RollingXYSeries metalSignalSeries;

    private Button buttonStart;
    private Button buttonRecord;
    private ProgressBar progressBarMicAmp;
    private SeekBar seekBarPhaseOffset;
    private SeekBar seekBarGroundBalance;
    private TextView textViewStatus, textViewAdjustments;

    private boolean threadQuit = false;
    private Thread rxThread;

    private Timer volumeCheckTimer;

    private static final int SAMPLE_RATE_RX = 192000;//192000 is the max on my pixel 3a
    private AudioRecord audioRecord;
    private int rxBufferSizeInBytes;

    //TRANSMITTER SETUP
    private static final int SAMPLE_RATE_TX = 192000;
    private static final double TX_FREQ_HZ = 16000;
    private static final int ONE_PERIOD_SAMPLES = (int)Math.round(1.0/TX_FREQ_HZ * SAMPLE_RATE_TX);
    private static final int QUARTER_PERIOD_SAMPLES = (int)Math.round(1.0/TX_FREQ_HZ * SAMPLE_RATE_TX / 4);
    private static final double TWO_PI = Math.PI * 2;
    private static final int GRAPH_LENGTH = 10000;//ONE_PERIOD_SAMPLES * 4;
    private AudioTrack audioTrack;
    private Thread txThread;
    private /*volatile*/ boolean txDone = false;
    private double desiredPhaseOffsetRad = 0;//just for display purposes
    private /*volatile*/ int desiredPhaseOffsetInCounts = 0;

    private double desiredGroundBalanceFactor = 0;

    //transmitter thread
    private Runnable transmitThreadRunnable = new Runnable() {
        @Override
        public void run() {
            short[] highResTxSamples = SinewaveGenerator.GenerateSinewave(SAMPLE_RATE_TX*100, TX_FREQ_HZ, Short.MAX_VALUE, 0, 1);
            int highResTxIncrement = 100;
            int phaseOffsetInCounts = 0;
            int highResTxIndex = 0;

            short[] txBuffer = new short[ONE_PERIOD_SAMPLES];
            //setup the initial tx array with zero phase offset
            for (int i = 0; i < txBuffer.length; i++) {
                txBuffer[i] = highResTxSamples[highResTxIndex];
                highResTxIndex = (highResTxIndex + highResTxIncrement) % highResTxSamples.length;
            }

            //the amount added to the current phase each cycle until it reaches the desired phase
            //TODO: acceleration?
            int phaseVelocity = 200;

            while (!txDone) {
                audioTrack.write(txBuffer, 0, txBuffer.length, AudioTrack.WRITE_BLOCKING);

                //TODO: lock the method var and make local backup
                if (phaseOffsetInCounts != desiredPhaseOffsetInCounts) {

                    if (phaseOffsetInCounts < desiredPhaseOffsetInCounts) {
                        phaseOffsetInCounts += phaseVelocity;
                        if (phaseOffsetInCounts > desiredPhaseOffsetInCounts)
                            phaseOffsetInCounts = desiredPhaseOffsetInCounts;
                    }
                    if (phaseOffsetInCounts > desiredPhaseOffsetInCounts) {
                        phaseOffsetInCounts -= phaseVelocity;
                        if (phaseOffsetInCounts < desiredPhaseOffsetInCounts)
                            phaseOffsetInCounts = desiredPhaseOffsetInCounts;
                    }
                    if (phaseOffsetInCounts < 0) phaseOffsetInCounts = 0;
                    highResTxIndex = phaseOffsetInCounts;
                    for (int i = 0; i < txBuffer.length; i++) {
                        txBuffer[i] = highResTxSamples[highResTxIndex];
                        highResTxIndex = (highResTxIndex + highResTxIncrement) % highResTxSamples.length;
                    }
                }
            }
        }
    };

    private boolean isRecording = false;

    private Short[] recordBuffer;
    private int recordCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            //request audio recording permissions in a loop until granted
            while (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            }

            progressBarMicAmp = findViewById(R.id.progressBarMicAmp);
            textViewStatus = findViewById(R.id.textViewStatus);
            textViewStatus.setText("Press Start to begin ->");
            textViewAdjustments = findViewById(R.id.textViewAdjustments);

            seekBarPhaseOffset = findViewById(R.id.seekBarPhaseOffset);
            seekBarPhaseOffset.setMin(0);
            seekBarPhaseOffset.setMax((int) (TWO_PI * 100));//[radians * 100]
            //GUI phase offset adjustment control
            seekBarPhaseOffset.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                //private long prevTime = System.currentTimeMillis();
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    desiredPhaseOffsetRad = ((double) progress) / 100;
                    //TODO: the rounding is kind of unnecessary here, maybe remove for speed
                    desiredPhaseOffsetInCounts = (int)/*Math.round*/(desiredPhaseOffsetRad / TWO_PI * (ONE_PERIOD_SAMPLES * 100));


                    /*short[] fixedPhaseWave = SinewaveGenerator.GenerateSinewave(SAMPLE_RATE_TX, TX_FREQ_HZ, Short.MAX_VALUE, desiredPhaseOffsetRad, 1);
                    ArrayList<Short> fixedPhaseList = new ArrayList<>();
                    for (short t : fixedPhaseWave) fixedPhaseList.add(t);
                    fixedPhaseSeries.setModel(fixedPhaseList, SimpleXYSeries.ArrayFormat.Y_VALS_ONLY);*/

                    //TODO: maybe only update occasionally for speed?
                    textViewAdjustments.setText(String.format("Phase Off: %.2f rad, GB Factor: %.2f", desiredPhaseOffsetRad, desiredGroundBalanceFactor));
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });

            seekBarGroundBalance = findViewById(R.id.seekBarGroundBalance);
            seekBarGroundBalance.setMin(-300);
            seekBarGroundBalance.setMax(300);//[radians * 100]
            seekBarGroundBalance.setProgress(0);
            //GUI phase offset adjustment control
            seekBarGroundBalance.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                //private long prevTime = System.currentTimeMillis();
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    desiredGroundBalanceFactor = ((double)progress)/100;

                    textViewAdjustments.setText(String.format("Phase Off: %.2f rad, GB Factor: %.2f", desiredPhaseOffsetRad, desiredGroundBalanceFactor));
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });

            externalFilesDir = this.getExternalFilesDir(null);

            //check that the 3.5mm plug from the metal detector is plugged in
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            Boolean wiredOn = audioManager.isWiredHeadsetOn();
            if (!wiredOn) SetErrorMessage("Need to plug in metal detector cable");
            //audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);

            //setup the amplitude plot
            {
                amplitudePlot = findViewById(R.id.amplitudePlot);
                amplitudeSeries = new SimpleXYSeries("mic adc");//GRAPH_LENGTH, new WeakReference<>(amplitudePlot.getRenderer(FastLineAndPointRenderer.class)));

                short[] fixedPhaseWave = SinewaveGenerator.GenerateSinewave(SAMPLE_RATE_TX, TX_FREQ_HZ, 30000, 0, 1);
                ArrayList<Short> fixedPhaseList = new ArrayList<>();
                for (short t : fixedPhaseWave) fixedPhaseList.add(t);
                if (fixedPhaseWave.length != ONE_PERIOD_SAMPLES) throw new Exception("period sample count mismatch");
                fixedPhaseSeries = new SimpleXYSeries("ref phase");
                fixedPhaseSeries.setModel(fixedPhaseList, SimpleXYSeries.ArrayFormat.Y_VALS_ONLY);
                FastLineAndPointRenderer.Formatter fixedPhaseFormat = new FastLineAndPointRenderer.Formatter(Color.RED, Color.RED, null);
                fixedPhaseFormat.setPointLabeler(null);

                FastLineAndPointRenderer.Formatter series1Format = new FastLineAndPointRenderer.Formatter(Color.BLUE, Color.BLUE, null);
                //LineAndPointFormatter series1Format = new LineAndPointFormatter(this, R.xml.line_point_formatter_with_labels);
                series1Format.setPointLabeler(null);
                //series1Format.setVertexPaint(null);
                amplitudePlot.addSeries(fixedPhaseSeries, fixedPhaseFormat);
                amplitudePlot.addSeries(amplitudeSeries, series1Format);
                //amplitudePlot.setDomainBoundaries(0, GRAPH_LENGTH, BoundaryMode.FIXED);
                amplitudePlot.getLegend().setVisible(true);
                amplitudePlot.getGraph().setMarginLeft(75);


                //integer x/y axis label formatting
                amplitudePlot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).setFormat(new Format() {
                    @Override
                    public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
                        int i = Math.round(((Number) obj).floatValue());
                        return toAppendTo.append(i);
                    }

                    @Override
                    public Object parseObject(String source, ParsePosition pos) {
                        return null;
                    }
                });

                amplitudePlot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.LEFT).setFormat(new Format() {
                    @Override
                    public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
                        int i = Math.round(((Number) obj).floatValue());
                        return toAppendTo.append(i);
                    }

                    @Override
                    public Object parseObject(String source, ParsePosition pos) {
                        return null;
                    }
                });

                //NOTE: doesn't seem to be necessary to explicitly call this, oddly
                //Add the draw start/finish listener, which is the custom series I made.
                //This handles the synchronization locks while drawing the plot.
                //amplitudePlot.addListener(amplitudeSeries);

                //use a redrawer to update/refresh/redraw the graph at a set framerate
                amplitudeRedrawer = new Redrawer(amplitudePlot, 10, true);
            }

            //setup the metal signal plot
            {

                metalSignalPlot = findViewById(R.id.metalSignalPlot);
                metalSignalSeries = new RollingXYSeries(GRAPH_LENGTH, new WeakReference<>(metalSignalPlot.getRenderer(FastLineAndPointRenderer.class)));

                FastLineAndPointRenderer.Formatter series1Format = new FastLineAndPointRenderer.Formatter(Color.BLUE, Color.BLUE, null);
                //LineAndPointFormatter series1Format = new LineAndPointFormatter(this, R.xml.line_point_formatter_with_labels);
                series1Format.setPointLabeler(null);
                series1Format.setVertexPaint(null);
                metalSignalPlot.addSeries(metalSignalSeries, series1Format);
                //metalSignalPlot.setDomainBoundaries(0, GRAPH_LENGTH, BoundaryMode.FIXED);
                metalSignalPlot.getLegend().setVisible(false);
                metalSignalPlot.getGraph().setMarginLeft(75);


                //integer x/y axis label formatting
                metalSignalPlot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).setFormat(new Format() {
                    @Override
                    public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
                        int i = Math.round(((Number) obj).floatValue());
                        return toAppendTo.append(i);
                    }

                    @Override
                    public Object parseObject(String source, ParsePosition pos) {
                        return null;
                    }
                });

                metalSignalPlot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.LEFT).setFormat(new Format() {
                    @Override
                    public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
                        int i = Math.round(((Number) obj).floatValue());
                        return toAppendTo.append(i);
                    }

                    @Override
                    public Object parseObject(String source, ParsePosition pos) {
                        return null;
                    }
                });

                //NOTE: doesn't seem to be necessary to explicitly call this, oddly
                //Add the draw start/finish listener, which is the custom series I made.
                //This handles the synchronization locks while drawing the plot.
                //metalSignalPlot.addListener(metalSignalSeries);

                //use a redrawer to update/refresh/redraw the graph at a set framerate
                metalSignalRedrawer = new Redrawer(metalSignalPlot, 15, true);
            }



            //set up the audio transmitter
            //todo: try using audioTrack.setNotificationMarkerPosition to track exact transmit position/phase for sync?
            //https://stackoverflow.com/a/6655260/268399
            //TODO: a small buffer seems to work here, but try increasing it
            int txBufferSizeInBytes = AudioTrack.getMinBufferSize(SAMPLE_RATE_TX, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            audioTrack = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE_TX)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .setTransferMode(AudioTrack.MODE_STREAM)//or static?
                    .setBufferSizeInBytes(txBufferSizeInBytes)
                    .build();
            audioTrack.play();

            //start the tx thread
            txThread = new Thread(transmitThreadRunnable);
            txThread.start();

            buttonRecord = findViewById(R.id.recordButton);
            buttonRecord.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isRecording) return;
                    isRecording = true;
                }
            });


            //setup audio recording
            int audioChannelConfig = AudioFormat.CHANNEL_IN_MONO;
            int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
            //just using the min buffer size causes occasional blips at lower sample rates, or regular ~40 ms blips at higher sample rates
            //for 192k, only buffers > min*80 seem to remove most of the blips, but this causes a long time delay on the graphs
            //maybe stick with lower sample rates if possible

            //todo: maybe try using OpenSL to stream audio data more smoothly? (google "low latency" audio recording for android)
            //https://developer.android.com/ndk/guides/audio/opensl/opensl-for-android
            //other latency topics:
            //https://stackoverflow.com/search?q=android+audiorecord+latency
            //https://developer.android.com/ndk/guides/audio/audio-latency

            //need bigger than the min buffer size to avoid blips on missed samples (from garbage collection, system events, etc delaying collection)
            //min buffer size for 192000 sps = 15352, smallest buffer without lots of missed sample blips = 15352 * 100 = 4 seconds!!!
			//todo: should this be a method var?  I don't think it matters
            //int rxBufferSizeInBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE_RX, audioChannelConfig, audioEncoding) * 50;
            //audioRecord = new AudioRecord(MediaRecorder.AudioSource.UNPROCESSED, SAMPLE_RATE_RX, audioChannelConfig, audioEncoding, bufferSizeInBytes);
            rxBufferSizeInBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE_RX, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 50;
            audioRecord = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.UNPROCESSED)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE_TX)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build())
                    .setBufferSizeInBytes(rxBufferSizeInBytes)
                    .build();

            //todo: exception handling
            //reentrantLockFFT = new ReentrantLock();

            //setup worker thread for collecting audio recording bytes
            rxThread = new Thread(new Runnable() {
                public void run() {
                    try {

                        int windowNumPeriods = 8;

                        OnlineFirFilter filterR = new OnlineFirFilter(OnlineFirFilter.CalcLowpassFilter(SAMPLE_RATE_RX / (ONE_PERIOD_SAMPLES*windowNumPeriods), 50, 401));
                        OnlineFirFilter filterX = new OnlineFirFilter(OnlineFirFilter.CalcLowpassFilter(SAMPLE_RATE_RX / (ONE_PERIOD_SAMPLES*windowNumPeriods), 50, 401));

                        FileWriter fileWriter = null;
                        //audioRecord.registerAudioRecordingCallback();
                        short[] buffer = new short[rxBufferSizeInBytes / 2];
                        audioRecord.startRecording();

                        short[] delayArr = new short[ONE_PERIOD_SAMPLES*windowNumPeriods];
                        int delayArrCount = 0;

                        while (!threadQuit) {
                            //using a smaller read size than the buffer size to avoid latency issues with big buffers (and big buffers are required to avoid garbage collector missed data blips)
                            //https://stackoverflow.com/a/39230480/268399
                            //note: I noticed a gradual latency increase when reading the full buffer at once, now I just read a small quantity to keep up with processing
                            int readResult = audioRecord.read(buffer, 0, 512);//rxBufferSizeInBytes / 2);
                            if (readResult == AudioRecord.ERROR_INVALID_OPERATION) {
                                //removed the error reporting here temporarily since it may be causing occasional blips/delays when catching up on reading audio data, needs more testing
                                //SetErrorMessage("Invalid operation in thread");
                            } else if (readResult == AudioRecord.ERROR_BAD_VALUE) {
                                //SetErrorMessage("Bad value in thread");
                            } else if (readResult == AudioRecord.ERROR_DEAD_OBJECT) {
                                //SetErrorMessage("Dead object in thread");
                            } else if (readResult == AudioRecord.ERROR) {
                                //SetErrorMessage("General audio error in thread");
                            } else {
                                for (int sampleNum = 0; sampleNum < readResult; sampleNum++) {
                                    short sample = buffer[sampleNum];
                                    if (isRecording) {
                                        if (fileWriter == null) {

                                            SetStatusMessage("Starting to record...");

                                            int volumeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);


                                            SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd HH_mm_ss z");
                                            Date date = new Date(System.currentTimeMillis());
                                            String filename = formatter.format(date) + String.format(" %d SPS %.1f Hz %d vol", SAMPLE_RATE_RX, TX_FREQ_HZ, volumeLevel) + ".txt";

                                            File file = new File(externalFilesDir, filename);

                                            fileWriter = new FileWriter(file);

                                            recordBuffer = new Short[SAMPLE_RATE_RX * 4];
                                            recordCount = 0;

                                            //clear the buffer
                                            audioRecord.read(buffer, 0, buffer.length);
                                        }

                                        //note: phone can't keep up with writing samples this fast to disk, use a buffer instead (recording for 10 seconds only stores 4 sec worth of data right now, the rest is lagged in the audio buffer)
                                        //fileWriter.write(String.format("%d\r\n", buffer[sampleNum]));
                                        recordBuffer[recordCount] = sample;
                                        recordCount++;
                                        if (recordCount >= recordBuffer.length) isRecording = false;
                                    } else {
                                        if (fileWriter != null) {

                                            SetStatusMessage("Writing file...");

                                            for (int i=0; i<recordBuffer.length; i++)
                                                fileWriter.write(String.format("%d\r\n", recordBuffer[i]));

                                            //fileWriter.flush();
                                            fileWriter.close();
                                            fileWriter = null;
                                            //SetStatusMessage("Wrote file");

                                            recordCount = 0;
                                            isRecording = false;
                                            recordBuffer = null;//try to trigger the gc here rather than at the start

                                            SetStatusMessage("Finished writing file");

                                            //clear the buffer
                                            audioRecord.read(buffer, 0, buffer.length);
                                        }

                                        delayArr[delayArrCount] = sample;
                                        delayArrCount++;
                                        if (delayArrCount >= delayArr.length)
                                        {
                                            ArrayList<Short> copyList = new ArrayList<>(ONE_PERIOD_SAMPLES);
                                            //for (short plotSample : delayArr) copyList.add(plotSample);
                                            for (int i=0; i<ONE_PERIOD_SAMPLES; i++) copyList.add(delayArr[i]);
                                            amplitudeSeries.setModel(copyList, SimpleXYSeries.ArrayFormat.Y_VALS_ONLY);

                                            double avgX = 0;
                                            double avgR = 0;
                                            for (int i=0; i<windowNumPeriods; i++){
                                                avgX += delayArr[i*ONE_PERIOD_SAMPLES];
                                                avgR += delayArr[i*ONE_PERIOD_SAMPLES + QUARTER_PERIOD_SAMPLES];
                                            }
                                            avgX /= windowNumPeriods;
                                            avgR /= windowNumPeriods;

                                            double filteredX = filterX.ProcessSample(avgX);
                                            double filteredR = filterR.ProcessSample(avgR);
                                            //double phase = Math.atan2(filteredX, filteredR);
                                            double diff = filteredR - desiredGroundBalanceFactor*filteredX;
                                            metalSignalSeries.addDataThreadSafe(diff);//filterR.ProcessSample(phase));
                                            delayArrCount = 0;
                                        }

                                        /*amplitudeBuffer.addLast(buffer[sampleNum]);
                                        while (amplitudeBuffer.size() > 20)
                                            amplitudeBuffer.removeFirst();
                                        double amp = Statistics.Max(amplitudeBuffer);
                                        SetProgressMicAmp((int) Math.round(amp));
                                        dlia.AddSample(buffer[sampleNum]);*/
                                    }
                                }
                            }
                        }
                    } catch (Exception ex) {
                        //SetErrorMessage("thread error: " + ex.getMessage());
                    }
                }
            });

            rxThread.start();

            volumeCheckTimer = new Timer();
            volumeCheckTimer.schedule(new VolumeCheckTask(), 0, 500);
        }
        catch (Exception ex){
            //todo: better error/status logging
            SetErrorMessage("Init error: " + ex.getMessage());
        }
    }

    private int prevVolume = -1;
    private class VolumeCheckTask extends TimerTask{
        @Override
        public void run() {
            int volumeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (volumeLevel != prevVolume)
            {
                SetStatusMessage(String.format("Output Volume: %d", volumeLevel));
                prevVolume = volumeLevel;
            }
        }
    }

    /*private void ProcessMicSample(short sample) throws InterruptedException {
        dlia.AddSample(sample);
    }*/

    //not thread safe
    private void SetErrorMessage(String message)
    {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void SetStatusMessage(String message)
    {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //textView.post(new Runnable() {public void run() {textView.setText(message);}});
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                textViewStatus.setText(message);
            }
        });

    }

    private long lastProgressMicAmpUpdateTime = System.currentTimeMillis();
    private void SetProgressMicAmp(int value){
        long current = System.currentTimeMillis();

        if ((current - lastProgressMicAmpUpdateTime) > 30) {
            lastProgressMicAmpUpdateTime = current;
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressBarMicAmp.setProgress(value, false);
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        amplitudeRedrawer.start();
        metalSignalRedrawer.start();

        //todo: clear the audio input buffer here
    }

    @Override
    public void onPause() {
        amplitudeRedrawer.pause();
        metalSignalRedrawer.pause();

        super.onPause();
    }

    @Override
    public void onDestroy() {
        amplitudeRedrawer.finish();
        metalSignalRedrawer.finish();

        super.onDestroy();
    }

}