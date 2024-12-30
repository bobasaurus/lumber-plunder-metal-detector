using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Data;
using System.Drawing;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace sine_wave_resolution_test
{
    public partial class Form1 : Form
    {
        private const UInt32 WAVE_BUFFER_CAPACITY = 50000;

        UInt16 waveAmplitude;//DAC counts ranging from [0,4095]
        UInt16 waveFrequency;//[Hz]
        UInt32 waveSize;//[points]
        UInt16[] waveBuffer = new UInt16[WAVE_BUFFER_CAPACITY];

        void GenerateWaveToTransmit(double sampleRate, double frequency, double amplitude, double center)
        {
            double sampleTime = 1.0 / sampleRate;//[sec/sample]
            UInt32 samplesPerCycle = (UInt32)Math.Round(sampleRate / frequency);

            if (samplesPerCycle >= WAVE_BUFFER_CAPACITY) throw new Exception("wave buffer too small");

            for (UInt32 i = 0; i < samplesPerCycle; i++)
            {
                double timeS = (i) * sampleTime;
                double waveValue = amplitude * Math.Sin(2 * Math.PI * frequency * timeS) + center;
                UInt16 waveValueInt = (UInt16)Math.Round(waveValue);//TODO: try just truncating instead of rounding
                if (waveValueInt < 0) throw new Exception("wave value too small");
                if (waveValueInt > 4095) throw new Exception("wave value too large");
                waveBuffer[i] = waveValueInt;
            }

            waveAmplitude = (UInt16)amplitude;
            waveFrequency = (UInt16)frequency;
            waveSize = samplesPerCycle;
        }

        public Form1()
        {
            InitializeComponent();
            try
            {

                double sampleRate = 10_000_000;//samples/sec
                double frequency = 10000;
                double amplitude = 2047.5;
                double center = 2047.5;
                GenerateWaveToTransmit(sampleRate, frequency, amplitude, center);

                //count the number of unique points in the first 1/4 cycle, which should equal the amplitude if no dac points are skipped due to a low sampling rate
                HashSet<double> hashSet = new HashSet<double>();
                for (int i = 0; i < waveSize / 4; i++) hashSet.Add(waveBuffer[i]);
                numUniquePointsLabel.Text = "num unique points in first 1/4 cycle: " + hashSet.Count;
                if ((hashSet.Count) < Math.Round(amplitude, 0)) MessageBox.Show("Missing Points!! (" + (hashSet.Count) + "/" + amplitude + ")");


                var pointPairList = new ZedGraph.PointPairList();
                for (int i = 0; i < waveSize; i++) pointPairList.Add(i, waveBuffer[i]);
                var curve = zedGraphControl1.GraphPane.AddCurve("sine wave", pointPairList, Color.Green);
                curve.Symbol.Type = ZedGraph.SymbolType.Circle;
                curve.Symbol.Fill = new ZedGraph.Fill(curve.Color);
                zedGraphControl1.GraphPane.XAxis.Scale.MaxGrace = 0;
                zedGraphControl1.GraphPane.YAxis.Scale.MaxGrace = 0;
                zedGraphControl1.GraphPane.XAxis.Scale.MinGrace = 0;
                zedGraphControl1.GraphPane.YAxis.Scale.MinGrace = 0;
                zedGraphControl1.GraphPane.Title.IsVisible = false;
                zedGraphControl1.GraphPane.Legend.IsVisible = false;
                zedGraphControl1.AxisChange();
                zedGraphControl1.Refresh();
            }
            catch (Exception ex)
            {
                MessageBox.Show(ex.Message);
            }
        }
    }
}
