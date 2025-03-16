package tech.rocksavage.chiselware.uart.tests

import chisel3._
import chiseltest._
import tech.rocksavage.chiselware.apb.ApbTestUtils._
import tech.rocksavage.chiselware.uart.param.UartParams
import tech.rocksavage.chiselware.uart.testutils.UartTestUtils.{
    setupUart,
    waitForDataAndVerify
}
import tech.rocksavage.chiselware.uart.testutils.fifo._
import tech.rocksavage.chiselware.uart.testutils.{
    UartTestUtils,
    UartTxFifoTestUtils
}
import tech.rocksavage.chiselware.uart.{FullDuplexUart, Uart}

import scala.util.Random

object randomTests {

    def iexp(base: Int, exp: Int): Int = {
        if (exp == 0) 1
        else base * iexp(base, exp - 1)
    }

    def randomTransmitTest(dut: Uart, params: UartParams): Unit = {
        implicit val clk: Clock = dut.clock
        dut.io.rx.poke(1.U)

        /** 2 exp 13, 2 exp 14, ... */
        val validClockFrequencies = Seq(
          13, 14, 15, 16, 17
        ).map(iexp(2, _))
        val validBaudRates = Seq(
          115_200, 230_400, 460_800, 921_600, 1_843_200, 3_686_400
        )
        val validDataBits = Seq(5, 6, 7, 8)

        val seed = Random.nextLong()

//        val seed = -706700778016793525L
        println(s"Random test seed: $seed")
        Random.setSeed(seed)

        for (_ <- 1 to 10) { // Test 10 random configurations
            val config = UartTestUtils.generateNextValidRandomConfig(
              validClockFrequencies,
              validBaudRates,
              validDataBits
            )

            println(
              s"Random tramsit transaction with configuration: \n$config"
            )

            // does all assertions that the behavior is correct
            UartTestUtils.transmit(dut, config)
        }
    }

    def randomFifoTransmitTest(dut: Uart, params: UartParams): Unit = {
        implicit val clk: Clock = dut.clock
        dut.io.rx.poke(1.U)

        /** 2 exp 13, 2 exp 14, ... */
        val validClockFrequencies = Seq(
          13, 14, 15, 16, 17
        ).map(iexp(2, _))
        val validBaudRates = Seq(
          115_200, 230_400, 460_800, 921_600, 1_843_200, 3_686_400
        )
        val validDataBits = Seq(5, 6, 7, 8)

//        val seed = Random.nextLong()

        val seed = 1117391718542493081L
        println(s"Random test seed: $seed")
        Random.setSeed(seed)

        for (_ <- 1 to 10) { // Test 10 random configurations
            val config =
                UartFifoConfigTestUtils.generateNextValidTxRandomConfig(
                  validClockFrequencies,
                  validBaudRates,
                  validDataBits,
                  fifoSize = 8
                )

            println(
              s"Random tramsit transaction with configuration: \n$config"
            )

            // does all assertions that the behavior is correct
            UartTxFifoTestUtils.transmit(dut, config)
        }
    }

    def randomReceiveTest(dut: Uart, params: UartParams): Unit = {
        implicit val clk: Clock = dut.clock
        dut.io.rx.poke(1.U)

        /** 2 exp 13, 2 exp 14, ... */
        val validClockFrequencies = Seq(
          13, 14, 15, 16, 17
        ).map(iexp(2, _))
        val validBaudRates = Seq(
          115_200, 230_400, 460_800, 921_600, 1_843_200, 3_686_400
        )
        val validDataBits = Seq(5, 6, 7, 8)

        val seed = Random.nextLong()

        println(s"Random test seed: $seed")
        Random.setSeed(seed)

        for (_ <- 1 to 10) { // Test 10 random configurations
            val config = UartTestUtils.generateNextValidRandomConfig(
              validClockFrequencies,
              validBaudRates,
              validDataBits
            )

            println(
              s"Random receive transaction with configuration: \n$config"
            )

            // does all assertions that the behavior is correct
            UartTestUtils.receive(dut, config)
        }
    }

    def randomFifoReceiveTest(dut: Uart, params: UartParams): Unit = {
        implicit val clk: Clock = dut.clock
        dut.io.rx.poke(1.U)

        /** 2 exp 13, 2 exp 14, ... */
        val validClockFrequencies = Seq(
          13, 14, 15, 16, 17
        ).map(iexp(2, _))
        val validBaudRates = Seq(
          115_200, 230_400, 460_800, 921_600, 1_843_200, 3_686_400
        )
        val validDataBits = Seq(5, 6, 7, 8)

        val seed = Random.nextLong()

        //        val seed = -706700778016793525L
        println(s"Random test seed: $seed")
        Random.setSeed(seed)

        for (_ <- 1 to 10) { // Test 10 random configurations
            val config =
                UartFifoConfigTestUtils.generateNextValidRxRandomConfig(
                  validClockFrequencies,
                  validBaudRates,
                  validDataBits,
                  fifoSize = 8
                )

            println(
              s"Random tramsit transaction with configuration: \n$config"
            )

            // does all assertions that the behavior is correct
            UartRxFifoTestUtils.receive(dut, config)
        }
    }

    /** Random Data Pattern Test Tests random data patterns including edge cases
      */
    def reverseByte(x: Int): Int = {
        (0 until 8).foldLeft(0) { (acc, i) =>
            // For each bit i in x, shift acc left and OR with that bit
            (acc << 1) | ((x >> i) & 1)
        }
    }

    /** Random Noise Injection Test Tests random noise patterns on the RX line
      */
    def randomNoiseTest(dut: FullDuplexUart, params: UartParams): Unit = {
        implicit val clk: Clock = dut.clock
        clk.setTimeout(10000)

        val clockFrequency = 25_000_000
        val baudRate       = 115_200

        val clocksPerBit  = clockFrequency / baudRate
        val numOutputBits = 8

        // Provide the baud rate
        setupUart(dut.io.uart1Apb, dut.getUart1, clockFrequency, baudRate)
        setupUart(dut.io.uart2Apb, dut.getUart2, clockFrequency, baudRate)

        for (_ <- 1 to 5) { // Run 5 noise pattern tests
            val testChar = Random.nextInt(128).toChar
            println(s"Testing noise immunity with char: $testChar")

            // Verify initial error state
            val initialError = readAPB(
              dut.io.uart2Apb,
              dut.getUart2.registerMap.getAddressOfRegister("error").get.U
            )
            assert(
              initialError.intValue == 0,
              "Error flags should be clear before test"
            )

            // Start transmission
            writeAPB(
              dut.io.uart1Apb,
              dut.getUart1.registerMap.getAddressOfRegister("tx_dataIn").get.U,
              testChar.toInt.U
            )
            writeAPB(
              dut.io.uart1Apb,
              dut.getUart1.registerMap.getAddressOfRegister("tx_load").get.U,
              true.B
            )
            clk.step(1)
            writeAPB(
              dut.io.uart1Apb,
              dut.getUart1.registerMap.getAddressOfRegister("tx_load").get.U,
              false.B
            )

            // Track noise injection
            var noiseInjections    = 0
            var totalNoiseDuration = 0
            var lastNoiseTime      = 0

            // Inject random noise with minimum duration
            val numNoisePulses = Random.nextInt(5) + 1 // 1 to 5 noise pulses
            for (_ <- 0 until numNoisePulses) {
                val noiseDuration = Random.nextInt(
                  clocksPerBit / 8
                ) + 1 // Ensure positive duration
                val noiseDelay = Random.nextInt(
                  clocksPerBit / 4
                ) + 1 // Ensure positive delay

                // Wait some time before injecting noise
                clk.step(noiseDelay)

                // Inject noise
                dut.io.uart2_rx.poke(Random.nextBoolean().B)
                clk.step(noiseDuration)
                dut.io.uart2_rx.poke(true.B) // Return to idle

                noiseInjections += 1
                totalNoiseDuration += noiseDuration
            }

            println(
              s"Injected $noiseInjections noise pulses, total duration: $totalNoiseDuration cycles"
            )

            // Extra delay for stability
            clk.step(clocksPerBit * 2)

            // Check for errors if noise was significant
            if (totalNoiseDuration > clocksPerBit / 2) {
                val errorStatus = readAPB(
                  dut.io.uart2Apb,
                  dut.getUart2.registerMap.getAddressOfRegister("error").get.U
                )
                assert(
                  errorStatus.intValue != 0,
                  "Expected error detection with significant noise"
                )
            } else {
                // Only verify data if noise wasn't too disruptive
                waitForDataAndVerify(
                  dut.io.uart2Apb,
                  dut.getUart2,
                  testChar.toInt
                )
            }

            // Verify return to idle state
            clk.step(clocksPerBit * 2)
            assert(
              dut.io.uart1_tx.peekBoolean(),
              "UART1 TX should return to idle"
            )
            assert(
              dut.io.uart2_tx.peekBoolean(),
              "UART2 TX should return to idle"
            )

            // Clear any errors before next test
            writeAPB(
              dut.io.uart2Apb,
              dut.getUart2.registerMap
                  .getAddressOfRegister("clearError")
                  .get
                  .U,
              true.B
            )
            clk.step(2)
            writeAPB(
              dut.io.uart2Apb,
              dut.getUart2.registerMap
                  .getAddressOfRegister("clearError")
                  .get
                  .U,
              false.B
            )
            clk.step(2)
        }
    }

    /** Random Parity Configuration Test Tests random combinations of parity
      * settings
      */
    def randomParityTest(dut: FullDuplexUart, params: UartParams): Unit = {
        implicit val clk: Clock = dut.clock
        clk.setTimeout(10000)

        val clockFrequency = 25_000_000
        val baudRate       = 115_200

        val clocksPerBit  = clockFrequency / baudRate
        val numOutputBits = 8

        for (_ <- 1 to 5) { // Test 5 random parity configurations
            val useParityDb = Random.nextBoolean()
            val parityOddDb = Random.nextBoolean()
            println(
              s"Testing with parity enabled: $useParityDb, odd parity: $parityOddDb"
            )

            // Clear any existing errors
            writeAPB(
              dut.io.uart1Apb,
              dut.getUart1.registerMap
                  .getAddressOfRegister("clearError")
                  .get
                  .U,
              true.B
            )
            writeAPB(
              dut.io.uart2Apb,
              dut.getUart2.registerMap
                  .getAddressOfRegister("clearError")
                  .get
                  .U,
              true.B
            )
            clk.step(2)
            writeAPB(
              dut.io.uart1Apb,
              dut.getUart1.registerMap
                  .getAddressOfRegister("clearError")
                  .get
                  .U,
              false.B
            )
            writeAPB(
              dut.io.uart2Apb,
              dut.getUart2.registerMap
                  .getAddressOfRegister("clearError")
                  .get
                  .U,
              false.B
            )
            clk.step(2)

            // Configure both UARTs
            setupUart(
              dut.io.uart1Apb,
              dut.getUart1,
              clockFrequency,
              baudRate,
              useParityDb,
              parityOddDb
            )
            setupUart(
              dut.io.uart2Apb,
              dut.getUart2,
              clockFrequency,
              baudRate,
              useParityDb,
              parityOddDb
            )
            clk.step(clocksPerBit * 2)

            // Verify parity configuration
            val uart1ParityEnabled = readAPB(
              dut.io.uart1Apb,
              dut.getUart1.registerMap
                  .getAddressOfRegister("tx_useParityDb")
                  .get
                  .U
            )
            val uart2ParityEnabled = readAPB(
              dut.io.uart2Apb,
              dut.getUart2.registerMap
                  .getAddressOfRegister("tx_useParityDb")
                  .get
                  .U
            )
            assert(
              uart1ParityEnabled.intValue == (if (useParityDb) 1 else 0),
              "UART1 parity configuration mismatch"
            )
            assert(
              uart2ParityEnabled.intValue == (if (useParityDb) 1 else 0),
              "UART2 parity configuration mismatch"
            )

            // Send random character
            val testChar = Random.nextInt(128).toChar
            println(s"Sending character: $testChar")

            // Send data
            writeAPB(
              dut.io.uart1Apb,
              dut.getUart1.registerMap.getAddressOfRegister("tx_dataIn").get.U,
              testChar.toInt.U
            )
            writeAPB(
              dut.io.uart1Apb,
              dut.getUart1.registerMap.getAddressOfRegister("tx_load").get.U,
              true.B
            )
            clk.step(1)
            writeAPB(
              dut.io.uart1Apb,
              dut.getUart1.registerMap.getAddressOfRegister("tx_load").get.U,
              false.B
            )

            // Monitor transmission
            var cycleCount = 0
            while (cycleCount < clocksPerBit * (if (useParityDb) 11 else 10)) {
                cycleCount += 1
                clk.step(1)
            }

            // Verify data and check for errors
            clk.step(clocksPerBit * 2) // Extra delay for stability
            waitForDataAndVerify(dut.io.uart2Apb, dut.getUart2, testChar.toInt)
        }
    }

}
