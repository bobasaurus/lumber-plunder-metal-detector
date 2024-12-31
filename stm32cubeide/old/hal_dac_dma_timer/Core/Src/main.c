/* USER CODE BEGIN Header */
/**
  ******************************************************************************
  * @file           : main.c
  * @brief          : Main program body
  ******************************************************************************
  * @attention
  *
  * Copyright (c) 2024 STMicroelectronics.
  * All rights reserved.
  *
  * This software is licensed under terms that can be found in the LICENSE file
  * in the root directory of this software component.
  * If no LICENSE file comes with this software, it is provided AS-IS.
  *
  ******************************************************************************
  */

//DAC2_OUT1 / pin 12 / PA6 (A5): unbuffered => AUDIO, timer 7 triggering
//note: the timer is considered an external trigger, so I think I need to use HAL_DAC_Start_DMA
//DMA config: DAC2_CH1, DMA1 channel 5, memory to peripheral, high priority
//"DMA request mapped on this DMA channel only if the corresponding remapping bit is set in the SYSCFG_CFGR1 or SYSCFGR3 register" (ref manual page 273), I think this is done already with the "__HAL_DMA_REMAP_CHANNEL_ENABLE" command

/* USER CODE END Header */
/* Includes ------------------------------------------------------------------*/
#include "main.h"

/* Private includes ----------------------------------------------------------*/
/* USER CODE BEGIN Includes */

/* USER CODE END Includes */

/* Private typedef -----------------------------------------------------------*/
/* USER CODE BEGIN PTD */

/* USER CODE END PTD */

/* Private define ------------------------------------------------------------*/
/* USER CODE BEGIN PD */

/* USER CODE END PD */

/* Private macro -------------------------------------------------------------*/
/* USER CODE BEGIN PM */

/* USER CODE END PM */

/* Private variables ---------------------------------------------------------*/
DAC_HandleTypeDef hdac2;
DMA_HandleTypeDef hdma_dac2_ch1;

TIM_HandleTypeDef htim7;

UART_HandleTypeDef huart2;

/* USER CODE BEGIN PV */

/* USER CODE END PV */

/* Private function prototypes -----------------------------------------------*/
void SystemClock_Config(void);
static void MX_GPIO_Init(void);
static void MX_DMA_Init(void);
static void MX_USART2_UART_Init(void);
static void MX_DAC2_Init(void);
static void MX_TIM7_Init(void);
/* USER CODE BEGIN PFP */

/* USER CODE END PFP */

/* Private user code ---------------------------------------------------------*/
/* USER CODE BEGIN 0 */

#define WAVE_BUFFER_CAPACITY 512

uint16_t waveAmplitude;//DAC counts ranging from [0,4095]
uint16_t waveFrequency;//[Hz]
uint16_t waveSize;//[points]
uint16_t waveBuffer[WAVE_BUFFER_CAPACITY];

uint16_t tempAmplitude;
uint16_t tempFrequency;
uint16_t tempSize;
uint16_t tempBuffer[WAVE_BUFFER_CAPACITY];//for double buffering

void GenerateWaveToTransmitInTempBuffer(uint16_t amplitudeValue, uint16_t frequencyValue)
{
	float amplitude = (float)amplitudeValue;//[counts, 4095 max], and wave will be offset so every value is above zero
	float frequency = (float)frequencyValue;//[Hz]
	float sampleRate = 64000000.0f/1.0f/6400.0f;//[samp/sec]
	float sampleTime = 1.0f/sampleRate;//[sec/sample]
	uint16_t samplesPerCycle = (uint16_t)roundf(1.0f/frequency * sampleRate);

	tempAmplitude = amplitudeValue;
	tempFrequency = frequencyValue;
	tempSize = samplesPerCycle;

	for (uint16_t i=0; i<samplesPerCycle; i++)
	{
		float timeS = (i+1) * sampleTime;
		float waveValue = amplitude * sinf(2*M_PI*frequency*timeS) + amplitude;
		if (waveValue < 0) waveValue = 0;//TODO: test and see if I can remove this
		if (waveValue > 4095) waveValue = 4095;
		tempBuffer[i] = (uint16_t)waveValue;
	}
}

void UpdateDACWaveAfterChange()
{
	HAL_DAC_Stop_DMA(&hdac2, DAC_CHANNEL_1);
	memcpy(waveBuffer, tempBuffer, sizeof(uint16_t) * tempSize);
	waveAmplitude = tempAmplitude;
	waveFrequency = tempFrequency;
	waveSize = tempSize;
	HAL_DAC_Start_DMA(&hdac2, DAC_CHANNEL_1, (uint32_t *)waveBuffer, waveSize, DAC_ALIGN_12B_R);
}

/* USER CODE END 0 */

/**
  * @brief  The application entry point.
  * @retval int
  */
int main(void)
{
  /* USER CODE BEGIN 1 */

  /* USER CODE END 1 */

  /* MCU Configuration--------------------------------------------------------*/

  /* Reset of all peripherals, Initializes the Flash interface and the Systick. */
  HAL_Init();

  /* USER CODE BEGIN Init */

  /* USER CODE END Init */

  /* Configure the system clock */
  SystemClock_Config();

  /* USER CODE BEGIN SysInit */

  /* USER CODE END SysInit */

  /* Initialize all configured peripherals */
  MX_GPIO_Init();
  MX_DMA_Init();
  MX_USART2_UART_Init();
  MX_DAC2_Init();
  MX_TIM7_Init();
  /* USER CODE BEGIN 2 */

  //HAL_DMA_Start_IT(&hdma_dac2_ch1, )
  //Enable the DAC channel using HAL_DAC_Start() or HAL_DAC_Start_DMA() functions
  //dac output voltage equation: DAC_OUTx = VREF+ * DOR / 4095

  GenerateWaveToTransmitInTempBuffer(300, 200);
  UpdateDACWaveAfterChange();

  //start the timer?
  //timer output rate = 8 MHz / prescaler / timermax, so default would be 8000000/1/65536 = 122.07 [update events per second, aka samples/sec]
  __HAL_RCC_TIM7_CLK_ENABLE();
  HAL_TIM_Base_Start(&htim7);   //HAL_TIM_Base_Start_DMA();  ???



  /* USER CODE END 2 */

  /* Infinite loop */
  /* USER CODE BEGIN WHILE */
  while (1)
  {
	  GPIO_PinState pinState0 = HAL_GPIO_ReadPin(SW0_GPIO_Port, SW0_Pin);
	  GPIO_PinState pinState1 = HAL_GPIO_ReadPin(SW1_GPIO_Port, SW1_Pin);
	  GPIO_PinState pinState8 = HAL_GPIO_ReadPin(SW8_GPIO_Port, SW8_Pin);
	  GPIO_PinState pinState7 = HAL_GPIO_ReadPin(SW7_GPIO_Port, SW7_Pin);

	  if (pinState0 == GPIO_PIN_RESET)
	  {
		  uint16_t newAmp = waveAmplitude + 10;
		  if (newAmp > 3000) newAmp = 3000;
		  GenerateWaveToTransmitInTempBuffer(newAmp, waveFrequency);
		  UpdateDACWaveAfterChange();

		  HAL_Delay(200);
	  }
	  if (pinState1 == GPIO_PIN_RESET)
	  {
		  int16_t newAmp = ((int16_t)waveAmplitude) - 10;
		  if (newAmp < 0) newAmp = 0;
		  GenerateWaveToTransmitInTempBuffer((uint16_t)newAmp, waveFrequency);
		  UpdateDACWaveAfterChange();

		  HAL_Delay(200);
	  }
	  if (pinState8 == GPIO_PIN_RESET)
	  {
		  uint16_t newFreq = waveFrequency + 20;
		  if (newFreq > 3000) newFreq = 3000;
		  GenerateWaveToTransmitInTempBuffer(waveAmplitude, newFreq);
		  UpdateDACWaveAfterChange();

		  HAL_Delay(200);
	  }
	  if (pinState7 == GPIO_PIN_RESET)
	  {
		  int16_t newFreq = ((int16_t)waveFrequency) - 20;
		  if (newFreq < 20) newFreq = 20;
		  GenerateWaveToTransmitInTempBuffer(waveAmplitude, (uint16_t)newFreq);
		  UpdateDACWaveAfterChange();

		  HAL_Delay(200);
	  }

    /* USER CODE END WHILE */

    /* USER CODE BEGIN 3 */
  }
  /* USER CODE END 3 */
}

/**
  * @brief System Clock Configuration
  * @retval None
  */
void SystemClock_Config(void)
{
  RCC_OscInitTypeDef RCC_OscInitStruct = {0};
  RCC_ClkInitTypeDef RCC_ClkInitStruct = {0};

  /** Initializes the RCC Oscillators according to the specified parameters
  * in the RCC_OscInitTypeDef structure.
  */
  RCC_OscInitStruct.OscillatorType = RCC_OSCILLATORTYPE_HSI;
  RCC_OscInitStruct.HSIState = RCC_HSI_ON;
  RCC_OscInitStruct.HSICalibrationValue = RCC_HSICALIBRATION_DEFAULT;
  RCC_OscInitStruct.PLL.PLLState = RCC_PLL_ON;
  RCC_OscInitStruct.PLL.PLLSource = RCC_PLLSOURCE_HSI;
  RCC_OscInitStruct.PLL.PLLMUL = RCC_PLL_MUL16;
  if (HAL_RCC_OscConfig(&RCC_OscInitStruct) != HAL_OK)
  {
    Error_Handler();
  }

  /** Initializes the CPU, AHB and APB buses clocks
  */
  RCC_ClkInitStruct.ClockType = RCC_CLOCKTYPE_HCLK|RCC_CLOCKTYPE_SYSCLK
                              |RCC_CLOCKTYPE_PCLK1|RCC_CLOCKTYPE_PCLK2;
  RCC_ClkInitStruct.SYSCLKSource = RCC_SYSCLKSOURCE_PLLCLK;
  RCC_ClkInitStruct.AHBCLKDivider = RCC_SYSCLK_DIV1;
  RCC_ClkInitStruct.APB1CLKDivider = RCC_HCLK_DIV2;
  RCC_ClkInitStruct.APB2CLKDivider = RCC_HCLK_DIV1;

  if (HAL_RCC_ClockConfig(&RCC_ClkInitStruct, FLASH_LATENCY_2) != HAL_OK)
  {
    Error_Handler();
  }
}

/**
  * @brief DAC2 Initialization Function
  * @param None
  * @retval None
  */
static void MX_DAC2_Init(void)
{

  /* USER CODE BEGIN DAC2_Init 0 */

  /* USER CODE END DAC2_Init 0 */

  DAC_ChannelConfTypeDef sConfig = {0};

  /* USER CODE BEGIN DAC2_Init 1 */

  /* USER CODE END DAC2_Init 1 */

  /** DAC Initialization
  */
  hdac2.Instance = DAC2;
  if (HAL_DAC_Init(&hdac2) != HAL_OK)
  {
    Error_Handler();
  }

  /** DAC channel OUT1 config
  */
  sConfig.DAC_Trigger = DAC_TRIGGER_T7_TRGO;
  sConfig.DAC_OutputSwitch = DAC_OUTPUTSWITCH_ENABLE;
  if (HAL_DAC_ConfigChannel(&hdac2, &sConfig, DAC_CHANNEL_1) != HAL_OK)
  {
    Error_Handler();
  }
  /* USER CODE BEGIN DAC2_Init 2 */

  /* USER CODE END DAC2_Init 2 */

}

/**
  * @brief TIM7 Initialization Function
  * @param None
  * @retval None
  */
static void MX_TIM7_Init(void)
{

  /* USER CODE BEGIN TIM7_Init 0 */

  /* USER CODE END TIM7_Init 0 */

  TIM_MasterConfigTypeDef sMasterConfig = {0};

  /* USER CODE BEGIN TIM7_Init 1 */

  /* USER CODE END TIM7_Init 1 */
  htim7.Instance = TIM7;
  htim7.Init.Prescaler = 0;
  htim7.Init.CounterMode = TIM_COUNTERMODE_UP;
  htim7.Init.Period = 6400;
  htim7.Init.AutoReloadPreload = TIM_AUTORELOAD_PRELOAD_DISABLE;
  if (HAL_TIM_Base_Init(&htim7) != HAL_OK)
  {
    Error_Handler();
  }
  sMasterConfig.MasterOutputTrigger = TIM_TRGO_UPDATE;
  sMasterConfig.MasterSlaveMode = TIM_MASTERSLAVEMODE_DISABLE;
  if (HAL_TIMEx_MasterConfigSynchronization(&htim7, &sMasterConfig) != HAL_OK)
  {
    Error_Handler();
  }
  /* USER CODE BEGIN TIM7_Init 2 */

  /* USER CODE END TIM7_Init 2 */

}

/**
  * @brief USART2 Initialization Function
  * @param None
  * @retval None
  */
static void MX_USART2_UART_Init(void)
{

  /* USER CODE BEGIN USART2_Init 0 */

  /* USER CODE END USART2_Init 0 */

  /* USER CODE BEGIN USART2_Init 1 */

  /* USER CODE END USART2_Init 1 */
  huart2.Instance = USART2;
  huart2.Init.BaudRate = 38400;
  huart2.Init.WordLength = UART_WORDLENGTH_8B;
  huart2.Init.StopBits = UART_STOPBITS_1;
  huart2.Init.Parity = UART_PARITY_NONE;
  huart2.Init.Mode = UART_MODE_TX_RX;
  huart2.Init.HwFlowCtl = UART_HWCONTROL_NONE;
  huart2.Init.OverSampling = UART_OVERSAMPLING_16;
  huart2.Init.OneBitSampling = UART_ONE_BIT_SAMPLE_DISABLE;
  huart2.AdvancedInit.AdvFeatureInit = UART_ADVFEATURE_NO_INIT;
  if (HAL_UART_Init(&huart2) != HAL_OK)
  {
    Error_Handler();
  }
  /* USER CODE BEGIN USART2_Init 2 */

  /* USER CODE END USART2_Init 2 */

}

/**
  * Enable DMA controller clock
  */
static void MX_DMA_Init(void)
{

  /* DMA controller clock enable */
  __HAL_RCC_DMA1_CLK_ENABLE();

  /* DMA interrupt init */
  /* DMA1_Channel5_IRQn interrupt configuration */
  HAL_NVIC_SetPriority(DMA1_Channel5_IRQn, 0, 0);
  HAL_NVIC_EnableIRQ(DMA1_Channel5_IRQn);

}

/**
  * @brief GPIO Initialization Function
  * @param None
  * @retval None
  */
static void MX_GPIO_Init(void)
{
  GPIO_InitTypeDef GPIO_InitStruct = {0};
/* USER CODE BEGIN MX_GPIO_Init_1 */
/* USER CODE END MX_GPIO_Init_1 */

  /* GPIO Ports Clock Enable */
  __HAL_RCC_GPIOF_CLK_ENABLE();
  __HAL_RCC_GPIOA_CLK_ENABLE();
  __HAL_RCC_GPIOB_CLK_ENABLE();

  /*Configure GPIO pins : SW8_Pin SW7_Pin */
  GPIO_InitStruct.Pin = SW8_Pin|SW7_Pin;
  GPIO_InitStruct.Mode = GPIO_MODE_INPUT;
  GPIO_InitStruct.Pull = GPIO_NOPULL;
  HAL_GPIO_Init(GPIOA, &GPIO_InitStruct);

  /*Configure GPIO pins : SW0_Pin SW1_Pin */
  GPIO_InitStruct.Pin = SW0_Pin|SW1_Pin;
  GPIO_InitStruct.Mode = GPIO_MODE_INPUT;
  GPIO_InitStruct.Pull = GPIO_NOPULL;
  HAL_GPIO_Init(GPIOB, &GPIO_InitStruct);

/* USER CODE BEGIN MX_GPIO_Init_2 */
/* USER CODE END MX_GPIO_Init_2 */
}

/* USER CODE BEGIN 4 */

/* USER CODE END 4 */

/**
  * @brief  This function is executed in case of error occurrence.
  * @retval None
  */
void Error_Handler(void)
{
  /* USER CODE BEGIN Error_Handler_Debug */
  /* User can add his own implementation to report the HAL error return state */
  __disable_irq();
  while (1)
  {
  }
  /* USER CODE END Error_Handler_Debug */
}

#ifdef  USE_FULL_ASSERT
/**
  * @brief  Reports the name of the source file and the source line number
  *         where the assert_param error has occurred.
  * @param  file: pointer to the source file name
  * @param  line: assert_param error line source number
  * @retval None
  */
void assert_failed(uint8_t *file, uint32_t line)
{
  /* USER CODE BEGIN 6 */
  /* User can add his own implementation to report the file name and line number,
     ex: printf("Wrong parameters value: file %s on line %d\r\n", file, line) */
  /* USER CODE END 6 */
}
#endif /* USE_FULL_ASSERT */
