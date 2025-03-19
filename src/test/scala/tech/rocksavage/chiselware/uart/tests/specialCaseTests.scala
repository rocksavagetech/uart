package tech.rocksavage.chiselware.uart.tests

import chisel3._
import chiseltest._
import tech.rocksavage.chiselware.uart.hw.Uart
import tech.rocksavage.chiselware.uart.testconfig.UartFifoDataDirection._
import tech.rocksavage.chiselware.uart.testconfig.{
    UartData,
    UartFifoRxRuntimeConfig,
    UartFifoTxRuntimeConfig,
    UartTestConfig
}
import tech.rocksavage.chiselware.uart.testutils.rx.UartRxTestUtils.receive
import tech.rocksavage.chiselware.uart.testutils.tx.UartTxTestUtils.transmit
import tech.rocksavage.chiselware.uart.types.param.UartParams

object specialCaseTests {

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

            transmit(dut, config)
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

            receive(dut, config)
        }
    }

}
