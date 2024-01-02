/*
 * wave_generation.cpp
 *
 *  Created on: Dec 31, 2023
 *      Author: blahblahlaptop
 */

#include "wave_generation.h"

extern "C" DAC_HandleTypeDef hdac1;//DAC1_OUT1 (buffered) = TX_AMP_RAW
extern "C" DAC_HandleTypeDef hdac2;//DAC2_OUT1 (unbuffered) = AUDIO

#define M_PI_F   3.14159265358979323846264338327950288f

DMA_HandleTypeDef hDMA_To_DAC_TX_AMP_RAW;

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
	GenerateWaveToTransmit();

	HAL_DAC_Start(&hdac1, DAC_CHANNEL_1);


	//float dacDesiredVoltage1 = 0.0f;
	//uint32_t dacValue1 = VoltageTo8BitDacValue(dacDesiredVoltage1);
	//float dacActualVoltage1 = ((float)dacValue1)/255.0f * 3.3f;
	//HAL_DAC_SetValue(&hdac1, DAC_CHANNEL_1, DAC_ALIGN_8B_R, dacValue1);

	/*hDMA_To_DAC_TX_AMP_RAW.Instance = DMA1_Channel3;
	hDMA_To_DAC_TX_AMP_RAW.Init.Direction = DMA_MEMORY_TO_PERIPH;//transfer direction
	hDMA_To_DAC_TX_AMP_RAW.Init.PeriphInc = DMA_PINC_DISABLE;
	hDMA_To_DAC_TX_AMP_RAW.Init.MemInc = DMA_MINC_ENABLE;//memory pointer should be incremented (by the data size = 2) each transfer to iterate through all the values
	hDMA_To_DAC_TX_AMP_RAW.Init.PeriphDataAlignment = DMA_PDATAALIGN_HALFWORD;//2-byte transfers for the 12-bit DAC
	hDMA_To_DAC_TX_AMP_RAW.Init.MemDataAlignment = DMA_MDATAALIGN_HALFWORD;
	hDMA_To_DAC_TX_AMP_RAW.Init.Mode = DMA_CIRCULAR;//or DMA_NORMAL ?? not sure which to use
	hDMA_To_DAC_TX_AMP_RAW.Init.Priority = DMA_PRIORITY_HIGH;

	hDMA_To_DAC_TX_AMP_RAW.XferCpltCallback = DMA_DAC_XferCpltCallback;
	hDMA_To_DAC_TX_AMP_RAW.XferErrorCallback = DMA_DAC_XferErrorCallback;
	//hDMA_To_DAC_TX_AMP_RAW.DmaBaseAddress = (DMA_TypeDef*) WaveToTransmit;//no idea if I did this right
	hDMA_To_DAC_TX_AMP_RAW.ChannelIndex = 3;

	//set up the system configuration controller to make the DMA channel mapping work (as per the reference manual)
	//make DMA1 channel 3 work with DAC1_CH1 (instead of TIM6_UP)
	//make DMA1 channel 5 work with DAC2_CH1
	SYSCFG->CFGR1 |= SYSCFG_CFGR1_TIM6DAC1Ch1_DMA_RMP | SYSCFG_CFGR1_DAC2Ch1_DMA_RMP;

	//HAL_NVIC_SetPriority()
	//HAL_NVIC_EnableIRQ(DMA1_Channel1_IRQn);

	__HAL_LINKDMA(&hdac1, DMA_Handle1, hDMA_To_DAC_TX_AMP_RAW);

	HAL_DMA_Init(&hDMA_To_DAC_TX_AMP_RAW);*/

	//HAL_StatusTypeDef HAL_DMA_Start (DMA_HandleTypeDef *hdma, uint32_t SrcAddress, uint32_t DstAddress, uint32_t DataLength);
	HAL_DMA_Start_IT(
			&hDMA_To_DAC_TX_AMP_RAW,
			(uint32_t)WaveToTransmit,
			(uint32_t)hdac1.Instance,
			WaveToTransmitSize
			);
}
