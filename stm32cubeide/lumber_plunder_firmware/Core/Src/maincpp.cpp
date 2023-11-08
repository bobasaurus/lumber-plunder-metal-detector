/*
 * maincpp.cpp
 *
 *  Created on: Nov 7, 2023
 *      Author: blah
 */

#include "maincpp.h"

uint32_t VoltageTo8BitDacValue(float voltage)
{
    if ((voltage < 0.0f) || (voltage > 3.3f)) return 0;//todo: output error?

    uint32_t dacValue = (uint32_t) roundf(voltage * 255 / 3.3f);//todo: not the fastest with this function call... maybe come up with a better way eventually
    return dacValue;
}

int maincpp(void)
{
    float amplitude = 0.5f;
    float frequency = 300.0f;
    float sampleRate = 1000.0f;//[samp/sec]
    float sampleTime = 1.0f/sampleRate;//[sec/sample]


    int samplesPerCycle = (int)roundf(1.0f/frequency * sampleRate);

    uint32_t wave[samplesPerCycle];

    for (int i=0; i<samplesPerCycle; i++){
        float timeS = (i+1) * sampleTime;
        float voltage = amplitude * sinf(2*3.14159265359f*frequency*timeS) + amplitude;
        wave[i] = VoltageTo8BitDacValue(voltage);
    }

    int waveCount = 0;
    uint32_t sampleTimeMS = (uint32_t)(sampleTime * 1000);
    while (1) {
        uint32_t waveValue = wave[waveCount];
        //HAL_DAC_SetValue(&hdac2, DAC_CHANNEL_1, DAC_ALIGN_8B_R, waveValue);

        HAL_Delay(sampleTimeMS);

        waveCount++;
        if (waveCount >= samplesPerCycle) waveCount = 0;
    }

    return 0;
}
