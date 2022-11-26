package com.example.lumberplundermetaldetector;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;

import android.media.AudioRecord;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.Plot;
import com.androidplot.xy.*;
import com.androidplot.util.Redrawer;

import org.apache.commons.math3.util.FastMath;

import java.io.File;
import java.io.FileWriter;
import java.lang.ref.WeakReference;
import java.text.ChoiceFormat;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.time.*;
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
    private RollingXYSeries amplitudeSeries;
    
    private XYPlot phasePlot;
    private Redrawer phaseRedrawer;
    private RollingXYSeries phaseSeries;

    private Button buttonStart;
    private Button buttonRecord;
    private ProgressBar progressBarMicAmp;
    private TextView textViewStatus;

    private boolean threadQuit = false;
    private Thread thread;

    private Timer volumeCheckTimer;

    private AudioRecord audioRecord;
    private static final int SAMPLE_RATE_RX = 192000;//192000 is the max on my pixel 3a

    private AudioTrack audioTrack;
    private static final double TX_FREQ_HZ = 8000;
    private static final int SAMPLE_RATE_TX = 192000;
    private short txBuffer[];// = new short[SAMPLE_RATE_TX];
    private boolean isPlaying = false;

    private DigitalLockInAmplifier dlia;

    private ISampleCollector phaseCollector, magCollector;

    private boolean isRecording = false;

    private LinkedList<Short> amplitudeBuffer = new LinkedList<Short>();

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

            externalFilesDir = this.getExternalFilesDir(null);

            //check that the 3.5mm plug from the metal detector is plugged in
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            Boolean wiredOn = audioManager.isWiredHeadsetOn();
            if (!wiredOn) SetErrorMessage("Need to plug in metal detector cable");
            //audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);

            //setup the amplitude plot
            {
                int numPoints = 3000;

                amplitudePlot = findViewById(R.id.amplitudePlot);
                amplitudeSeries = new RollingXYSeries(numPoints, new WeakReference<>(amplitudePlot.getRenderer(FastLineAndPointRenderer.class)));

                FastLineAndPointRenderer.Formatter series1Format = new FastLineAndPointRenderer.Formatter(Color.GREEN, Color.GREEN, null);

                //LineAndPointFormatter series1Format =
                //        new LineAndPointFormatter(this, R.xml.line_point_formatter_with_labels);
                series1Format.setPointLabeler(null);
                series1Format.setVertexPaint(null);
                amplitudePlot.addSeries(amplitudeSeries, series1Format);
                amplitudePlot.setDomainBoundaries(0, numPoints, BoundaryMode.FIXED);
                amplitudePlot.getLegend().setVisible(false);
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
                amplitudeRedrawer = new Redrawer(amplitudePlot, 15, true);
            }

            //setup the phase plot
            {
                int numPoints = 3000;

                phasePlot = findViewById(R.id.phasePlot);
                phaseSeries = new RollingXYSeries(numPoints, new WeakReference<>(phasePlot.getRenderer(FastLineAndPointRenderer.class)));

                //LineAndPointFormatter series1Format = new LineAndPointFormatter(this, R.xml.line_point_formatter_with_labels_2);
                FastLineAndPointRenderer.Formatter series1Format = new FastLineAndPointRenderer.Formatter(Color.RED, Color.RED, null);
                series1Format.setPointLabeler(null);
                series1Format.setVertexPaint(null);
                series1Format.setInterpolationParams(null);

                phasePlot.addSeries(phaseSeries, series1Format);
                phasePlot.setDomainBoundaries(0, numPoints, BoundaryMode.FIXED);
                phasePlot.getLegend().setVisible(false);
                phasePlot.getGraph().setMarginLeft(75);

                //integer x axis label formatting
                phasePlot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).setFormat(new Format() {
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
                //phasePlot.addListener(phaseSeries);

                //use a redrawer to update/refresh/redraw the graph at a set framerate
                phaseRedrawer = new Redrawer(phasePlot, 15, true);
            }

            //setup audio output
            txBuffer = SinewaveGenerator.GenerateSinewave(SAMPLE_RATE_TX, TX_FREQ_HZ, Short.MAX_VALUE, 10);

            //todo: try using audioTrack.setNotificationMarkerPosition to track exact transmit position/phase for dlia sync?
            //https://stackoverflow.com/a/6655260/268399
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE_TX, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, txBuffer.length,
                    AudioTrack.MODE_STATIC);
            audioTrack.write(txBuffer, 0, txBuffer.length);

            buttonRecord = findViewById(R.id.recordButton);
            buttonRecord.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    /*if (isRecording) {
                        isRecording = false;
                        buttonRecord.setText("Record");
                    } else {
                        isRecording = true;
                        buttonRecord.setText("Stop");
                    }*/

                    if (isRecording) return;
                    isRecording = true;
                }
            });

            buttonStart = (Button) findViewById(R.id.startStopButton);
            buttonStart.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    if (isPlaying) {
                        audioTrack.stop();
                        isPlaying = false;
                        buttonStart.setText("Start");
                    } else {
                        //looping needs to be initialized right before play is called, or else it doesn't work
                        int loopSuccess = audioTrack.setLoopPoints(0, txBuffer.length / 2, -1);
                        if (loopSuccess != AudioTrack.SUCCESS) {
                            SetErrorMessage("Could not start loop, error value: " + loopSuccess);
                        }

                        audioTrack.play();
                        isPlaying = true;
                        buttonStart.setText("Stop");
                    }
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
            int bufferSizeInBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE_RX, audioChannelConfig, audioEncoding) * 50;
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.UNPROCESSED, SAMPLE_RATE_RX, audioChannelConfig, audioEncoding, bufferSizeInBytes);


            //setup the digital lock-in amplifier
            phaseCollector = new ISampleCollector() {
                private int count = 0;

                @Override
                public void AddSample(double sample) {
                    //skip data to reduce the num points (should probably filter before this decimation, but oh well)
                    /*if (count++%10 == 0) */

                    //phaseSeries.addDataThreadSafe(sample);
                }
            };
            magCollector = new ISampleCollector() {
                private final int BUFFER_SIZE = 10;
                private double[] buffer = new double[BUFFER_SIZE];
                private int count = 0;

                @Override
                public void AddSample(double sample) {
                    /*if (count < BUFFER_SIZE){
                        buffer[count] = sample;
                        count++;
                    }
                    else {
                        amplitudeSeries.addDataThreadSafe(Statistics.Average(buffer));
                        count =0;
                    }*/

                    amplitudeSeries.addDataThreadSafe(sample);
                }
            };
            //dlia = new DigitalLockInAmplifier(SAMPLE_RATE_RX, TX_FREQ_HZ, 4, 10,
            //        phaseCollector, magCollector);
            dlia = new DigitalLockInAmplifier(SAMPLE_RATE_RX, TX_FREQ_HZ, 16, 10, 10,
                    phaseCollector, magCollector);

            //setup worker thread for polling/collecting audio recording bytes
            //todo: exception handling
            //reentrantLockFFT = new ReentrantLock();
            //reentrantLockPhase = new ReentrantLock();
            //reentrantLockMag = new ReentrantLock();

            thread = new Thread(new Runnable() {
                public void run() {
                    try {

                        FileWriter fileWriter = null;
                        //audioRecord.registerAudioRecordingCallback();
                        short[] buffer = new short[bufferSizeInBytes / 2];
                        audioRecord.startRecording();

                        while (!threadQuit) {
                            //using a smaller read size than the buffer size to avoid latency issues with big buffers (and big buffers are required to avoid garbage collector missed data blips)
                            //https://stackoverflow.com/a/39230480/268399
                            //note: I noticed a gradual latency increase when reading the full buffer at once, now I just read a small quantity to keep up with processing
                            int readResult = audioRecord.read(buffer, 0, 512);//bufferSizeInBytes / 2);
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
                                    if (isRecording) {
                                        if (fileWriter == null) {

                                            SetStatusMessage("Starting to record...");

                                            int volumeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);


                                            SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd HH_mm_ss z");
                                            Date date = new Date(System.currentTimeMillis());
                                            String filename = formatter.format(date) + String.format(" %d SPS %.1f Hz %d vol", SAMPLE_RATE_RX, TX_FREQ_HZ, volumeLevel) + (isPlaying ? " playing" : " stopped") + ".txt";

                                            File file = new File(externalFilesDir, filename);

                                            fileWriter = new FileWriter(file);

                                            recordBuffer = new Short[SAMPLE_RATE_RX * 4];
                                            recordCount = 0;

                                            //clear the buffer
                                            audioRecord.read(buffer, 0, bufferSizeInBytes / 2);
                                        }

                                        //note: phone can't keep up with writing samples this fast to disk, use a buffer instead (recording for 10 seconds only stores 4 sec worth of data right now, the rest is lagged in the audio buffer)
                                        //fileWriter.write(String.format("%d\r\n", buffer[sampleNum]));
                                        recordBuffer[recordCount] = buffer[sampleNum];
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
                                            audioRecord.read(buffer, 0, bufferSizeInBytes / 2);
                                        }

                                        amplitudeBuffer.addLast(buffer[sampleNum]);
                                        while (amplitudeBuffer.size() > 20)
                                            amplitudeBuffer.removeFirst();
                                        double amp = Statistics.Max(amplitudeBuffer);
                                        SetProgressMicAmp((int) Math.round(amp));

                                        dlia.AddSample(buffer[sampleNum]);
                                    }
                                }
                            }
                        }
                    } catch (Exception ex) {
                        //SetErrorMessage("thread error: " + ex.getMessage());
                    }
                }
            });

            thread.start();

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
                SetStatusMessage(String.format("output volume: %d", volumeLevel));
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
        phaseRedrawer.start();

        //todo: clear the audio input buffer here
    }

    @Override
    public void onPause() {
        amplitudeRedrawer.pause();
        phaseRedrawer.pause();

        super.onPause();
    }

    @Override
    public void onDestroy() {
        amplitudeRedrawer.finish();
        phaseRedrawer.finish();

        super.onDestroy();
    }

}