package tech.rocksavage.chiselware.uart.tests

import chisel3._
import chiseltest._
import tech.rocksavage.chiselware.uart.UartFifoDataDirection._
import tech.rocksavage.chiselware.uart._
import tech.rocksavage.chiselware.uart.param.UartParams
import tech.rocksavage.chiselware.uart.testutils.{
    UartFifoTestUtils,
    UartTestUtils
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

        val specialCases: Seq[UartFifoRuntimeConfig] = Seq(
//          UartFifoRuntimeConfig(
//            useAsserts = true,
//            config = UartTestConfig(
//              baudRate = 100_000,
//              clockFrequency = 100_000,
//              numOutputBits = 6,
//              useParity = false,
//              parityOdd = false
//            ),
//            data = Seq(
//              new UartData(
//                data = 43,
//                direction = UartFifoDataDirection.Push
//              ),
//              new UartData(
//                data = 43,
//                direction = UartFifoDataDirection.Pop
//              )
//            )
//          )
          UartFifoRuntimeConfig(
            useAsserts = true,
            config = UartTestConfig(100_000, 100_000, 8, true, true, 16),
            data = List(
              new UartData(data = 255, direction = Push),
              new UartData(data = 196, direction = Push),
              new UartData(data = 115, direction = Push),
              new UartData(data = 67, direction = Pop),
              new UartData(data = 102, direction = Pop),
              new UartData(data = 169, direction = Pop),
              new UartData(data = 196, direction = Push),
              new UartData(data = 207, direction = Push),
              new UartData(data = 137, direction = Push),
              new UartData(data = 19, direction = Pop),
              new UartData(data = 230, direction = Pop),
              new UartData(data = 17, direction = Push),
              new UartData(data = 95, direction = Push),
              new UartData(data = 116, direction = Push),
              new UartData(data = 140, direction = Pop),
              new UartData(data = 202, direction = Push)
            )
          ),
          UartFifoRuntimeConfig(
            useAsserts = true,
            config = UartTestConfig(100_000, 1_000_000, 8, true, true, 16),
            data = List(
              new UartData(data = 255, direction = Push),
              new UartData(data = 196, direction = Push),
              new UartData(data = 115, direction = Push),
              new UartData(data = 67, direction = Pop),
              new UartData(data = 102, direction = Pop),
              new UartData(data = 169, direction = Pop),
              new UartData(data = 196, direction = Push),
              new UartData(data = 207, direction = Push),
              new UartData(data = 137, direction = Push),
              new UartData(data = 19, direction = Pop),
              new UartData(data = 230, direction = Pop),
              new UartData(data = 17, direction = Push),
              new UartData(data = 95, direction = Push),
              new UartData(data = 116, direction = Push),
              new UartData(data = 140, direction = Pop),
              new UartData(data = 202, direction = Push)
            )
          )
        )

        for (config <- specialCases) {

            println(
              s"Receive transaction with configuration: \n$config"
            )

            UartFifoTestUtils.transmit(dut, config)
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

}
