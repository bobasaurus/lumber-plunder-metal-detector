/*
 * wavegeneration.h
 *
 *  Created on: Dec 31, 2023
 *      Author: Allen Jordan
 */

#ifndef INC_WAVE_GENERATION_H_
#define INC_WAVE_GENERATION_H_

#include "stm32f3xx.h"//contains the raw register/bit definitions (actually in stm32f303x8.h, open for reference)
#include "stm32f3xx_hal.h"
#include <math.h>

#include "main.h"//needed for externs

void SetupWaveGeneration();

#endif /* INC_WAVE_GENERATION_H_ */
