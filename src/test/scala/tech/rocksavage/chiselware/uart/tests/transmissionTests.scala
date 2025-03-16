// transmissionTests.scala
package tech.rocksavage.chiselware.uart.tests

import chisel3._
import chiseltest._
import tech.rocksavage.chiselware.uart.param.UartParams
import tech.rocksavage.chiselware.uart.testutils.UartRxTestUtils
import tech.rocksavage.chiselware.uart.testutils.UartRxTestUtils.setBaudRateRx
import tech.rocksavage.chiselware.uart.testutils.UartTxTestUtils.setBaudRateTx
import tech.rocksavage.chiselware.uart.{UartRx, UartTx}

object transmissionTests {
    def basicRxTest(dut: UartRx, params: UartParams): Unit = {
        implicit val clock = dut.clock

        val clockFrequency = 25_000_000
        val baudRate       = 115_200

        val clocksPerBit  = clockFrequency / baudRate
        val numOutputBits = 8

        // Reset the device
        dut.io.rx.poke(1.U)

        // Provide the baud rate
        setBaudRateRx(dut, baudRate, clockFrequency)

        dut.io.rxConfig.numOutputBitsDb.poke(numOutputBits.U)
        dut.io.rxConfig.useParityDb.poke(false.B)
        dut.io.rxConfig.parityOddDb.poke(false.B) // Explicitly set parity mode

        val chars = Seq('s', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K')
        for (char <- chars) {
            println(s"Testing character: $char")
            UartRxTestUtils.transactionCharRx(dut, char, clocksPerBit)

            // Wait for valid signal with timeout
            var timeout = 0
//            dut.clock.setTimeout(clocksPerBit * 12 + 1)
            dut.clock.setTimeout(20000)
            while (
              dut.io.fifoBundle.empty
                  .peek()
                  .litToBoolean && timeout < clocksPerBit * 12
            ) {
                dut.clock.step(1)
                timeout += 1
            }

            if (timeout >= clocksPerBit * 12) {
                throw new RuntimeException(
                  s"Timeout waiting for valid signal on character $char"
                )
            }
            dut.io.rxConfig.rxDataRegRead.poke(true.B)
            dut.io.data.expect(char.U)
            dut.clock.step(1)
            dut.io.rxConfig.rxDataRegRead.poke(false.B)
            dut.clock.step(clocksPerBit) // Wait between characters
        }
    }

    def bufferedRxTest(dut: UartRx, params: UartParams): Unit = {
        implicit val clock = dut.clock

        val clockFrequency = 50_000_000
        val baudRate       = 115_200

        val clocksPerBit  = clockFrequency / baudRate
        val numOutputBits = 8

        // Reset the device
        dut.io.rx.poke(1.U)

        // Provide the baud rate
        setBaudRateRx(dut, baudRate, clockFrequency)

        dut.io.rxConfig.numOutputBitsDb.poke(numOutputBits.U)
        dut.io.rxConfig.useParityDb.poke(false.B)
        dut.io.rxConfig.parityOddDb.poke(false.B) // Explicitly set parity mode

        val chars = Seq('A', 'B', 'C', 'D')
        for (char <- chars) {
            println(s"Sending character: $char")
            UartRxTestUtils.transactionCharRx(dut, char, clocksPerBit)
        }

        clock.setTimeout(clocksPerBit * 8 + 1)
        clock.step(clocksPerBit)

        println("Checking FIFO contents\n")
        println("Expecting A\n")
        dut.io.fifoBundle.empty.expect(false.B)
        dut.io.data.expect('A'.U)
        dut.io.rxConfig.rxDataRegRead.poke(true.B)
        dut.clock.step(1)
        dut.io.rxConfig.rxDataRegRead.poke(false.B)
        dut.clock.step(1)

        println("Expecting B\n")
        dut.io.fifoBundle.empty.expect(false.B)
        dut.io.data.expect('B'.U)
        dut.io.rxConfig.rxDataRegRead.poke(true.B)
        dut.clock.step(1)
        dut.io.rxConfig.rxDataRegRead.poke(false.B)
        dut.clock.step(1)

        println("Expecting C\n")
        dut.io.fifoBundle.empty.expect(false.B)
        dut.io.data.expect('C'.U)
        dut.io.rxConfig.rxDataRegRead.poke(true.B)
        dut.clock.step(1)
        dut.io.rxConfig.rxDataRegRead.poke(false.B)
        dut.clock.step(1)

        println("Expecting D\n")
        dut.io.fifoBundle.empty.expect(false.B)
        dut.io.data.expect('D'.U)
        dut.io.rxConfig.rxDataRegRead.poke(true.B)
        dut.clock.step(1)
        dut.io.rxConfig.rxDataRegRead.poke(false.B)
        dut.clock.step(1)

    }

    def basicTxTest(dut: UartTx, params: UartParams): Unit = {
        implicit val clock = dut.clock

        val clockFrequency = 25_000_000
        val baudRate       = 115_200

        val clocksPerBit  = clockFrequency / baudRate
        val numOutputBits = 8

        // Provide the baud rate
        setBaudRateTx(dut, baudRate, clockFrequency)

        dut.io.txConfig.numOutputBitsDb.poke(numOutputBits.U)
        dut.io.txConfig.useParityDb.poke(false.B)
        dut.io.txConfig.parityOddDb.poke(false.B) // Explicitly set parity mode

        // Before starting transmission, the transmitter is idle so the output should be high
        dut.io.tx.expect(true.B)

        dut.clock.step(2)

        def expectConstantTx(expected: Boolean, cycles: Int): Unit = {
            dut.io.tx.expect(expected.B)
            dut.clock.setTimeout(cycles + 2)
            dut.clock.step(cycles + 1)
        }

//        0b11001010
        val data: Int = 202
        val dataBits: Seq[Boolean] = (0 until numOutputBits).map { i =>
            ((data >> i) & 1) == 1
        }.reverse

        // Build the expected sequence
        val expectedSequence: Seq[Boolean] = Seq(false) ++ dataBits ++ Seq(true)
        println(s"Starting transmission of data: ${data.toBinaryString}")
        println(s"Expected sequence: $expectedSequence")

        // Initiate transmission
        dut.io.txConfig.data.poke(data.U)
        dut.io.txConfig.txDataRegWrite.poke(true.B)
        dut.clock.step(1)
        dut.io.txConfig.txDataRegWrite.poke(false.B)
        dut.clock.step(1)
        dut.io.txConfig.load.poke(true.B)
        dut.clock.step(1)
        fork {
            dut.io.txConfig.load.poke(false.B)
            dut.clock.step(1)
        }.joinAndStep(clock)

        // Check each bit with a timeout
        for ((expectedBit, index) <- expectedSequence.zipWithIndex) {
            println(s"Checking bit $index: expected $expectedBit")
            expectConstantTx(expectedBit, clocksPerBit)
        }

        // Verify return to idle
        dut.clock.setTimeout(clocksPerBit + 1)
        dut.clock.step(clocksPerBit)
        dut.io.tx.expect(true.B)
    }

    def bufferedTxTest(dut: UartTx, params: UartParams): Unit = {
        implicit val clock = dut.clock

        val clockFrequency = 50_000_000
        val baudRate       = 115_200

        val clocksPerBit  = clockFrequency / baudRate
        val numOutputBits = 8

        // Provide the baud rate
        setBaudRateTx(dut, baudRate, clockFrequency)

        dut.io.txConfig.numOutputBitsDb.poke(numOutputBits.U)
        dut.io.txConfig.useParityDb.poke(false.B)
        dut.io.txConfig.parityOddDb.poke(false.B) // Explicitly set parity mode

        // Before starting transmission, the transmitter is idle so the output should be high
        dut.io.tx.expect(true.B)

        dut.clock.step(2)

        def expectConstantTx(expected: Boolean, cycles: Int): Unit = {
            dut.io.tx.expect(expected.B)
            dut.clock.setTimeout(cycles + 2)
            dut.clock.step(cycles + 1)
        }

        val data1: Int = 202 // 0b11001010

        val dataBits1: Seq[Boolean] = (0 until numOutputBits).map { i =>
            ((data1 >> i) & 1) == 1
        }.reverse
        val expectedSequence1: Seq[Boolean] =
            Seq(false) ++ dataBits1 ++ Seq(true)

        // 0b11110000
        val data2: Int = 240
        val dataBits2: Seq[Boolean] = (0 until numOutputBits).map { i =>
            ((data2 >> i) & 1) == 1
        }.reverse
        val expectedSequence2: Seq[Boolean] =
            Seq(false) ++ dataBits2 ++ Seq(true)
        println(
          s"Starting transmission of data: ${data1.toBinaryString} and ${data2.toBinaryString}"
        )

        // Initiate transmission
        dut.io.txConfig.data.poke(data1.U)
        dut.io.txConfig.txDataRegWrite.poke(true.B)
        dut.clock.step(1)
        dut.io.txConfig.txDataRegWrite.poke(false.B)
        dut.clock.step(1)

        dut.io.txConfig.data.poke(data2.U)
        dut.io.txConfig.txDataRegWrite.poke(true.B)
        dut.clock.step(1)
        dut.io.txConfig.txDataRegWrite.poke(false.B)
        dut.clock.step(1)

        dut.io.txConfig.load.poke(true.B)
        dut.clock.step(1)
        dut.io.txConfig.load.poke(false.B)
        dut.clock.step(1)

        // Check each bit with a timeout
        for ((expectedBit, index) <- expectedSequence1.zipWithIndex) {
            println(s"Checking bit $index: expected $expectedBit")
            expectConstantTx(expectedBit, clocksPerBit)
        }

        for ((expectedBit, index) <- expectedSequence2.zipWithIndex) {
            println(s"Checking bit $index: expected $expectedBit")
            expectConstantTx(expectedBit, clocksPerBit)
        }

        // Verify return to idle
        dut.clock.setTimeout(clocksPerBit + 1)
        dut.clock.step(clocksPerBit)
        dut.io.tx.expect(true.B)
    }
}
