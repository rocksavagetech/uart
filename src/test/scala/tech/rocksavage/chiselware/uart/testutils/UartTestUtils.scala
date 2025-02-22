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
import tech.rocksavage.chiselware.uart.{Uart, UartRuntimeConfig}

import scala.math.BigInt.int2bigInt

object UartTestUtils {

    def transmit(dut: Uart, config: UartRuntimeConfig)(implicit
        clock: Clock
    ): Unit = {

        val clockFrequency = config.clockFrequency
        val baudRate       = config.baudRate

        val clocksPerBit  = clockFrequency / baudRate
        val numOutputBits = config.numOutputBits

        val data = config.data

        // Provide the baud rate
        setBaudRate(dut, baudRate, clockFrequency)

        // Configure UART
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("numOutputBitsDb").get.U,
          numOutputBits.U
        )
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("useParityDb").get.U,
          config.useParity.B
        )
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("parityOddDb").get.U,
          config.useParity.B
        )

        val foundNumOutputBits = readAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("numOutputBitsDb").get.U
        )
        val foundUseParity = readAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("useParityDb").get.U
        )
        val foundParityOdd = readAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("parityOddDb").get.U
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
          dut.registerMap.getAddressOfRegister("dataIn").get.U,
          data.U
        )
        writeApbNoDelay(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("load").get.U,
          true.B
        )
        clock.step(1)
        writeApbNoDelay(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("load").get.U,
          false.B
        )

        def expectConstantTx(expected: Boolean, cycles: Int): Unit = {
            dut.io.tx.expect(expected.B)
            dut.clock.setTimeout(cycles + 1)
            dut.clock.step(cycles)
        }

        // Verify start bit
        dut.io.tx.expect(
          false.B,
          "TX line should go low to start transmission"
        )

        println(s"Expected sequence: $expectedSequence")
        // Check each bit with a timeout
        for ((expectedBit, index) <- expectedSequence.zipWithIndex) {
            println(s"Checking bit $index: expected $expectedBit")
            expectConstantTx(expectedBit, clocksPerBit)
        }

        // Verify final state
        dut.io.tx.expect(
          true.B,
          "TX line should return to high (idle) after transmission"
        )
    }

    def setBaudRate(dut: Uart, baudRate: Int, clockFrequency: Int): Unit = {
        implicit val clock = dut.clock

        val baudRateAddr = dut.registerMap.getAddressOfRegister("baudRate").get
        val clockFreqAddr =
            dut.registerMap.getAddressOfRegister("clockFreq").get
        val updateBaudAddr =
            dut.registerMap.getAddressOfRegister("updateBaud").get
        val clocksPerBitRxAddr =
            dut.registerMap.getAddressOfRegister("clocksPerBitRx").get
        val clocksPerBitTxAddr =
            dut.registerMap.getAddressOfRegister("clocksPerBitTx").get

        writeAPB(dut.io.apb, baudRateAddr.U, baudRate.U)
        writeAPB(dut.io.apb, clockFreqAddr.U, clockFrequency.U)
        writeAPB(dut.io.apb, updateBaudAddr.U, 1.U)

        val clocksPerBitExpected = clockFrequency / baudRate

        var breakLoop = false
        while (!breakLoop) {
            val clocksPerBitActual = readAPB(
              dut.io.apb,
              clocksPerBitRxAddr.U
            )
            if (clocksPerBitActual == clocksPerBitExpected) {
                breakLoop = true
            }
            clock.step(1)
        }

        val clocksPerBitRx = readAPB(
          dut.io.apb,
          clocksPerBitRxAddr.U
        )
        val clocksPerBitTx = readAPB(
          dut.io.apb,
          clocksPerBitTxAddr.U
        )

        assert(
          clocksPerBitRx == (clockFrequency / baudRate)
        )
        assert(
          clocksPerBitTx == (clockFrequency / baudRate)
        )
    }

}
