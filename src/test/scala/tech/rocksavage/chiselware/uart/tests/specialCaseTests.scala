package tech.rocksavage.chiselware.uart.tests

import chisel3._
import chiseltest._
import tech.rocksavage.chiselware.uart.param.UartParams
import tech.rocksavage.chiselware.uart.testutils.UartTestUtils
import tech.rocksavage.chiselware.uart.{Uart, UartRuntimeConfig}

object specialCaseTests {

    def specialCaseTransmitTests(dut: Uart, params: UartParams): Unit = {
        implicit val clk: Clock = dut.clock
        dut.io.rx.poke(1.U)

        val specialCases: Seq[UartRuntimeConfig] = Seq(
          UartRuntimeConfig(
            useAsserts = true,
            baudRate = 1_000_000,
            clockFrequency = 1_000_000,
            numOutputBits = 8,
            useParity = true,
            parityOdd = true,
            data = 254
          ),
          UartRuntimeConfig(
            useAsserts = true,
            baudRate = 115200,
            clockFrequency = 262144,
            numOutputBits = 8,
            useParity = true,
            parityOdd = false,
            data = 26
          )
        )

        for (config <- specialCases) {

            println(
              s"Receive transaction with configuration: \n$config"
            )

            UartTestUtils.receive(dut, config)
        }
    }

    def specialCaseReceiveTests(dut: Uart, params: UartParams): Unit = {
        implicit val clk: Clock = dut.clock
        dut.io.rx.poke(1.U)

        val specialCases: Seq[UartRuntimeConfig] = Seq(
          UartRuntimeConfig(
            useAsserts = true,
            baudRate = 1_000_000,
            clockFrequency = 1_000_000,
            numOutputBits = 8,
            useParity = true,
            parityOdd = true,
            data = 254
          )
        )

        for (config <- specialCases) {

            println(
              s"Receive transaction with configuration: \n$config"
            )

            UartTestUtils.receive(dut, config)
        }
    }

}
