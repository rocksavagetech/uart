package tech.rocksavage.chiselware.uart.tests

import chisel3._
import chiseltest._
import tech.rocksavage.chiselware.uart.UartFifoDataDirection._
import tech.rocksavage.chiselware.uart._
import tech.rocksavage.chiselware.uart.param.UartParams
import tech.rocksavage.chiselware.uart.testutils.fifo.UartRxFifoTestUtils
import tech.rocksavage.chiselware.uart.testutils.{
    UartTestUtils,
    UartTxFifoTestUtils
}

object specialCaseTests {

    def specialCaseTransmitTests(dut: Uart, params: UartParams): Unit = {
        implicit val clk: Clock = dut.clock
        dut.io.rx.poke(1.U)

        val specialCases: Seq[UartRuntimeConfig] = Seq(
          UartRuntimeConfig(
            useAsserts = true,
            baudRate = 460800,
            clockFrequency = 5000000,
            numOutputBits = 6,
            useParity = false,
            parityOdd = false,
            data = 43
          ),
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
          ),
          UartRuntimeConfig(
            useAsserts = true,
            baudRate = 921600,
            clockFrequency = 5000000,
            numOutputBits = 7,
            useParity = false,
            parityOdd = false,
            data = 21
          )
        )

        for (config <- specialCases) {

            println(
              s"Receive transaction with configuration: \n$config"
            )

            UartTestUtils.transmit(dut, config)
        }
    }

    def specialCaseFifoTransmitTests(dut: Uart, params: UartParams): Unit = {
        implicit val clk: Clock = dut.clock
        dut.io.rx.poke(1.U)

        val specialCases: Seq[UartFifoTxRuntimeConfig] = Seq(
          UartFifoTxRuntimeConfig(
            useAsserts = true,
            config = UartTestConfig(100_000, 100_000, 8, true, true, 16),
            data = List(
              new UartData(data = 255, direction = Push),
              new UartData(data = 196, direction = Push),
              new UartData(data = 115, direction = Push),
              new UartData(data = 67, direction = Pop),
              new UartData(data = 196, direction = Push),
              new UartData(data = 207, direction = Push),
              new UartData(data = 137, direction = Push),
              new UartData(data = 19, direction = Pop),
              new UartData(data = 17, direction = Push),
              new UartData(data = 95, direction = Push),
              new UartData(data = 116, direction = Push),
              new UartData(data = 140, direction = Pop),
              new UartData(data = 202, direction = Push),
              new UartData(data = 255, direction = Pop)
            )
          ),
          UartFifoTxRuntimeConfig(
            useAsserts = true,
            config = UartTestConfig(100_000, 1_000_000, 8, true, true, 16),
            data = List(
              new UartData(data = 255, direction = Push),
              new UartData(data = 196, direction = Push),
              new UartData(data = 115, direction = Push),
              new UartData(data = 67, direction = Pop),
              new UartData(data = 196, direction = Push),
              new UartData(data = 207, direction = Push),
              new UartData(data = 137, direction = Push),
              new UartData(data = 19, direction = Pop),
              new UartData(data = 17, direction = Push),
              new UartData(data = 95, direction = Push),
              new UartData(data = 116, direction = Push),
              new UartData(data = 140, direction = Pop),
              new UartData(data = 202, direction = Push),
              new UartData(data = 255, direction = Pop)
            )
          )
        )

        for (config <- specialCases) {

            println(
              s"Receive transaction with configuration: \n$config"
            )

            UartTxFifoTestUtils.transmit(dut, config)
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
          ),
          UartRuntimeConfig(
            useAsserts = true,
            baudRate = 100_000,
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

    def specialCaseFifoReceiveTests(dut: Uart, params: UartParams): Unit = {
        implicit val clk: Clock = dut.clock
        dut.io.rx.poke(1.U)

        val specialCases: Seq[UartFifoRxRuntimeConfig] = Seq(
          UartFifoRxRuntimeConfig(
            useAsserts = true,
            config = UartTestConfig(100_000, 100_000, 8, true, true, 16),
            data = List(
              new UartData(data = 255, direction = Push),
              new UartData(data = 196, direction = Push),
              new UartData(data = 115, direction = Push),
              new UartData(data = 67, direction = Pop),
              new UartData(data = 196, direction = Push),
              new UartData(data = 207, direction = Push),
              new UartData(data = 137, direction = Push),
              new UartData(data = 19, direction = Pop),
              new UartData(data = 17, direction = Push),
              new UartData(data = 95, direction = Push),
              new UartData(data = 116, direction = Push),
              new UartData(data = 140, direction = Pop),
              new UartData(data = 202, direction = Push),
              new UartData(data = 255, direction = Pop),
              new UartData(data = 67, direction = Pop),
              new UartData(data = 67, direction = Pop),
              new UartData(data = 67, direction = Pop),
              new UartData(data = 67, direction = Pop),
              new UartData(data = 67, direction = Pop),
              new UartData(data = 67, direction = Pop)
            )
          ),
          UartFifoRxRuntimeConfig(
            useAsserts = true,
            config = UartTestConfig(100_000, 1_000_000, 8, true, true, 16),
            data = List(
              new UartData(data = 255, direction = Push),
              new UartData(data = 196, direction = Push),
              new UartData(data = 115, direction = Push),
              new UartData(data = 67, direction = Pop),
              new UartData(data = 196, direction = Push),
              new UartData(data = 207, direction = Push),
              new UartData(data = 137, direction = Push),
              new UartData(data = 19, direction = Pop),
              new UartData(data = 17, direction = Push),
              new UartData(data = 95, direction = Push),
              new UartData(data = 116, direction = Push),
              new UartData(data = 140, direction = Pop),
              new UartData(data = 202, direction = Push),
              new UartData(data = 255, direction = Pop),
              new UartData(data = 67, direction = Pop),
              new UartData(data = 67, direction = Pop),
              new UartData(data = 67, direction = Pop),
              new UartData(data = 67, direction = Pop),
              new UartData(data = 67, direction = Pop),
              new UartData(data = 67, direction = Pop)
            )
          ),
          UartFifoRxRuntimeConfig(
            useAsserts = true,
            config = UartTestConfig(100_000, 100_000, 8, true, true, 16),
            data = List(
              new UartData(data = 255, direction = Push),
              new UartData(data = 196, direction = Push),
              new UartData(data = 115, direction = Push),
              new UartData(data = 67, direction = Pop),
              new UartData(data = 196, direction = Push),
              new UartData(data = 207, direction = Push),
              new UartData(data = 67, direction = Pop),
              new UartData(data = 137, direction = Push),
              new UartData(data = 19, direction = Pop),
              new UartData(data = 67, direction = Pop),
              new UartData(data = 67, direction = Pop),
              new UartData(data = 17, direction = Push),
              new UartData(data = 95, direction = Push),
              new UartData(data = 116, direction = Push),
              new UartData(data = 140, direction = Pop),
              new UartData(data = 67, direction = Pop),
              new UartData(data = 202, direction = Push),
              new UartData(data = 255, direction = Pop),
              new UartData(data = 67, direction = Pop),
              new UartData(data = 67, direction = Pop)
            )
          ),
          UartFifoRxRuntimeConfig(
            useAsserts = true,
            config = UartTestConfig(100_000, 1_000_000, 8, true, true, 16),
            data = List(
              new UartData(data = 255, direction = Push),
              new UartData(data = 196, direction = Push),
              new UartData(data = 115, direction = Push),
              new UartData(data = 67, direction = Pop),
              new UartData(data = 196, direction = Push),
              new UartData(data = 207, direction = Push),
              new UartData(data = 67, direction = Pop),
              new UartData(data = 137, direction = Push),
              new UartData(data = 19, direction = Pop),
              new UartData(data = 67, direction = Pop),
              new UartData(data = 67, direction = Pop),
              new UartData(data = 17, direction = Push),
              new UartData(data = 95, direction = Push),
              new UartData(data = 116, direction = Push),
              new UartData(data = 140, direction = Pop),
              new UartData(data = 67, direction = Pop),
              new UartData(data = 202, direction = Push),
              new UartData(data = 255, direction = Pop),
              new UartData(data = 67, direction = Pop),
              new UartData(data = 67, direction = Pop)
            )
          )
        )

        for (config <- specialCases) {

            println(
              s"Receive transaction with configuration: \n$config"
            )

            UartRxFifoTestUtils.receive(dut, config)
        }
    }

}
