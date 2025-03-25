// (c) 2024 Rocksavage Technology, Inc.
// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart.testutils.rx

import chisel3._
import chiseltest._
import tech.rocksavage.chiselware.apb.ApbTestUtils.readAPB
import tech.rocksavage.chiselware.uart.UartParity
import tech.rocksavage.chiselware.uart.hw.Uart
import tech.rocksavage.chiselware.uart.testconfig.{
    UartFifoDataDirection,
    UartFifoRxRuntimeConfig
}
import tech.rocksavage.chiselware.uart.testutils.rx.UartRxSetupTestUtils.receiveSetup
import tech.rocksavage.chiselware.uart.types.enums.UartState

import scala.math.BigInt.int2bigInt

object UartRxTestUtils {

    def receive(dut: Uart, config: UartFifoRxRuntimeConfig)(implicit
        clock: Clock
    ): Unit = {
        println("Preparing to receive data")
        receiveSetup(dut.registerMap, dut.io.apb, config.config)
        println(s"Receiving data: ${config.data}\n")
        val testFifo = scala.collection.mutable.Queue[(Int)]()
        for (data <- config.data) {
            clock.setTimeout(1000)
            println(s"Next Data Operation: $data")
            if (data.direction == UartFifoDataDirection.Push) {
                testFifo.enqueue(data.data)
                println(s"Data ($data) sent to RX")
                receivePush(
                  dut,
                  config,
                  data.data,
                  dataFirst = testFifo.size == 1
                )
            } else {
                val poppedData = testFifo.dequeue()
                println(s"Popping next data: ${poppedData} from RX: $testFifo")
                receivePop(dut, config, poppedData)
                println(s"Data popped from Fifo Successfully")
            }
            val actualAlmostFull = readAPB(
              dut.io.apb,
              dut.registerMap.getAddressOfRegister("rx_fifoAlmostFull").get.U
            )
            val actualAlmostEmpty = readAPB(
              dut.io.apb,
              dut.registerMap.getAddressOfRegister("rx_fifoAlmostEmpty").get.U
            )
            if (testFifo.size >= config.config.almostFullLevel) {
                assert(
                  actualAlmostFull == 1,
                  "TX almostFull should be 1 when the fifo is almost full"
                )
            } else {
                assert(
                  actualAlmostFull == 0,
                  "TX almostFull should be 0 when the fifo is not almost full"
                )
            }
            if (testFifo.size <= config.config.almostEmptyLevel) {
                assert(
                  actualAlmostEmpty == 1,
                  "TX almostEmpty should be 1 when the fifo is almost empty"
                )
            } else {
                assert(
                  actualAlmostEmpty == 0,
                  "TX almostEmpty should be 0 when the fifo is not almost empty"
                )
            }
        }
    }

    def receivePush(
        dut: Uart,
        config: UartFifoRxRuntimeConfig,
        dataSend: Int,
        dataFirst: Boolean = false
    )(implicit
        clock: Clock
    ): Unit = {
        val numOutputBits  = config.config.numOutputBits
        val clockFrequency = config.config.clockFrequency
        val baudRate       = config.config.baudRate
        val clocksPerBit   = clockFrequency / (baudRate / 2)
        val useParity      = config.config.useParity
        val parityOdd      = config.config.parityOdd

        println(s"RX receiving data: $dataSend")
        // assert that the data is within the range of 0 to 255
        assert(
          dataSend >= 0 && dataSend <= 2.pow(numOutputBits) - 1,
          "Data must be in the range 0 to 255"
        )

        // msbFirst
        val dataBits: Seq[Boolean] = if (config.config.lsbFirst) {
            (0 until numOutputBits).map { i =>
                ((dataSend >> i) & 1) == 1
            }
        } else {
            (0 until numOutputBits).map { i =>
                ((dataSend >> i) & 1) == 1
            }.reverse
        }

        // Build the expected sequence
        // match on useParity
        val sequence: Seq[(Boolean, UartState.Type)] =
            if (useParity) {
                val parityBit = UartParity.parity(dataSend, parityOdd)
                Seq((false, UartState.Start)) ++ dataBits.map(
                  (_, UartState.Data)
                ) ++ Seq((parityBit, UartState.Parity)) ++ Seq(
                  (true, UartState.Stop)
                )
            } else {
                Seq((false, UartState.Start)) ++ dataBits.map(
                  (_, UartState.Data)
                ) ++ Seq((true, UartState.Stop))
            }

        // Start transmission
        // Check each bit with a timeout
        for ((bit, index) <- sequence.zipWithIndex) {
            dut.io.rx.poke(bit._1.B)
            dut.clock.setTimeout(clocksPerBit + 1)
            dut.clock.step(clocksPerBit)
        }
        clock.setTimeout(100)
        fork {
            clock.step(2)
            if (dataFirst) {
                dut.io.interrupts.dataReceived.expect(true.B)
            } else {
                dut.io.interrupts.dataReceived.expect(false.B)
            }
        }.joinAndStep(clock)
    }

    def receivePop(
        dut: Uart,
        config: UartFifoRxRuntimeConfig,
        dataExpect: Int
    )(implicit
        clock: Clock
    ): Unit = {
        // Verify final state
        val dataAvailableActual = readAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("rx_dataAvailable").get.U
        )
        assert(
          dataAvailableActual == 1,
          "RX data should be available after transmission"
        )
        val dataActual = readAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("rx_data").get.U
        )
        assert(
          dataActual == dataExpect,
          s"RX data should match transmitted data: expected $dataExpect, got $dataActual"
        )
        val errorStatusActual = readAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("error").get.U
        )
        assert(
          errorStatusActual == 0,
          "RX error should be 0"
        )
    }

}
