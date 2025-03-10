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
import tech.rocksavage.chiselware.uart.{FullDuplexUart, Uart, UartRuntimeConfig}

import scala.math.BigInt.int2bigInt

object UartTestUtils {

    def generateNextValidRandomConfig(
        validClockFreqs: Seq[Int],
        validBaudRates: Seq[Int],
        validNumOutputBits: Seq[Int]
    ): UartRuntimeConfig = {
        while (true) {
            try {
                val data = scala.util.Random
                    .nextInt(2.pow(validNumOutputBits.last).toInt)

                val config = UartRuntimeConfig(
                  baudRate = validBaudRates(
                    scala.util.Random.nextInt(validBaudRates.length)
                  ),
                  clockFrequency = validClockFreqs(
                    scala.util.Random.nextInt(validClockFreqs.length)
                  ),
                  numOutputBits = validNumOutputBits(
                    scala.util.Random.nextInt(validNumOutputBits.length)
                  ),
                  data = data,
                  useParity = scala.util.Random.nextBoolean(),
                  parityOdd = scala.util.Random.nextBoolean()
                )
                return config
            } catch {
                case _: IllegalArgumentException =>
            }
        }
        throw new RuntimeException("Failed to generate a valid config")
    }

    def transmit(dut: Uart, config: UartRuntimeConfig)(implicit
        clock: Clock
    ): Unit = {

        clock.setTimeout(1000)

        val clockFrequency = config.clockFrequency
        val baudRate       = config.baudRate

        val clocksPerBit  = clockFrequency / baudRate
        val numOutputBits = config.numOutputBits

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
          config.useParity.B
        )
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("tx_parityOddDb").get.U,
          config.useParity.B
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
          (foundUseParity == 1) == config.useParity,
          "useParityDb register not set correctly"
        )
        assert(
          (foundParityOdd == 1) == config.useParity,
          "parityOddDb register not set correctly"
        )

        println(s"Sending data: $data")

        // assert that the data is within the range of 0 to 255
        assert(
          data >= 0 && data <= 2.pow(numOutputBits) - 1,
          "Data must be in the range 0 to 255"
        )

        val dataBits: Seq[Boolean] = (0 until numOutputBits).map { i =>
            ((data >> i) & 1) == 1
        }.reverse

        // Build the expected sequence
        // match on useParity
        val expectedSequence =
            if (config.useParity) {
                val parityBit = dataBits.count(identity) % 2 == 0
                Seq(false) ++ dataBits ++ Seq(parityBit) ++ Seq(true)
            } else {
                Seq(false) ++ dataBits ++ Seq(true)
            }

        // Record initial state
        dut.io.tx.expect(
          true.B,
          "TX line should be high (idle) before transmission"
        )

        // Start transmission
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("tx_dataIn").get.U,
          data.U
        )

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
            dut.io.apb.PWDATA.poke(data)
            clock.step(1)
            dut.io.apb.PSEL.poke(0.U)
            dut.io.apb.PENABLE.poke(0.U)
        }

        // #####################

        def expectConstantTx(expected: Boolean, cycles: Int): Unit = {
            dut.io.tx.expect(expected.B)
            dut.clock.setTimeout(cycles + 1)
            dut.clock.step(cycles)
        }

        println(s"Expected sequence: $expectedSequence")
        // Check each bit with a timeout
        for ((expectedBit, index) <- expectedSequence.zipWithIndex) {
            println(s"Checking bit $index: expected $expectedBit")
            expectConstantTx(expectedBit, clocksPerBit)
        }
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

        clock.setTimeout(1000)
        var breakLoop = false
        while (!breakLoop) {
            val clocksPerBitActual = readAPB(
              dut.io.apb,
              txClocksPerBitAddr.U
            )
            if (clocksPerBitActual == clocksPerBitExpected) {
                breakLoop = true
            }
            clock.step(1)
        }
    }

    def receive(dut: Uart, config: UartRuntimeConfig)(implicit
        clock: Clock
    ): Unit = {

        clock.setTimeout(1000)

        val clockFrequency = config.clockFrequency
        val baudRate       = config.baudRate

        val clocksPerBit  = clockFrequency / baudRate
        val numOutputBits = config.numOutputBits

        val data = config.data

        // initial state
        dut.io.rx.poke(true.B)

        // Provide the baud rate
        rxSetBaudRate(dut, baudRate, clockFrequency)

        // Configure UART
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("rx_numOutputBitsDb").get.U,
          numOutputBits.U
        )
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("rx_useParityDb").get.U,
          config.useParity.B
        )
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("rx_parityOddDb").get.U,
          config.useParity.B
        )

        val foundNumOutputBits = readAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("rx_numOutputBitsDb").get.U
        )
        val foundUseParity = readAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("rx_useParityDb").get.U
        )
        val foundParityOdd = readAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("rx_parityOddDb").get.U
        )

        assert(
          foundNumOutputBits == numOutputBits,
          "numOutputBitsDb register not set correctly"
        )
        assert(
          (foundUseParity == 1) == config.useParity,
          "useParityDb register not set correctly"
        )
        assert(
          (foundParityOdd == 1) == config.useParity,
          "parityOddDb register not set correctly"
        )

        println(s"Sending data: $data")

        // assert that the data is within the range of 0 to 255
        assert(
          data >= 0 && data <= 2.pow(numOutputBits) - 1,
          "Data must be in the range 0 to 255"
        )

        val dataBits: Seq[Boolean] = (0 until numOutputBits).map { i =>
            ((data >> i) & 1) == 1
        }.reverse

        // Build the expected sequence
        // match on useParity
        val sequence =
            if (config.useParity) {
                val parityBit = dataBits.count(identity) % 2 == 0
                Seq(false) ++ dataBits ++ Seq(parityBit) ++ Seq(true)
            } else {
                Seq(false) ++ dataBits ++ Seq(true)
            }

        val dataAvailable = readAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("rx_dataAvailable").get.U
        )
        assert(
          dataAvailable == 0,
          "RX data should not be available before transmission"
        )

        // Start transmission
        // Check each bit with a timeout
        for ((bit, index) <- sequence.zipWithIndex) {
            println(s"Checking bit $index: expected $bit")
            dut.io.rx.poke(bit.B)
            dut.clock.setTimeout(clocksPerBit + 1)
            dut.clock.step(clocksPerBit)
        }

        dut.clock.setTimeout(10)
        dut.clock.step(1)

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
          dataActual == data,
          s"RX data should match transmitted data: expected $data, got $dataActual"
        )
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
        val numLoops  = 30
        var loopCount = 0
        while (!breakLoop) {
            val clocksPerBitActual = readAPB(
              dut.io.apb,
              rxClocksPerBitAddr.U
            )
            if (clocksPerBitActual == clocksPerBitExpected) {
                breakLoop = true
            }
            
            if (loopCount >= numLoops) {
                throw new RuntimeException(
                  s"Failed to set baud rate after $numLoops attempts"
                )
            }

            loopCount += 1
            clock.step(1)
        }
    }

    def setBaudRate(dut: Uart, baudRate: Int, clockFrequency: Int): Unit = {
        implicit val clock = dut.clock

        rxSetBaudRate(dut, baudRate, clockFrequency)
        txSetBaudRate(dut, baudRate, clockFrequency)
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

    def waitForDataAndVerify(
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
              uart.registerMap.getAddressOfRegister("rx_dataAvailable").get.U
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
                  uart.registerMap.getAddressOfRegister("rx_data").get.U
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
