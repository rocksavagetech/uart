package tech.rocksavage.chiselware.uart.tests

import chisel3._
import chiseltest._
import tech.rocksavage.chiselware.apb.ApbBundle
import tech.rocksavage.chiselware.apb.ApbTestUtils._
import tech.rocksavage.chiselware.uart.param.UartParams
import tech.rocksavage.chiselware.uart.testutils.UartTestUtils
import tech.rocksavage.chiselware.uart.{FullDuplexUart, Uart, UartRuntimeConfig}

import scala.util.Random

object randomTests {

    def iexp(base: Int, exp: Int): Int = {
        if (exp == 0) 1
        else base * iexp(base, exp - 1)
    }

    /** Random Baud Rate Test Tests random valid baud rate configurations
      */
    def randomTest(dut: Uart, params: UartParams): Unit = {
        implicit val clk: Clock = dut.clock
        dut.io.rx.poke(1.U)

        /** 2 exp 13, 2 exp 14, ... */
        val validClockFrequencies = Seq(
          13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25
        ).map(iexp(2, _))
        val validBaudRates = Seq(
          9600, 19_200, 38_400, 57_600, 115_200, 230_400, 460_800, 921_600
        )
        val validDataBits = Seq(5, 6, 7, 8)

        for (_ <- 1 to 10) { // Test 10 random configurations
            val baudRate = validBaudRates(
              Random.nextInt(validBaudRates.length)
            )
            val clockFrequency = validClockFrequencies(
              Random.nextInt(validClockFrequencies.length)
            )
            val numOutputBits = validDataBits(
              Random.nextInt(validDataBits.length)
            )

            // truncate data to the number of bits
            val data = Random.nextInt(iexp(2, numOutputBits))

            val useParity = Random.nextBoolean()
            val parityOdd = Random.nextBoolean() && useParity

            println(
              s"Testing random baud rate with configuration: \nbaud rate = $baudRate, \nclock frequency = $clockFrequency, \ndata bits = $numOutputBits, \ndata = $data"
            )

            val config: UartRuntimeConfig = UartRuntimeConfig(
              baudRate = baudRate,
              clockFrequency = clockFrequency,
              numOutputBits = numOutputBits,
              data = data,
              useParity = useParity,
              parityOdd = parityOdd
            )

            // does all assertions that the behavior is correct
            UartTestUtils.transmit(dut, config)
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
              dut.getUart1.registerMap.getAddressOfRegister("dataIn").get.U,
              testChar.toInt.U
            )
            writeAPB(
              dut.io.uart1Apb,
              dut.getUart1.registerMap.getAddressOfRegister("load").get.U,
              true.B
            )
            clk.step(1)
            writeAPB(
              dut.io.uart1Apb,
              dut.getUart1.registerMap.getAddressOfRegister("load").get.U,
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
              dut.getUart2.registerMap.getAddressOfRegister("clearError").get.U,
              true.B
            )
            clk.step(2)
            writeAPB(
              dut.io.uart2Apb,
              dut.getUart2.registerMap.getAddressOfRegister("clearError").get.U,
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
              dut.getUart1.registerMap.getAddressOfRegister("clearError").get.U,
              true.B
            )
            writeAPB(
              dut.io.uart2Apb,
              dut.getUart2.registerMap.getAddressOfRegister("clearError").get.U,
              true.B
            )
            clk.step(2)
            writeAPB(
              dut.io.uart1Apb,
              dut.getUart1.registerMap.getAddressOfRegister("clearError").get.U,
              false.B
            )
            writeAPB(
              dut.io.uart2Apb,
              dut.getUart2.registerMap.getAddressOfRegister("clearError").get.U,
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
              dut.getUart1.registerMap.getAddressOfRegister("useParityDb").get.U
            )
            val uart2ParityEnabled = readAPB(
              dut.io.uart2Apb,
              dut.getUart2.registerMap.getAddressOfRegister("useParityDb").get.U
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
              dut.getUart1.registerMap.getAddressOfRegister("dataIn").get.U,
              testChar.toInt.U
            )
            writeAPB(
              dut.io.uart1Apb,
              dut.getUart1.registerMap.getAddressOfRegister("load").get.U,
              true.B
            )
            clk.step(1)
            writeAPB(
              dut.io.uart1Apb,
              dut.getUart1.registerMap.getAddressOfRegister("load").get.U,
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

    // Helper Functions
    private def setupUart(
        apb: ApbBundle,
        uart: Uart,
        clockFrequency: Int,
        baudRate: Int,
        useParity: Boolean = false,
        parityOdd: Boolean = false
    )(implicit clock: Clock): Unit = {

        // Seting up baud rate

        val baudRateAddr = uart.registerMap.getAddressOfRegister("baudRate").get
        val clockFreqAddr =
            uart.registerMap.getAddressOfRegister("clockFreq").get
        val updateBaudAddr =
            uart.registerMap.getAddressOfRegister("updateBaud").get

        writeAPB(apb, baudRateAddr.U, baudRate.U)
        writeAPB(apb, clockFreqAddr.U, clockFrequency.U)
        writeAPB(apb, updateBaudAddr.U, 1.U)
        clock.step(40)

        writeAPB(
          apb,
          uart.registerMap.getAddressOfRegister("numOutputBitsDb").get.U,
          8.U
        )
        writeAPB(
          apb,
          uart.registerMap.getAddressOfRegister("useParityDb").get.U,
          useParity.B
        )
        writeAPB(
          apb,
          uart.registerMap.getAddressOfRegister("parityOddDb").get.U,
          parityOdd.B
        )

        val actualNumBits = readAPB(
          apb,
          uart.registerMap.getAddressOfRegister("numOutputBitsDb").get.U
        )
        val actualUseParity = readAPB(
          apb,
          uart.registerMap.getAddressOfRegister("useParityDb").get.U
        )
        val actualParityOdd = readAPB(
          apb,
          uart.registerMap.getAddressOfRegister("parityOddDb").get.U
        )

        assert(
          actualNumBits == 8,
          s"NumOutputBits mismatch: expected 8, got ${actualNumBits}"
        )
        assert(
          actualUseParity == (if (useParity) 1 else 0),
          s"UseParity mismatch: expected $useParity, got ${actualUseParity}"
        )
        assert(
          actualParityOdd == (if (parityOdd) 1 else 0),
          s"ParityOdd mismatch: expected $parityOdd, got ${actualParityOdd}"
        )
    }

    private def waitForDataAndVerify(
        apb: ApbBundle,
        uart: Uart,
        expectedData: Int
    )(implicit clock: Clock): Unit = {
        var received    = false
        var timeout     = 0
        val maxTimeout  = 5000
        var dataValid   = false
        var validCycles = 0

        while (!received && timeout < maxTimeout) {
            val rxDataAvailable = readAPB(
              apb,
              uart.registerMap.getAddressOfRegister("rxDataAvailable").get.U
            )

            if (rxDataAvailable.intValue != 0) {
                validCycles += 1
                if (validCycles >= 2) {
                    dataValid = true
                }
            } else {
                validCycles = 0
            }

            if (dataValid) {
                val receivedData = readAPB(
                  apb,
                  uart.registerMap.getAddressOfRegister("rxData").get.U
                )
                assert(
                  receivedData == expectedData,
                  s"Data mismatch: expected ${expectedData}, got ${receivedData}"
                )

                val errorStatus = readAPB(
                  apb,
                  uart.registerMap.getAddressOfRegister("error").get.U
                )
                assert(
                  errorStatus == 0,
                  s"Unexpected error status: ${errorStatus}"
                )

                received = true
            }

            clock.step(1)
            timeout += 1
        }

        assert(received, s"Timeout waiting for data after $timeout cycles")
    }

    private def verifyIdleState(
        dut: FullDuplexUart
    )(implicit clock: Clock): Unit = {
        assert(dut.io.uart1_tx.peekBoolean(), "UART1 TX should be idle (high)")
        assert(dut.io.uart2_tx.peekBoolean(), "UART2 TX should be idle (high)")

        val uart1Error = readAPB(
          dut.io.uart1Apb,
          dut.getUart1.registerMap.getAddressOfRegister("error").get.U
        )
        val uart2Error = readAPB(
          dut.io.uart2Apb,
          dut.getUart2.registerMap.getAddressOfRegister("error").get.U
        )

        assert(uart1Error == 0, s"UART1 has unexpected errors: $uart1Error")
        assert(uart2Error == 0, s"UART2 has unexpected errors: $uart2Error")

        val uart1DataAvailable = readAPB(
          dut.io.uart1Apb,
          dut.getUart1.registerMap.getAddressOfRegister("rxDataAvailable").get.U
        )
        val uart2DataAvailable = readAPB(
          dut.io.uart2Apb,
          dut.getUart2.registerMap.getAddressOfRegister("rxDataAvailable").get.U
        )

        assert(
          uart1DataAvailable == 0,
          "UART1 should not have data available in idle state"
        )
        assert(
          uart2DataAvailable == 0,
          "UART2 should not have data available in idle state"
        )
    }

}
