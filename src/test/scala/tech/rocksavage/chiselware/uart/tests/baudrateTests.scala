package tech.rocksavage.chiselware.uart.tests

import chisel3._
import chiseltest._
import tech.rocksavage.chiselware.apb.ApbBundle
import tech.rocksavage.chiselware.apb.ApbTestUtils._
import tech.rocksavage.chiselware.uart.param.UartParams
import tech.rocksavage.chiselware.uart.testutils.UartTestUtils
import tech.rocksavage.chiselware.uart.{FullDuplexUart, Uart}

import scala.util.Random

object baudrateTests {

    def changeingBaudTest(dut: Uart, params: UartParams): Unit = {
        implicit val clk: Clock = dut.clock
        dut.io.rx.poke(1.U)

        val validClockFrequencies = Seq(
          5_000_000
        )
        val validBaudRates = Seq(
          230_400,
          460_800,
          921_600
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
              s"Testing random baud rate with configuration: \n$config"
            )

            // does all assertions that the behavior is correct
            UartTestUtils.transmit(dut, config)
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
