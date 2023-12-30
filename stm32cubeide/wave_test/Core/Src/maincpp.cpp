/*
 * maincpp.cpp
 *
 *  Created on: Nov 7, 2023
 *      Author: blah
 */

#include "maincpp.h"
#include "u8g2\u8g2.h"

u8g2_t u8g2; // a structure which will contain all the data for one display

extern "C" ADC_HandleTypeDef hadc1;
extern "C" ADC_HandleTypeDef hadc2;
extern "C" DAC_HandleTypeDef hdac1;
extern "C" DAC_HandleTypeDef hdac2;
extern "C" UART_HandleTypeDef huart2;

extern "C" uint8_t u8x8_stm32_gpio_and_delay(u8x8_t *u8x8, uint8_t msg, uint8_t arg_int, void *arg_ptr);
extern "C" uint8_t u8x8_byte_stm32_hw_spi(u8x8_t *u8x8, uint8_t msg, uint8_t arg_int, void *arg_ptr);

/*uint32_t VoltageTo8BitDacValue(float voltage)
{
    if ((voltage < 0.0f) || (voltage > 3.3f)) return 0;//todo: output error?

    uint32_t dacValue = (uint32_t) roundf(voltage * 255 / 3.3f);//todo: not the fastest with this function call... maybe come up with a better way eventually
    return dacValue;
}*/


//output a single cycle of a sine wave with values in the 0-4095 (12-bit) range
uint16_t YSineDigital(int x, int ns)
{
	return (uint16_t)lround((sin(x * 2*M_PI/ns) + 1) * 0xFFF / 2);
}

float SineFrequencyToTimerTriggerOutputFrequency(float sineFrequency, int ns)
{
	return sineFrequency * ns;
}

float TimerTriggerOutputFrequencyToSineFrequency(float trgoFrequency, int ns)
{
	return trgoFrequency / ns;
}

#define NUM_SAMPLES 256
uint16_t testToneBuffer[NUM_SAMPLES];

int maincpp(void)
{
	//DAC/DMA Setup
	/*
	* DAC1_OUT1 / pin 10 / PA4 (A3): buffered => TX_AMP_RAW
	* DAC1_OUT2 / pin 11 / PA5 (A4): unbuffered => (not used as DAC, instead using as battery measurement ADC input)
	* DAC2_OUT1 / pin 12 / PA6 (A5): unbuffered => AUDIO
	*/

	for (int i=0; i<NUM_SAMPLES; i++)
	{
		testToneBuffer[i] = YSineDigital(i, NUM_SAMPLES);
	}

	//the dac is triggered by the timer at the desired sample rate

	return 0;
}

/*int maincpp(void)
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

    uint8_t count = 0;
    const int BUFFER_LEN = 30;
    char buffer[BUFFER_LEN];
    uint32_t adcValue2;

    GPIO_TypeDef* switchPortList[8] = {SW0_GPIO_Port, SW1_GPIO_Port, SW2_GPIO_Port, SW3_GPIO_Port, SW4_GPIO_Port, SW5_GPIO_Port, SW7_GPIO_Port, SW8_GPIO_Port};
    uint16_t switchPinList[8] = {SW0_Pin, SW1_Pin, SW2_Pin, SW3_Pin, SW4_Pin, SW5_Pin, SW7_Pin, SW8_Pin};

    while(1){
    	HAL_ADC_Start(&hadc2);
		if (HAL_ADC_PollForConversion(&hadc2, 100) == HAL_OK) {
			adcValue2 = HAL_ADC_GetValue(&hadc2);

			float voltageAtADCInput = ((float)adcValue2)/4095 * 3.3;
			//vout = vin * R2/(R1+R2)    where R2 is closest to gnd
			//vin = vout * (R1+R2)/R2
			float batteryVoltage = (100.0f+30.1f)/30.1f*voltageAtADCInput;

			snprintf_(buffer, BUFFER_LEN, "BAT: %.2f [V]\r\n", batteryVoltage);//TODO: disable printf support, it gobbles up a lot of space (about 21% of flash on its own)
			count++;
			HAL_UART_Transmit(&huart2, (uint8_t *)buffer, strlen(buffer), 1000);

			u8g2_ClearBuffer(&u8g2);
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
		}



        HAL_Delay(250);
    }

    return 0;
}*/
