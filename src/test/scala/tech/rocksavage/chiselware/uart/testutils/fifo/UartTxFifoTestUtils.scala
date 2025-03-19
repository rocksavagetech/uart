// (c) 2024 Rocksavage Technology, Inc.
// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart.testutils

import chisel3._
import chiseltest._
import tech.rocksavage.chiselware.apb.ApbTestUtils.{
    readAPB,
    writeAPB,
    writeApbNoDelay
}
import tech.rocksavage.chiselware.uart._
import tech.rocksavage.chiselware.uart.testutils.UartTestUtils.txSetBaudRate

import scala.math.BigInt.int2bigInt

object UartTxFifoTestUtils {

    def transmit(dut: Uart, config: UartFifoTxRuntimeConfig)(implicit
        clock: Clock
    ): Unit = {
        println("Preparing to transmit data")
        transmitSetup(dut, config)
        println(s"Transmitting data: ${config.data}\n")
        val testFifo = scala.collection.mutable.Queue[(Int)]()
        for (data <- config.data) {

            clock.setTimeout(1000)
            println(s"Next Data Operation: $data")

            if (data.direction == UartFifoDataDirection.Push) {
                testFifo.enqueue(data.data)
                println(s"Data ($data) queued to Fifo: $testFifo")
                transmitPush(dut, config, data.data)
            } else {
                println(s"Popping all data from Fifo: $testFifo")
                transmitPop(dut, config, testFifo)
                println(s"Data popped from Fifo Successfully")
            }
            clock.setTimeout(100)
            // depending on the fill level, we should expect the fill level indicators to be correct
            val actualAlmostFull = readAPB(
              dut.io.apb,
              dut.registerMap.getAddressOfRegister("tx_fifoAlmostFull").get.U
            )
            val actualAlmostEmpty = readAPB(
              dut.io.apb,
              dut.registerMap.getAddressOfRegister("tx_fifoAlmostEmpty").get.U
            )
            if (testFifo.size >= config.config.almostFullLevel) {
                assert(
                  actualAlmostFull == 1,
                  s"TX almostFull should be 1 when the fifo is almost full"
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

    def transmitSetup(dut: Uart, config: UartFifoTxRuntimeConfig)(implicit
        clock: Clock
    ): Unit = {
        clock.setTimeout(1000)

        val clockFrequency = config.config.clockFrequency
        val baudRate       = config.config.baudRate

        val numOutputBits = config.config.numOutputBits

        // Provide the baud rate
        txSetBaudRate(dut, baudRate, clockFrequency)

        // Configure UART
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("tx_numOutputBitsDb").get.U,
          numOutputBits.U
        )
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("tx_useParityDb").get.U,
          config.config.useParity.B
        )
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("tx_parityOddDb").get.U,
          config.config.parityOdd.B
        )
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("tx_almostFullLevel").get.U,
          config.config.almostFullLevel.U
        )
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("tx_almostEmptyLevel").get.U,
          config.config.almostEmptyLevel.U
        )
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("tx_lsbFirst").get.U,
          config.config.lsbFirst.B
        )

        val foundNumOutputBits = readAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("tx_numOutputBitsDb").get.U
        )
        val foundUseParity = readAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("tx_useParityDb").get.U
        )
        val foundParityOdd = readAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("tx_parityOddDb").get.U
        )
        val foundAlmostFullLevel = readAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("tx_almostFullLevel").get.U
        )
        val foundAlmostEmptyLevel = readAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("tx_almostEmptyLevel").get.U
        )
        val foundLsbFirst = readAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("tx_lsbFirst").get.U
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
          foundAlmostFullLevel == config.config.almostFullLevel,
          "almostFullLevel register not set correctly"
        )
        assert(
          foundAlmostEmptyLevel == config.config.almostEmptyLevel,
          "almostEmptyLevel register not set correctly"
        )
        assert(
          (foundLsbFirst == 1) == config.config.lsbFirst,
          "lsbFirst register not set correctly"
        )
    }

    def transmitPush(
        dut: Uart,
        config: UartFifoTxRuntimeConfig,
        dataSend: Int
    )(implicit
        clock: Clock
    ): Unit = {
        println(s"Sending data: $dataSend")
        // Start transmission
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("tx_dataIn").get.U,
          dataSend.U
        )

    }

    def transmitPop(
        dut: Uart,
        config: UartFifoTxRuntimeConfig,
        testFifo: scala.collection.mutable.Queue[Int]
    )(implicit
        clock: Clock
    ): Unit = {

        def expectConstantTx(expected: Boolean, cycles: Int): Unit = {
            dut.io.tx.expect(expected.B)
            dut.clock.setTimeout(cycles + 1)
            dut.clock.step(cycles)
        }

        var expectedBits: Seq[(Boolean, UartState.Type)] = Seq()
        println(s"Expecting data:")
        while (!testFifo.isEmpty) {
            val dataDequeued = testFifo.dequeue()
            println(s"$dataDequeued")
            // assert that the data is within the range of 0 to 255
            assert(
              dataDequeued >= 0 && dataDequeued <= 2.pow(
                config.config.numOutputBits
              ) - 1,
              "Data must be in the range 0 to 255"
            )

//            val dataBits: Seq[Boolean] =
//                (0 until config.config.numOutputBits).map { i =>
//                    ((dataDequeued >> i) & 1) == 1
//                }.reverse

            val dataBits: Seq[Boolean] = if (config.config.lsbFirst) {
                (0 until config.config.numOutputBits).map { i =>
                    ((dataDequeued >> i) & 1) == 1
                }
            } else {
                (0 until config.config.numOutputBits).map { i =>
                    ((dataDequeued >> i) & 1) == 1
                }.reverse
            }
            val expectedSequenceIndividual: Seq[(Boolean, UartState.Type)] =
                if (config.config.useParity) {
                    val parityBit = UartParity.parity(
                      dataDequeued,
                      config.config.parityOdd
                    )
                    Seq((false, UartState.Start)) ++ dataBits.map(
                      (_, UartState.Data)
                    ) ++ Seq(
                      (parityBit, UartState.Parity),
                      (true, UartState.Stop)
                    )
                } else {
                    Seq((false, UartState.Start)) ++ dataBits.map(
                      (_, UartState.Data)
                    ) ++ Seq(
                      (true, UartState.Stop)
                    )
                }

            expectedBits = expectedBits ++ expectedSequenceIndividual
        }

        println(s"Expected data sequence: $expectedBits")

        writeApbNoDelay(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("tx_load").get.U,
          true.B
        )

        // #####################
        fork {
            clock.step(1)
            dut.io.apb.PSEL.poke(1.U)
            dut.io.apb.PENABLE.poke(1.U)
            dut.io.apb.PWRITE.poke(1.U)
            dut.io.apb.PADDR
                .poke(dut.registerMap.getAddressOfRegister("tx_load").get.U)
            dut.io.apb.PWDATA.poke(0.U)
            clock.step(1)
            dut.io.apb.PSEL.poke(0.U)
            dut.io.apb.PENABLE.poke(0.U)
            dut.io.apb.PWRITE.poke(0.U)
            dut.io.apb.PADDR.poke(0.U)
            dut.io.apb.PWDATA.poke(0.U)
        }.fork {
            val clockFrequency = config.config.clockFrequency
            val baudRate       = config.config.baudRate

            val clocksPerBit = clockFrequency / (baudRate / 2)

            // #####################
            for ((expectedBit, index) <- expectedBits.zipWithIndex) {
                println(
                  s"Checking bit $index: expected ${expectedBit._1} in state ${expectedBit._2}"
                )
                expectConstantTx(expectedBit._1, clocksPerBit)
            }
        }.join()
    }
}
