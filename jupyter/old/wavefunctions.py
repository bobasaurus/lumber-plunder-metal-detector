import numpy as np
import math
import scipy.signal as signal
import numpy as np

print("setting up wave functions...")

def GenerateSinewavePeriods(sampleRate, frequency, amplitude, phaseOffset, numPeriods):
    #generate x periods worth of samples
    numSamples = int(math.ceil(1.0/frequency * sampleRate * numPeriods)) #[sec] * [samples/sec] = [samples]

    txBuffer = np.zeros(numSamples)
    timeStep = 1.0 / sampleRate

    time = 0.0
    for i in range(numSamples):
        sample = math.sin(2.0 * math.pi * frequency * time + phaseOffset)
        txBuffer[i] = sample * amplitude
        time += timeStep
    
    return txBuffer

#alternately:  x = np.arange(0, numPoints) ; wave = amplitude * np.sin(math.pi*2*frequency/sampleRate * x + phaseOffset)
def GenerateSinewavePoints(sampleRate, frequency, amplitude, phaseOffset, numPoints):
    #generate x periods worth of samples
    numSamples = numPoints #int(math.ceil(1.0/frequency * sampleRate * numPeriods)) #[sec] * [samples/sec] = [samples]

    txBuffer = np.zeros(numSamples)
    timeStep = 1.0 / sampleRate

    time = 0.0
    for i in range(numSamples):
        sample = math.sin(2.0 * math.pi * frequency * time + phaseOffset)
        txBuffer[i] = sample * amplitude
        time += timeStep
    
    return txBuffer

