/*
 * u8g2_stm32f4.c
 *
 *  Created on: 2019. 3. 27.
 *      Author: KIKI
 */

#include "main.h"
#include "stm32f3xx_hal.h"
#include "u8g2/u8g2.h"

#define DEVICE_ADDRESS 	0x3C
#define TX_TIMEOUT		100

extern SPI_HandleTypeDef hspi1;
extern I2C_HandleTypeDef hi2c1;

/*
    can manually reset the display like this:
    HAL_GPIO_WritePin(OLED_RST_GPIO_Port, OLED_RST_Pin, RESET);
    HAL_Delay(100);
    HAL_GPIO_WritePin(OLED_RST_GPIO_Port, OLED_RST_Pin, SET);
    HAL_Delay(200);
 */

//uC specific GPIO and delay callback
//(only the U8X8_MSG_GPIO_RESET message is mandatory when using hardware SPI (from early testing at least), but I'm leaving the rest to be safe)
uint8_t u8x8_stm32_gpio_and_delay(u8x8_t *u8x8, uint8_t msg, uint8_t arg_int, void *arg_ptr)
{
	//STM32 supports HW SPI, Remove unused cases like U8X8_MSG_DELAY_XXX & U8X8_MSG_GPIO_XXX
	switch(msg)
	{
	case U8X8_MSG_GPIO_AND_DELAY_INIT:
		//Insert codes for initialization
		break;
    case U8X8_MSG_DELAY_10MICRO://timing may not be perfect, copy pasted from here: https://github.com/nikola-v/u8g2_template_stm32f103c8t6/blob/master/Src/main.c
        for (uint16_t n = 0; n < 320; n++)
        {
            __NOP();
        }
        break;
    case U8X8_MSG_DELAY_100NANO://timing may not be perfect
        __NOP();
        break;
	case U8X8_MSG_DELAY_MILLI:
		HAL_Delay(arg_int);
		break;
	case U8X8_MSG_GPIO_CS:
		//Insert codes for SS pin control
		HAL_GPIO_WritePin(OLED_CS_GPIO_Port, OLED_CS_Pin, arg_int);
		break;
	case U8X8_MSG_GPIO_DC:
		//Insert codes for DC pin control
		HAL_GPIO_WritePin(OLED_DC_GPIO_Port, OLED_DC_Pin, arg_int);
		break;
	case U8X8_MSG_GPIO_RESET://not sure if this is ever called?
		//Insert codes for RST pin control
		HAL_GPIO_WritePin(OLED_RES_GPIO_Port, OLED_RES_Pin, arg_int);
		break;
	}
	return 1;
}

//This is the minimal set of messages that I need to handle when using the hardware SPI.
/*uint8_t u8x8_stm32_gpio_and_delay(u8x8_t *u8x8, uint8_t msg, uint8_t arg_int, void *arg_ptr)
{
    switch(msg)
    {
    case U8X8_MSG_GPIO_RESET://not sure if this is ever called?
        //Insert codes for RST pin control
        HAL_GPIO_WritePin(OLED_RST_GPIO_Port, OLED_RST_Pin, arg_int);
        break;
    }
    return 1;
}*/

//this lets us use the microcontroller's hardware SPI instead of the u8g2 library's built-in bitbanging SPI
uint8_t u8x8_byte_stm32_hw_spi(u8x8_t *u8x8, uint8_t msg, uint8_t arg_int, void *arg_ptr)
{
	switch(msg) {
	case U8X8_MSG_BYTE_SEND:
		/* Insert codes to transmit data */
		if(HAL_SPI_Transmit(&hspi1, arg_ptr, arg_int, TX_TIMEOUT) != HAL_OK) return 0;
		break;
	case U8X8_MSG_BYTE_INIT:
		/* Insert codes to begin SPI transmission */
		break;
	case U8X8_MSG_BYTE_SET_DC:
		//u8x8_gpio_SetDC(u8x8, arg_int);//Control DC pin, U8X8_MSG_GPIO_DC message will be called (incurs extra function call)
	    HAL_GPIO_WritePin(OLED_DC_GPIO_Port, OLED_DC_Pin, arg_int);
		break;
	case U8X8_MSG_BYTE_START_TRANSFER:
		//u8x8_gpio_SetCS(u8x8, u8x8->display_info->chip_enable_level);//(incurs extra function call)
	    HAL_GPIO_WritePin(OLED_CS_GPIO_Port, OLED_CS_Pin, RESET);
		//HAL_Delay(1);
		__NOP();
		break;
	case U8X8_MSG_BYTE_END_TRANSFER:
		//HAL_Delay(1);
	    __NOP();
		//u8x8_gpio_SetCS(u8x8, u8x8->display_info->chip_disable_level);//extra function call, maybe just duplicate GPIO pin change here
		HAL_GPIO_WritePin(OLED_CS_GPIO_Port, OLED_CS_Pin, SET);
		break;
	default:
		return 0;
	}
	return 1;
}
