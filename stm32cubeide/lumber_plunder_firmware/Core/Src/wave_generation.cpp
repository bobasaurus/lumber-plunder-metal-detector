/*
 * wave_generation.cpp
 *
 *  Created on: Dec 31, 2023
 *      Author: blahblahlaptop
 */

#include "wave_generation.h"

extern "C" DAC_HandleTypeDef hdac1;//DAC1_OUT1 (buffered) = TX_AMP_RAW
extern "C" DAC_HandleTypeDef hdac2;//DAC2_OUT1 (unbuffered) = AUDIO
extern "C" DMA_HandleTypeDef hdma_dac1_ch1;//DMA handle for DAC 1 Channel 1 (TX_AMP_RAW)

#define M_PI_F   3.14159265358979323846264338327950288f

bool WaveToTransmitAlreadyGenerated = false;
uint32_t WaveToTransmitSize;
uint16_t *WaveToTransmit;

uint16_t VoltageTo12BitDacValue(float voltage)
{
    if ((voltage < 0.0f) || (voltage > 3.3f)) return 0;//todo: output error?

    uint32_t dacValue = (uint32_t) roundf(voltage * 4095 / 3.3f);//todo: not the fastest with this function call... maybe come up with a better way eventually
    return dacValue;
}

void GenerateWaveToTransmit()
{
	if (WaveToTransmitAlreadyGenerated) return;//quick safeguard on calling this multiple times

	float amplitude = 1.0f;//[V], and wave will be offset so every value is above zero
	float frequency = 10'000;//(using digit separators for easier reading)
	float sampleRate = 1'000'000;//[samp/sec]
	float sampleTime = 1.0f/sampleRate;//[sec/sample]
	int samplesPerCycle = (int)roundf(1.0f/frequency * sampleRate);

	WaveToTransmitSize = (uint32_t)samplesPerCycle;
	WaveToTransmit = (uint16_t *)malloc(sizeof(uint16_t) * samplesPerCycle);//maybe don't need to free since the program will be using this constantly and never really "exit"

	for (int i=0; i<samplesPerCycle; i++)
	{
		float timeS = (i+1) * sampleTime;
		float voltage = amplitude * sinf(2*M_PI_F*frequency*timeS) + amplitude;
		WaveToTransmit[i] = VoltageTo12BitDacValue(voltage);
	}

	WaveToTransmitAlreadyGenerated = true;
}

void DMA_DAC_XferCpltCallback(DMA_HandleTypeDef *hDMA)
{
	__NOP();
}

void DMA_DAC_XferErrorCallback(DMA_HandleTypeDef *hDMA)
{
	__NOP();
}

void SetupWaveGeneration()
{
	//TODO: work out DAC clock speed and how it relates to sample rate, match DAC sample rate to generated waveform sample rate
	//might need a timer trigger to the DMA, ugh
	GenerateWaveToTransmit();

	HAL_DAC_Start(&hdac1, DAC_CHANNEL_1);

	//HAL_DAC_SetValue(&hdac1, DAC_CHANNEL_1, DAC_ALIGN_12B_R, dacValue1);

	hdma_dac1_ch1.XferCpltCallback=&DMA_DAC_XferCpltCallback;
	hdma_dac1_ch1.XferErrorCallback=&DMA_DAC_XferErrorCallback;

	//HAL_StatusTypeDef HAL_DMA_Start (DMA_HandleTypeDef *hdma, uint32_t SrcAddress, uint32_t DstAddress, uint32_t DataLength);
	HAL_DMA_Start_IT(
			&hdma_dac1_ch1,
			(uint32_t)WaveToTransmit,
			(uint32_t)hdac1.Instance,
			WaveToTransmitSize
			);
}
