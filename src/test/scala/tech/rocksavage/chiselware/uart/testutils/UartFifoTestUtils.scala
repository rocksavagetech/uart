// (c) 2024 Rocksavage Technology, Inc.
// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart.testutils

import chisel3._
import chiseltest._
import tech.rocksavage.chiselware.apb.ApbBundle
import tech.rocksavage.chiselware.apb.ApbTestUtils.{
    readAPB,
    writeAPB,
    writeApbNoDelay
}
import tech.rocksavage.chiselware.uart.UartFifoDataDirection.UartFifoDataDirection
import tech.rocksavage.chiselware.uart._

import scala.math.BigInt.int2bigInt

object UartFifoTestUtils {

    def generateNextValidRandomConfig(
        validClockFreqs: Seq[Int],
        validBaudRates: Seq[Int],
        validNumOutputBits: Seq[Int],
        fifoSize: Int
    ): UartFifoRuntimeConfig = {
        var datas: Seq[UartData] = Seq()
        var fifoHeight           = 0
        for (_ <- 0 until 2 * fifoSize) {
            // case statement to determine if we should push or pop
            val pushOrPop: UartFifoDataDirection = {
                if (fifoHeight == 0) {
                    UartFifoDataDirection.Push
                } else if (fifoHeight == fifoSize) {
                    UartFifoDataDirection.Pop
                } else {
                    val randomInt = scala.util.Random.nextInt(16)
                    val boolValue = randomInt >= 15 // 1/16 chance of popping
                    boolToPushPop(boolValue)
                }
            }

            if (pushOrPop == UartFifoDataDirection.Push) {
                fifoHeight += 1
            } else {
                fifoHeight = 0
            }
            datas = datas :+ new UartData(
              scala.util.Random
                  .nextInt(2.pow(validNumOutputBits.last).toInt),
              pushOrPop
            )
        }

        while (true) {
            try {
                val config = UartTestConfig(
                  baudRate = validBaudRates(
                    scala.util.Random.nextInt(validBaudRates.length)
                  ),
                  clockFrequency = validClockFreqs(
                    scala.util.Random.nextInt(validClockFreqs.length)
                  ),
                  numOutputBits = validNumOutputBits(
                    scala.util.Random.nextInt(validNumOutputBits.length)
                  ),
                  useParity = scala.util.Random.nextBoolean(),
                  parityOdd = scala.util.Random.nextBoolean()
                )
                return UartFifoRuntimeConfig(
                  config = config,
                  data = datas
                )
            } catch {
                case _: IllegalArgumentException =>
            }
        }

        throw new RuntimeException("Failed to generate a valid config")
    }

    def boolToPushPop(bool: Boolean): UartFifoDataDirection = {
        if (bool) {
            UartFifoDataDirection.Push
        } else {
            UartFifoDataDirection.Pop
        }
    }

    def transmit(dut: Uart, config: UartFifoRuntimeConfig)(implicit
        clock: Clock
    ): Unit = {
        println("Preparing to transmit data")
        transmit_setup(dut, config)
        println(s"Transmitting data: ${config.data}\n")
        val testFifo = scala.collection.mutable.Queue[(Int)]()
        for (data <- config.data) {
            clock.setTimeout(1000)
            println(s"Next Data Operation: $data")
            if (data.direction == UartFifoDataDirection.Push) {
                testFifo.enqueue(data.data)
                println(s"Data ($data) queued to Fifo: $testFifo")
                transmit_push(dut, config, data.data)
            } else {
                println(s"Popping all data from Fifo: $testFifo")
                transmit_pop(dut, config, testFifo)
                println(s"Data popped from Fifo Successfully")
            }
        }
    }

    def transmit_setup(dut: Uart, config: UartFifoRuntimeConfig)(implicit
        clock: Clock
    ): Unit = {
        clock.setTimeout(1000)

        val clockFrequency = config.config.clockFrequency
        val baudRate       = config.config.baudRate

        val clocksPerBit  = clockFrequency / baudRate
        val numOutputBits = config.config.numOutputBits

        val data = config.data

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
          config.config.useParity.B
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

        assert(
          foundNumOutputBits == numOutputBits,
          "numOutputBitsDb register not set correctly"
        )
        assert(
          (foundUseParity == 1) == config.config.useParity,
          "useParityDb register not set correctly"
        )
        assert(
          (foundParityOdd == 1) == config.config.useParity,
          "parityOddDb register not set correctly"
        )
    }

    def transmit_push(
        dut: Uart,
        config: UartFifoRuntimeConfig,
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

    def transmit_pop(
        dut: Uart,
        config: UartFifoRuntimeConfig,
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

            val dataBits: Seq[Boolean] =
                (0 until config.config.numOutputBits).map { i =>
                    ((dataDequeued >> i) & 1) == 1
                }.reverse
            val expectedSequenceIndividual: Seq[(Boolean, UartState.Type)] =
                if (config.config.useParity) {
                    val parityBit = dataBits.count(identity) % 2 == 0
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

            val clocksPerBit = clockFrequency / baudRate

            // #####################
            for ((expectedBit, index) <- expectedBits.zipWithIndex) {
                println(
                  s"Checking bit $index: expected ${expectedBit._1} in state ${expectedBit._2}"
                )
                expectConstantTx(expectedBit._1, clocksPerBit)
            }
        }.join()
    }

    def receive(dut: Uart, config: UartFifoRuntimeConfig)(implicit
        clock: Clock
    ): Unit = {

//        clock.setTimeout(1000)
//
//        val clockFrequency = config.config.clockFrequency
//        val baudRate       = config.config.baudRate
//
//        val clocksPerBit  = clockFrequency / baudRate
//        val numOutputBits = config.config.numOutputBits
//
//        val data = config.data
//
//        // initial state
//        dut.io.rx.poke(true.B)
//
//        // Provide the baud rate
//        rxSetBaudRate(dut, baudRate, clockFrequency)
//
//        // Configure UART
//        writeAPB(
//          dut.io.apb,
//          dut.registerMap.getAddressOfRegister("rx_numOutputBitsDb").get.U,
//          numOutputBits.U
//        )
//        writeAPB(
//          dut.io.apb,
//          dut.registerMap.getAddressOfRegister("rx_useParityDb").get.U,
//          config.config.useParity.B
//        )
//        writeAPB(
//          dut.io.apb,
//          dut.registerMap.getAddressOfRegister("rx_parityOddDb").get.U,
//          config.config.useParity.B
//        )
//
//        val foundNumOutputBits = readAPB(
//          dut.io.apb,
//          dut.registerMap.getAddressOfRegister("rx_numOutputBitsDb").get.U
//        )
//        val foundUseParity = readAPB(
//          dut.io.apb,
//          dut.registerMap.getAddressOfRegister("rx_useParityDb").get.U
//        )
//        val foundParityOdd = readAPB(
//          dut.io.apb,
//          dut.registerMap.getAddressOfRegister("rx_parityOddDb").get.U
//        )
//
//        assert(
//          foundNumOutputBits == numOutputBits,
//          "numOutputBitsDb register not set correctly"
//        )
//        assert(
//          (foundUseParity == 1) == config.useParity,
//          "useParityDb register not set correctly"
//        )
//        assert(
//          (foundParityOdd == 1) == config.useParity,
//          "parityOddDb register not set correctly"
//        )
//
//        println(s"Sending data: $data")
//
//        // assert that the data is within the range of 0 to 255
//        assert(
//          data >= 0 && data <= 2.pow(numOutputBits) - 1,
//          "Data must be in the range 0 to 255"
//        )
//
//        val dataBits: Seq[Boolean] = (0 until numOutputBits).map { i =>
//            ((data >> i) & 1) == 1
//        }.reverse
//
//        // Build the expected sequence
//        // match on useParity
//        val sequence =
//            if (config.useParity) {
//                val parityBit = dataBits.count(identity) % 2 == 0
//                Seq(false) ++ dataBits ++ Seq(parityBit) ++ Seq(true)
//            } else {
//                Seq(false) ++ dataBits ++ Seq(true)
//            }
//
//        val dataAvailable = readAPB(
//          dut.io.apb,
//          dut.registerMap.getAddressOfRegister("rx_dataAvailable").get.U
//        )
//        assert(
//          dataAvailable == 0,
//          "RX data should not be available before transmission"
//        )
//
//        // Start transmission
//        // Check each bit with a timeout
//        for ((bit, index) <- sequence.zipWithIndex) {
//            println(s"Checking bit $index: expected $bit")
//            dut.io.rx.poke(bit.B)
//            dut.clock.setTimeout(clocksPerBit + 1)
//            dut.clock.step(clocksPerBit)
//        }
//
//        dut.clock.setTimeout(10)
//        dut.clock.step(1)
//
//        // Verify final state
//        val dataAvailableActual = readAPB(
//          dut.io.apb,
//          dut.registerMap.getAddressOfRegister("rx_dataAvailable").get.U
//        )
//        assert(
//          dataAvailableActual == 1,
//          "RX data should be available after transmission"
//        )
//        val dataActual = readAPB(
//          dut.io.apb,
//          dut.registerMap.getAddressOfRegister("rx_data").get.U
//        )
//        assert(
//          dataActual == data,
//          s"RX data should match transmitted data: expected $data, got $dataActual"
//        )
    }

    def setBaudRate(dut: Uart, baudRate: Int, clockFrequency: Int): Unit = {
        implicit val clock = dut.clock

        rxSetBaudRate(dut, baudRate, clockFrequency)
        txSetBaudRate(dut, baudRate, clockFrequency)
    }

    def txSetBaudRate(dut: Uart, baudRate: Int, clockFrequency: Int): Unit = {
        implicit val clock = dut.clock

        val txBaudRateAddr =
            dut.registerMap.getAddressOfRegister("tx_baudRate").get
        val txClockFreqAddr =
            dut.registerMap.getAddressOfRegister("tx_clockFreq").get
        val txUpdateBaudAddr =
            dut.registerMap.getAddressOfRegister("tx_updateBaud").get
        val txClocksPerBitAddr =
            dut.registerMap.getAddressOfRegister("tx_clocksPerBit").get

        writeAPB(dut.io.apb, txBaudRateAddr.U, baudRate.U)
        writeAPB(dut.io.apb, txClockFreqAddr.U, clockFrequency.U)
        writeAPB(dut.io.apb, txUpdateBaudAddr.U, 1.U)

        val clocksPerBitExpected = clockFrequency / baudRate

//        clock.setTimeout(40)
        var breakLoop = false
        var count     = 0
        val maxCount  = 50
        while (!breakLoop) {
            val clocksPerBitActual = readAPB(
              dut.io.apb,
              txClocksPerBitAddr.U
            )
            if (clocksPerBitActual == clocksPerBitExpected) {
                breakLoop = true
            }
            if (count >= maxCount) {
                throw new RuntimeException(
                  s"Failed to set baud rate after $maxCount attempts"
                )
            }
            count = count + 1
            clock.step(1)
        }
    }

    def rxSetBaudRate(dut: Uart, baudRate: Int, clockFrequency: Int): Unit = {
        implicit val clock = dut.clock

        val rxBaudRateAddr =
            dut.registerMap.getAddressOfRegister("rx_baudRate").get
        val rxClockFreqAddr =
            dut.registerMap.getAddressOfRegister("rx_clockFreq").get
        val rxUpdateBaudAddr =
            dut.registerMap.getAddressOfRegister("rx_updateBaud").get
        val rxClocksPerBitAddr =
            dut.registerMap.getAddressOfRegister("rx_clocksPerBit").get

        writeAPB(dut.io.apb, rxBaudRateAddr.U, baudRate.U)
        writeAPB(dut.io.apb, rxClockFreqAddr.U, clockFrequency.U)
        writeAPB(dut.io.apb, rxUpdateBaudAddr.U, 1.U)

        val clocksPerBitExpected = clockFrequency / baudRate

        clock.setTimeout(1000)
        var breakLoop = false
        var count     = 0
        val maxCount  = 50
        while (!breakLoop) {
            val clocksPerBitActual = readAPB(
              dut.io.apb,
              rxClocksPerBitAddr.U
            )
            if (clocksPerBitActual == clocksPerBitExpected) {
                breakLoop = true
            }
            if (count >= maxCount) {
                throw new RuntimeException(
                  s"Failed to set baud rate after $maxCount attempts"
                )
            }
            count = count + 1
            clock.step(1)
        }
        while (!breakLoop) {
            val clocksPerBitActual = readAPB(
              dut.io.apb,
              rxClocksPerBitAddr.U
            )
            if (clocksPerBitActual == clocksPerBitExpected) {
                breakLoop = true
            }

            if (count >= maxCount) {
                throw new RuntimeException(
                  s"Failed to set baud rate after $count attempts"
                )
            }

            count += 1
            clock.step(1)
        }
    }

    def setupUart(
        apb: ApbBundle,
        uart: Uart,
        clockFreq: Int,
        baudRate: Int,
        useParity: Boolean = false,
        parityOdd: Boolean = false
    )(implicit clock: Clock): Unit = {
        setupRxUart(apb, uart, clockFreq, baudRate, useParity, parityOdd)
        setupTxUart(apb, uart, clockFreq, baudRate, useParity, parityOdd)
    }

    def setupRxUart(
        apb: ApbBundle,
        uart: Uart,
        clockFreq: Int,
        baudRate: Int,
        useParity: Boolean = false,
        parityOdd: Boolean = false
    )(implicit clock: Clock): Unit = {

        val baudRateAddr =
            uart.registerMap.getAddressOfRegister("rx_baudRate").get
        val clockFreqAddr =
            uart.registerMap.getAddressOfRegister("rx_clockFreq").get
        val updateBaudAddr =
            uart.registerMap.getAddressOfRegister("rx_updateBaud").get

        writeAPB(apb, baudRateAddr.U, baudRate.U)
        writeAPB(apb, clockFreqAddr.U, clockFreq.U)
        writeAPB(apb, updateBaudAddr.U, 1.U)
        clock.step(32)

        writeAPB(
          apb,
          uart.registerMap.getAddressOfRegister("rx_numOutputBitsDb").get.U,
          8.U
        )
        writeAPB(
          apb,
          uart.registerMap.getAddressOfRegister("rx_useParityDb").get.U,
          useParity.B
        )
        if (useParity) {
            writeAPB(
              apb,
              uart.registerMap.getAddressOfRegister("rx_parityOddDb").get.U,
              parityOdd.B
            )
        }
    }

    def setupTxUart(
        apb: ApbBundle,
        uart: Uart,
        clockFreq: Int,
        baudRate: Int,
        useParity: Boolean = false,
        parityOdd: Boolean = false
    )(implicit clock: Clock): Unit = {

        val baudRateAddr =
            uart.registerMap.getAddressOfRegister("tx_baudRate").get
        val clockFreqAddr =
            uart.registerMap.getAddressOfRegister("tx_clockFreq").get
        val updateBaudAddr =
            uart.registerMap.getAddressOfRegister("tx_updateBaud").get

        writeAPB(apb, baudRateAddr.U, baudRate.U)
        writeAPB(apb, clockFreqAddr.U, clockFreq.U)
        writeAPB(apb, updateBaudAddr.U, 1.U)
        clock.step(32)

        writeAPB(
          apb,
          uart.registerMap.getAddressOfRegister("tx_numOutputBitsDb").get.U,
          8.U
        )
        writeAPB(
          apb,
          uart.registerMap.getAddressOfRegister("tx_useParityDb").get.U,
          useParity.B
        )
        if (useParity) {
            writeAPB(
              apb,
              uart.registerMap.getAddressOfRegister("tx_parityOddDb").get.U,
              parityOdd.B
            )
        }
    }

    def verifyIdleState(
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
