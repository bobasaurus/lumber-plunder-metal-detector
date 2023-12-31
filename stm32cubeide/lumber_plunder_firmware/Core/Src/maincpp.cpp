/*
 * maincpp.cpp
 *
 *  Created on: Nov 7, 2023
 *      Author: blah
 */

#include "maincpp.h"
//#include "u8g2\u8g2.h"

extern "C" ADC_HandleTypeDef hadc1;
extern "C" ADC_HandleTypeDef hadc2;
extern "C" DAC_HandleTypeDef hdac1;//DAC1_OUT1 (buffered) = TX_AMP_RAW
extern "C" DAC_HandleTypeDef hdac2;//DAC2_OUT1 (unbuffered) = AUDIO
extern "C" UART_HandleTypeDef huart2;

int maincpp(void)
{
	SetupWaveGeneration();

	return 0;
}



//void HAL_DAC_Process_DMA (DAC_HandleTypeDef *hDAC)
//{
	//hDAC->DMA_Handle1->XferCpltCallback = HAL_DAC_TxCpltCallback ;
	//hDAC->DMA_Handle1->XferErrorCallback = HAL_DAC_ErrorCallback ;
//}
//const int BUFFER_LEN = 30;
//char buffer[BUFFER_LEN];



/*int maincpp_adc_accuracy_test(void)
{
    LL_ADC_Enable(ADC1);
    LL_ADC_REG_StartConversion(ADC1);
    for (uint32_t i=0; i<LL_ADC_DELAY_CALIB_ENABLE_ADC_CYCLES; i++);//overkill, but will work
    LL_ADC_REG_ReadConversionData12(ADC1);
    HAL_Delay(1);//this delay seems to matter the most?
    LL_ADC_Disable(ADC1);
    HAL_Delay(1);
    LL_ADC_StartCalibration(ADC1, LL_ADC_SINGLE_ENDED);
    //while (LL_ADC_IsCalibrationOnGoing(ADC1));
    for (uint32_t i=0; i<LL_ADC_DELAY_CALIB_ENABLE_ADC_CYCLES; i++);//overkill, but will work
    HAL_Delay(1);
    LL_ADC_Enable(ADC1);
    HAL_Delay(1);

    while(1)
    {
        LL_ADC_REG_StartConversion(ADC1);
        while (LL_ADC_IsActiveFlag_EOS(ADC1) == 0);
        uint16_t rxSample = LL_ADC_REG_ReadConversionData12(ADC1);

        snprintf_(buffer, BUFFER_LEN, "%d\n", rxSample);
        HAL_UART_Transmit(&huart2, (uint8_t *)buffer, strlen(buffer), 1000);
    }
}*/

//u8g2_t u8g2; // a structure which will contain all the data for one display

//extern "C" uint8_t u8x8_stm32_gpio_and_delay(u8x8_t *u8x8, uint8_t msg, uint8_t arg_int, void *arg_ptr);
//extern "C" uint8_t u8x8_byte_stm32_hw_spi(u8x8_t *u8x8, uint8_t msg, uint8_t arg_int, void *arg_ptr);

/*int maincppold(void)
{
    u8g2_Setup_ssd1309_128x64_noname2_f(&u8g2, U8G2_R0, u8x8_byte_stm32_hw_spi,
            u8x8_stm32_gpio_and_delay);
    u8g2_InitDisplay(&u8g2);
    u8g2_SetPowerSave(&u8g2, 0);
    //https://github.com/olikraus/u8g2/wiki/fntlist8
    //u8g2_SetFont(&u8g2, u8g2_font_nokiafc22_tu);
    //u8g2_SetFont(&u8g2, u8g2_font_ncenB14_tr);
    //u8g2_SetFont(&u8g2, u8g2_font_unifont_t_symbols);
    u8g2_SetFont(&u8g2, u8g2_font_5x8_mr);

    //u8g2_DrawXBM(&u8g2, 0, 0, 50, 50, (uint8_t *) bitmap);
    u8g2_ClearBuffer(&u8g2);
    u8g2_SendBuffer(&u8g2);



    GPIO_TypeDef* switchPortList[8] = {SW0_GPIO_Port, SW1_GPIO_Port, SW2_GPIO_Port, SW3_GPIO_Port, SW4_GPIO_Port, SW5_GPIO_Port, SW7_GPIO_Port, SW8_GPIO_Port};
    uint16_t switchPinList[8] = {SW0_Pin, SW1_Pin, SW2_Pin, SW3_Pin, SW4_Pin, SW5_Pin, SW7_Pin, SW8_Pin};

    LL_ADC_Enable(ADC2);
    LL_ADC_REG_StartConversion(ADC2);
    for (uint32_t i=0; i<LL_ADC_DELAY_CALIB_ENABLE_ADC_CYCLES; i++);//overkill, but will work
    LL_ADC_REG_ReadConversionData12(ADC2);
    HAL_Delay(1);//this delay seems to matter the most?
    LL_ADC_Disable(ADC2);
    HAL_Delay(1);
    LL_ADC_StartCalibration(ADC2, LL_ADC_SINGLE_ENDED);
    //while (LL_ADC_IsCalibrationOnGoing(ADC2));
    for (uint32_t i=0; i<LL_ADC_DELAY_CALIB_ENABLE_ADC_CYCLES; i++);//overkill, but will work
    HAL_Delay(1);
    LL_ADC_Enable(ADC2);
    HAL_Delay(1);


    //HAL_DAC_Start(&hdac1, DAC_CHANNEL_1);
    //float dacDesiredVoltage1 = 1.55f;
    //uint32_t dacValue1 = VoltageTo8BitDacValue(dacDesiredVoltage1);
    //float dacActualVoltage1 = ((float)dacValue1)/255.0f * 3.3f;
    //HAL_DAC_SetValue(&hdac1, DAC_CHANNEL_1, DAC_ALIGN_8B_R, dacValue1);

    HAL_DAC_Start(&hdac2, DAC_CHANNEL_1);  //audio out is on PA6 = DAC2/1
    //float dacDesiredVoltage2 = 2.59f;
    //uint32_t dacValue2 = (uint32_t) roundf(dacDesiredVoltage2 * 255 / 3.3f);
    //float dacActualVoltage2 = ((float)dacValue2)/255.0f * 3.3f;
    //HAL_DAC_SetValue(&hdac2, DAC_CHANNEL_1, DAC_ALIGN_8B_R, dacValue2);//2.59V

    float amplitude = 0.00f;
    float frequency = 100.0f;
    float sampleRate = 1000.0f;//[samp/sec]
    float sampleTime = 1.0f/sampleRate;//[sec/sample]
    int samplesPerCycle = (int)roundf(1.0f/frequency * sampleRate);

    uint32_t wave[samplesPerCycle];
    for (int i=0; i<samplesPerCycle; i++)
    {
        float timeS = (i+1) * sampleTime;
        float voltage = amplitude * sinf(2*3.14159265359f*frequency*timeS) + amplitude;
        wave[i] = VoltageTo8BitDacValue(voltage);
    }

    int waveCount = 0;
    int buttonDelay = 0;
    uint32_t sampleTimeMS = (uint32_t)(sampleTime * 1000);
    while (1)
    {
        LL_ADC_REG_StartConversion(ADC2);
        while (LL_ADC_IsActiveFlag_EOS(ADC2) == 0);
        uint16_t adcValue2 = LL_ADC_REG_ReadConversionData12(ADC2);

        float voltageAtADCInput = ((float)adcValue2)/4095 * 3.3;
        //vout = vin * R2/(R1+R2)    where R2 is closest to gnd
        //vin = vout * (R1+R2)/R2
        float batteryVoltage = (100.0f+30.1f)/30.1f*voltageAtADCInput;

        u8g2_ClearBuffer(&u8g2);

        snprintf_(buffer, BUFFER_LEN, "BAT: %.2f [V]\r\n", batteryVoltage);//TODO: disable printf support, it gobbles up a lot of space (about 21% of flash on its own)
        HAL_UART_Transmit(&huart2, (uint8_t *)buffer, strlen(buffer), 1000);

        u8g2_DrawStr(&u8g2, 0, 7, buffer);

        for (uint8_t i=0; i<8; i++)
        {
            GPIO_PinState pinState = HAL_GPIO_ReadPin(switchPortList[i], switchPinList[i]);
            int switchNumber = i;
            if (i >= 6) switchNumber = i+1;
            snprintf_(buffer, BUFFER_LEN, "SW%d:%d", switchNumber, (int)pinState);
            u8g2_DrawStr(&u8g2, 0, 14 + i*7, buffer);
        }

        u8g2_SendBuffer(&u8g2);

        if (buttonDelay-- <= 0)
        {
            buttonDelay = 0;
            float prevAmplitude = amplitude;
            if (HAL_GPIO_ReadPin(SW0_GPIO_Port, SW0_Pin) == 0) amplitude += 0.05f;
            if (HAL_GPIO_ReadPin(SW1_GPIO_Port, SW1_Pin) == 0) amplitude -= 0.05f;
            if (amplitude < 0) amplitude = 0;
            if (amplitude > 2) amplitude = 2;//no idea what a good max is
            if (prevAmplitude != amplitude) {
                for (int i=0; i<samplesPerCycle; i++)
                {
                    float timeS = (i+1) * sampleTime;
                    float voltage = amplitude * sinf(2*3.14159265359f*frequency*timeS) + amplitude;
                    wave[i] = VoltageTo8BitDacValue(voltage);
                }
                //waveCount = 0;
                buttonDelay = 150;

                snprintf_(buffer, BUFFER_LEN, "amp: %.2f", amplitude);
                u8g2_ClearBuffer(&u8g2);
                u8g2_DrawStr(&u8g2, 0, 14, buffer);
                u8g2_SendBuffer(&u8g2);
            }
        }


        uint32_t waveValue = wave[waveCount];
        HAL_DAC_SetValue(&hdac2, DAC_CHANNEL_1, DAC_ALIGN_8B_R, waveValue);//DAC_ALIGN_12B_R
        HAL_Delay(sampleTimeMS);
        waveCount++;
        if (waveCount >= samplesPerCycle) waveCount = 0;
    }

    return 0;
}*/
