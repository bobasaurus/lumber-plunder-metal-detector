package com.example.lumberplundermetaldetector;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;

import android.media.AudioRecord;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.androidplot.Plot;
import com.androidplot.xy.*;
import com.androidplot.util.Redrawer;

import org.apache.commons.math3.util.FastMath;

import java.lang.ref.WeakReference;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    private AudioManager audioManager;

    private XYPlot amplitudePlot;
    private Redrawer amplitudeRedrawer;
    private RollingXYSeries amplitudeSeries;
    
    private XYPlot phasePlot;
    private Redrawer phaseRedrawer;
    private RollingXYSeries phaseSeries;

    private Button buttonStart;

    private boolean threadQuit = false;
    private Thread thread;

    private AudioRecord audioRecord;
    private static final int SAMPLE_RATE_RX = 48000;//192000 is the max on my pixel 3a

    private AudioTrack audioTrack;
    private static final double TX_FREQ_HZ = 16000;
    private static final int SAMPLE_RATE_TX = 192000;
    private short txBuffer[];// = new short[SAMPLE_RATE_TX];
    private boolean isPlaying = false;

    private DigitalLockInAmplifier dlia;

    private ISampleCollector phaseCollector, magCollector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            //request audio recording permissions in a loop until granted
            while (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            }

            //check that the 3.5mm plug from the metal detector is plugged in
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            Boolean wiredOn = audioManager.isWiredHeadsetOn();
            if (!wiredOn) SetErrorMessage("Need to plug in metal detector cable");
            //audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);

            //setup the amplitude plot
            {
                int numPoints = 4000;

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

                //NOTE: doesn't seem to be necessary to explicitly call this, oddly
                //Add the draw start/finish listener, which is the custom series I made.
                //This handles the synchronization locks while drawing the plot.
                //amplitudePlot.addListener(amplitudeSeries);

                //use a redrawer to update/refresh/redraw the graph at a set framerate
                amplitudeRedrawer = new Redrawer(amplitudePlot, 15, true);
            }

            //setup the phase plot
            {
                int numPoints = 4000;

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

                //NOTE: doesn't seem to be necessary to explicitly call this, oddly
                //Add the draw start/finish listener, which is the custom series I made.
                //This handles the synchronization locks while drawing the plot.
                //phasePlot.addListener(phaseSeries);

                //use a redrawer to update/refresh/redraw the graph at a set framerate
                phaseRedrawer = new Redrawer(phasePlot, 15, true);
            }

            //setup audio output
            txBuffer = SinewaveGenerator.GenerateSinewave(SAMPLE_RATE_TX, TX_FREQ_HZ);

            //todo: try using audioTrack.setNotificationMarkerPosition to track exact transmit position/phase for dlia sync?
            //https://stackoverflow.com/a/6655260/268399
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE_TX, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, txBuffer.length,
                    AudioTrack.MODE_STATIC);
            audioTrack.write(txBuffer, 0, txBuffer.length);

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
            int bufferSizeInBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE_RX, audioChannelConfig, audioEncoding);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.UNPROCESSED, SAMPLE_RATE_RX, audioChannelConfig, audioEncoding, bufferSizeInBytes);

            

            //setup the digital lock-in amplifier
            phaseCollector = new ISampleCollector() {
                private int count = 0;
                @Override
                public void AddSample(double sample) {
                    //skip data to reduce the num points (should probably filter before this decimation, but oh well)
                    if (count++%10 == 0) phaseSeries.addDataThreadSafe(sample);
                }
            };
            magCollector = new ISampleCollector() {
                private final int BUFFER_SIZE = 10;
                private double[] buffer = new double[BUFFER_SIZE];
                private int count = 0;
                @Override
                public void AddSample(double sample) {
                    if (count < BUFFER_SIZE){
                        buffer[count] = sample;
                        count++;
                    }
                    else {
                        amplitudeSeries.addDataThreadSafe(Statistics.Average(buffer));
                        count =0;
                    }
                }
            };
            dlia = new DigitalLockInAmplifier(SAMPLE_RATE_RX, TX_FREQ_HZ, 4, 50,
                    phaseCollector, magCollector, null);

            //setup worker thread for polling/collecting audio recording bytes
            //todo: exception handling
            //reentrantLockFFT = new ReentrantLock();
            //reentrantLockPhase = new ReentrantLock();
            //reentrantLockMag = new ReentrantLock();

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
                                    ProcessMicSample(buffer[sampleNum]);//todo: maybe send whole buffer to save number of calls?
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
    }

    private void SetErrorMessage(String message)
    {
        //textView.post(new Runnable() {public void run() {textView.setText("Err: " + message);}});
        Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
    }

    private void SetStatusMessage(String message)
    {
        //textView.post(new Runnable() {public void run() {textView.setText(message);}});
        Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onResume() {
        super.onResume();

        amplitudeRedrawer.start();
        phaseRedrawer.start();
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