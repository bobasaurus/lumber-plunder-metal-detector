/*
 * wave_generation.cpp
 *
 *  Created on: Dec 31, 2023
 *      Author: blahblahlaptop
 */

#include "wave_generation.h"

extern "C" DAC_HandleTypeDef hdac1;//DAC1_OUT1 (buffered) = TX_AMP_RAW
extern "C" DAC_HandleTypeDef hdac2;//DAC2_OUT1 (unbuffered) = AUDIO

DMA_HandleTypeDef hDMA_To_DAC_TX_AMP_RAW;

uint32_t VoltageTo8BitDacValue(float voltage)
{
    if ((voltage < 0.0f) || (voltage > 3.3f)) return 0;//todo: output error?

    uint32_t dacValue = (uint32_t) roundf(voltage * 255 / 3.3f);//todo: not the fastest with this function call... maybe come up with a better way eventually
    return dacValue;
}

void DMA_DAC_XferCpltCallback(DMA_HandleTypeDef *hDMA)
{

}

void DMA_DAC_XferErrorCallback(DMA_HandleTypeDef *hDMA)
{

}

void SetupWaveGeneration()
{
	//Enable and configure the peripheral to be connected to the DMA Channel
	//refer to Reference manual for connection between peripherals and DMA requests

	HAL_DAC_Start(&hdac1, DAC_CHANNEL_1);
	//float dacDesiredVoltage1 = 0.0f;
	//uint32_t dacValue1 = VoltageTo8BitDacValue(dacDesiredVoltage1);
	//float dacActualVoltage1 = ((float)dacValue1)/255.0f * 3.3f;
	//HAL_DAC_SetValue(&hdac1, DAC_CHANNEL_1, DAC_ALIGN_8B_R, dacValue1);

	hDMA_To_DAC_TX_AMP_RAW.Init.Direction = DMA_MEMORY_TO_PERIPH;//transfer direction
	hDMA_To_DAC_TX_AMP_RAW.Init.PeriphInc = DMA_PINC_DISABLE;
	hDMA_To_DAC_TX_AMP_RAW.Init.MemInc = DMA_MINC_DISABLE;
	hDMA_To_DAC_TX_AMP_RAW.Init.PeriphDataAlignment = DMA_PDATAALIGN_HALFWORD;//2-byte transfers for the 12-bit DAC
	hDMA_To_DAC_TX_AMP_RAW.Init.MemDataAlignment = DMA_MDATAALIGN_HALFWORD;
	hDMA_To_DAC_TX_AMP_RAW.Init.Mode = DMA_CIRCULAR;//or DMA_NORMAL ?? not sure which to use
	hDMA_To_DAC_TX_AMP_RAW.Init.Priority = DMA_PRIORITY_HIGH;

	hDMA_To_DAC_TX_AMP_RAW.XferCpltCallback = DMA_DAC_XferCpltCallback;
	hDMA_To_DAC_TX_AMP_RAW.XferErrorCallback = DMA_DAC_XferErrorCallback;
	hDMA_To_DAC_TX_AMP_RAW.DmaBaseAddress = 0;//??????
	hDMA_To_DAC_TX_AMP_RAW.ChannelIndex = 3;
	//set up the system configuration controller to make the DMA channel mapping work (as per the reference manual)
	//make DMA1 channel 3 work with DAC1_CH1 (instead of TIM6_UP)
	//make DMA1 channel 5 work with DAC2_CH1
	SYSCFG->CFGR1 |= SYSCFG_CFGR1_TIM6DAC1Ch1_DMA_RMP | SYSCFG_CFGR1_DAC2Ch1_DMA_RMP;

	//HAL_NVIC_SetPriority()
	//HAL_NVIC_EnableIRQ(DMA1_Channel1_IRQn);

	__HAL_LINKDMA(&hdac1, DMA_Handle1, hDMA_To_DAC_TX_AMP_RAW);

	/*HAL_StatusTypeDef dmaStatus =*/ HAL_DMA_Init(&hDMA_To_DAC_TX_AMP_RAW);
}
