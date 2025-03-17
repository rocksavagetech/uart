// (c) 2024 Rocksavage Technology, Inc.
// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart.testutils.fifo

import chisel3._
import chiseltest._
import tech.rocksavage.chiselware.apb.ApbTestUtils.{readAPB, writeAPB}
import tech.rocksavage.chiselware.uart._
import tech.rocksavage.chiselware.uart.testutils.UartTestUtils.rxSetBaudRate

import scala.math.BigInt.int2bigInt

object UartRxFifoTestUtils {

    def receive(dut: Uart, config: UartFifoRxRuntimeConfig)(implicit
        clock: Clock
    ): Unit = {
        println("Preparing to receive data")
        receiveSetup(dut, config)
        println(s"Receiving data: ${config.data}\n")
        val testFifo = scala.collection.mutable.Queue[(Int)]()
        for (data <- config.data) {
            clock.setTimeout(1000)
            println(s"Next Data Operation: $data")
            if (data.direction == UartFifoDataDirection.Push) {
                testFifo.enqueue(data.data)
                println(s"Data ($data) sent to RX")
                receivePush(dut, config, data.data)
            } else {
                val poppedData = testFifo.dequeue()
                println(s"Popping next data: ${poppedData} from RX: $testFifo")
                receivePop(dut, config, poppedData)
                println(s"Data popped from Fifo Successfully")
            }
        }
    }

    def receiveSetup(uart: Uart, config: UartFifoRxRuntimeConfig)(implicit
        clock: Clock
    ): Unit = {
        clock.setTimeout(1000)

        val clockFrequency = config.config.clockFrequency
        val baudRate       = config.config.baudRate
        val numOutputBits  = config.config.numOutputBits

        // Provide the baud rate
        rxSetBaudRate(uart, baudRate, clockFrequency)

        // Configure UART
        writeAPB(
          uart.io.apb,
          uart.registerMap.getAddressOfRegister("rx_numOutputBitsDb").get.U,
          numOutputBits.U
        )
        writeAPB(
          uart.io.apb,
          uart.registerMap.getAddressOfRegister("rx_useParityDb").get.U,
          config.config.useParity.B
        )
        writeAPB(
          uart.io.apb,
          uart.registerMap.getAddressOfRegister("rx_parityOddDb").get.U,
          config.config.parityOdd.B
        )
        writeAPB(
          uart.io.apb,
          uart.registerMap.getAddressOfRegister("rx_lsbFirst").get.U,
          config.config.lsbFirst.B
        )

        val foundNumOutputBits = readAPB(
          uart.io.apb,
          uart.registerMap.getAddressOfRegister("rx_numOutputBitsDb").get.U
        )
        val foundUseParity = readAPB(
          uart.io.apb,
          uart.registerMap.getAddressOfRegister("rx_useParityDb").get.U
        )
        val foundParityOdd = readAPB(
          uart.io.apb,
          uart.registerMap.getAddressOfRegister("rx_parityOddDb").get.U
        )
        val foundLsbFirst = readAPB(
          uart.io.apb,
          uart.registerMap.getAddressOfRegister("rx_lsbFirst").get.U
        )

        assert(
          foundNumOutputBits == numOutputBits,
          "numOutputBitsDb register not set correctly"
        )
        assert(
          (foundUseParity == 1) == config.config.useParity,
          "useParityDb register not set correctly"
        )
        assert(
          (foundParityOdd == 1) == config.config.parityOdd,
          "parityOddDb register not set correctly"
        )
        assert(
          (foundLsbFirst == 1) == config.config.lsbFirst,
          "lsbFirst register not set correctly"
        )
    }

    def receivePush(
        dut: Uart,
        config: UartFifoRxRuntimeConfig,
        dataSend: Int
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
//        val dataBits: Seq[Boolean] = (0 until numOutputBits).map { i =>
//            ((dataSend >> i) & 1) == 1
//        }.reverse
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
//            println(
//              s"Checking bit $index: expected ${bit._1} in state ${bit._2}"
//            )
            dut.io.rx.poke(bit._1.B)
            dut.clock.setTimeout(clocksPerBit + 1)
            dut.clock.step(clocksPerBit)
        }
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
