package tech.rocksavage.chiselware.uart.tests

import chisel3._
import chiseltest._
import tech.rocksavage.chiselware.uart.hw.Uart
import tech.rocksavage.chiselware.uart.testutils.rx.UartRxTestUtils.receive
import tech.rocksavage.chiselware.uart.testutils.top.UartTestUtils.{
    generateNextValidRxRandomConfig,
    generateNextValidTxRandomConfig
}
import tech.rocksavage.chiselware.uart.testutils.tx.UartTxTestUtils.transmit
import tech.rocksavage.chiselware.uart.types.param.UartParams

import scala.math.pow
import scala.util.Random

object randomTests {

    def randomFifoTransmitTest(dut: Uart, params: UartParams): Unit = {
        implicit val clk: Clock = dut.clock
        dut.io.rx.poke(1.U)

        /** 2 exp 13, 2 exp 14, ... */
        val validClockFrequencies = Seq(
          13, 14, 15, 16, 17
        ).map(pow(2, _).toInt)
        val validBaudRates = Seq(
          115_200, 230_400, 460_800, 921_600, 1_843_200, 3_686_400
        )
        val validDataBits = Seq(5, 6, 7, 8)

        val seed = Random.nextLong()

//        val seed = 1117391718542493081L
        println(s"Random test seed: $seed")
        Random.setSeed(seed)

        for (_ <- 1 to 10) { // Test 10 random configurations
            val config =
                generateNextValidTxRandomConfig(
                  validClockFrequencies,
                  validBaudRates,
                  validDataBits,
                  fifoSize = 8
                )

            println(
              s"Random tramsit transaction with configuration: \n$config"
            )

            // does all assertions that the behavior is correct
            transmit(dut, config)
        }
    }

    def randomFifoReceiveTest(dut: Uart, params: UartParams): Unit = {
        implicit val clk: Clock = dut.clock
        dut.io.rx.poke(1.U)

        /** 2 exp 13, 2 exp 14, ... */
        val validClockFrequencies = Seq(
          13, 14, 15, 16, 17
        ).map(pow(2, _).toInt)
        val validBaudRates = Seq(
          115_200, 230_400, 460_800, 921_600, 1_843_200, 3_686_400
        )
        val validDataBits = Seq(5, 6, 7, 8)

        val seed = Random.nextLong()

//        val seed = 6176633847783885148L
        println(s"Random test seed: $seed")
        Random.setSeed(seed)

        for (_ <- 1 to 10) { // Test 10 random configurations
            val config =
                generateNextValidRxRandomConfig(
                  validClockFrequencies,
                  validBaudRates,
                  validDataBits,
                  fifoSize = 8
                )

            println(
              s"Random receive transaction with configuration: \n$config"
            )

            // does all assertions that the behavior is correct
            receive(dut, config)
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

    /** Random Parity Configuration Test Tests random combinations of parity
      * settings
      */
//    def randomParityTest(dut: FullDuplexUart, params: UartParams): Unit = {
//        implicit val clk: Clock = dut.clock
//        clk.setTimeout(10000)
//
//        val clockFrequency = 25_000_000
//        val baudRate       = 115_200
//
//        val clocksPerBit  = clockFrequency / (baudRate / 2)
//        val numOutputBits = 8
//
//        for (_ <- 1 to 5) { // Test 5 random parity configurations
//            val useParityDb = Random.nextBoolean()
//            val parityOddDb = Random.nextBoolean()
//            println(
//              s"Testing with parity enabled: $useParityDb, odd parity: $parityOddDb"
//            )
//
//            // Clear any existing errors
//            writeAPB(
//              dut.io.uart1Apb,
//              dut.getUart1.registerMap
//                  .getAddressOfRegister("clearError")
//                  .get
//                  .U,
//              true.B
//            )
//            writeAPB(
//              dut.io.uart2Apb,
//              dut.getUart2.registerMap
//                  .getAddressOfRegister("clearError")
//                  .get
//                  .U,
//              true.B
//            )
//            clk.step(2)
//            writeAPB(
//              dut.io.uart1Apb,
//              dut.getUart1.registerMap
//                  .getAddressOfRegister("clearError")
//                  .get
//                  .U,
//              false.B
//            )
//            writeAPB(
//              dut.io.uart2Apb,
//              dut.getUart2.registerMap
//                  .getAddressOfRegister("clearError")
//                  .get
//                  .U,
//              false.B
//            )
//            clk.step(2)
//
//            // Configure both UARTs
//            setupUart(
//              dut.io.uart1Apb,
//              dut.getUart1,
//              clockFrequency,
//              baudRate,
//              useParityDb,
//              parityOddDb
//            )
//            setupUart(
//              dut.io.uart2Apb,
//              dut.getUart2,
//              clockFrequency,
//              baudRate,
//              useParityDb,
//              parityOddDb
//            )
//            clk.step(clocksPerBit * 2)
//
//            // Verify parity configuration
//            val uart1ParityEnabled = readAPB(
//              dut.io.uart1Apb,
//              dut.getUart1.registerMap
//                  .getAddressOfRegister("tx_useParityDb")
//                  .get
//                  .U
//            )
//            val uart2ParityEnabled = readAPB(
//              dut.io.uart2Apb,
//              dut.getUart2.registerMap
//                  .getAddressOfRegister("tx_useParityDb")
//                  .get
//                  .U
//            )
//            assert(
//              uart1ParityEnabled.intValue == (if (useParityDb) 1 else 0),
//              "UART1 parity configuration mismatch"
//            )
//            assert(
//              uart2ParityEnabled.intValue == (if (useParityDb) 1 else 0),
//              "UART2 parity configuration mismatch"
//            )
//
//            // Send random character
//            val testChar = Random.nextInt(128).toChar
//            println(s"Sending character: $testChar")
//
//            // Send data
//            writeAPB(
//              dut.io.uart1Apb,
//              dut.getUart1.registerMap.getAddressOfRegister("tx_dataIn").get.U,
//              testChar.toInt.U
//            )
//            writeAPB(
//              dut.io.uart1Apb,
//              dut.getUart1.registerMap.getAddressOfRegister("tx_load").get.U,
//              true.B
//            )
//            clk.step(1)
//            writeAPB(
//              dut.io.uart1Apb,
//              dut.getUart1.registerMap.getAddressOfRegister("tx_load").get.U,
//              false.B
//            )
//
//            // Monitor transmission
//            var cycleCount = 0
//            while (cycleCount < clocksPerBit * (if (useParityDb) 11 else 10)) {
//                cycleCount += 1
//                clk.step(1)
//            }
//
//            // Verify data and check for errors
//            clk.step(clocksPerBit * 2) // Extra delay for stability
//            waitForDataAndVerify(dut.io.uart2Apb, dut.getUart2, testChar.toInt)
//        }
//    }

}
