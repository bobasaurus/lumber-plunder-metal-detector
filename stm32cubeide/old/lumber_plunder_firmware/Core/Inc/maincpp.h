/*
 * maincpp.h
 *
 *  Created on: Nov 7, 2023
 *      Author: blah
 */

#ifndef INC_MAINCPP_H_
#define INC_MAINCPP_H_

#include "stm32f3xx_hal.h"
#include "stm32f3xx_ll_adc.h"
#include <math.h>
#include <string.h>

#include "printf.h"
#include "main.h"
#include "wave_generation.h"

extern "C" int maincpp(void);

#endif /* INC_MAINCPP_H_ */
