/*
 * maincpp.cpp
 *
 *  Created on: Nov 7, 2023
 *      Author: blah
 */

#include "maincpp.h"
#include "u8g2\u8g2.h"

u8g2_t u8g2; // a structure which will contain all the data for one display

extern "C" UART_HandleTypeDef huart2;

extern "C" uint8_t u8x8_stm32_gpio_and_delay(u8x8_t *u8x8, uint8_t msg, uint8_t arg_int, void *arg_ptr);
extern "C" uint8_t u8x8_byte_stm32_hw_spi(u8x8_t *u8x8, uint8_t msg, uint8_t arg_int, void *arg_ptr);

/*uint32_t VoltageTo8BitDacValue(float voltage)
{
    if ((voltage < 0.0f) || (voltage > 3.3f)) return 0;//todo: output error?

    uint32_t dacValue = (uint32_t) roundf(voltage * 255 / 3.3f);//todo: not the fastest with this function call... maybe come up with a better way eventually
    return dacValue;
}*/

int maincpp(void)
{
    u8g2_Setup_ssd1309_128x64_noname2_f(&u8g2, U8G2_R0, u8x8_byte_stm32_hw_spi,
            u8x8_stm32_gpio_and_delay);
    u8g2_InitDisplay(&u8g2);
    u8g2_SetPowerSave(&u8g2, 0);

    //https://github.com/olikraus/u8g2/wiki/fntlist8
    //u8g2_SetFont(&u8g2, u8g2_font_nokiafc22_tu);
    u8g2_SetFont(&u8g2, u8g2_font_ncenB14_tr);
    //u8g2_SetFont(&u8g2, u8g2_font_unifont_t_symbols);
    //char strBuffer[30];
    u8g2_DrawStr(&u8g2, 0, 15, "Hello World");
    u8g2_SendBuffer(&u8g2);

    uint8_t count = 0;
    char buffer[30];
    while(1){
        sprintf(buffer, "blah: %d\r\n", count);
        count++;
        HAL_UART_Transmit(&huart2, (uint8_t *)buffer, strlen(buffer), 1000);
        HAL_Delay(250);
    }

    return 0;
}
