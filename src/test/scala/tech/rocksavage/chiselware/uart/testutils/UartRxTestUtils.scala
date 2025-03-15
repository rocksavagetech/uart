// (c) 2024 Rocksavage Technology, Inc.
// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart.testutils

import chisel3._
import chiseltest._
import tech.rocksavage.chiselware.uart.UartRx

object UartRxTestUtils {

    def setBaudRateRx(dut: UartRx, baudRate: Int, clockFrequency: Int): Unit = {

        implicit val clock = dut.clock

        dut.io.rxConfig.baud.poke(baudRate.U)
        dut.io.rxConfig.clockFreq.poke(clockFrequency.U)
        dut.clock.step(1)
        dut.io.rxConfig.updateBaud.poke(true.B)
        dut.clock.step(1)
        dut.io.rxConfig.updateBaud.poke(false.B)
        dut.clock.step(1)
        dut.clock.step(36)

        dut.io.clocksPerBit.expect((clockFrequency / baudRate).U)
    }

    def transactionCharRx(dut: UartRx, char: Char, clocksPerBit: Int): Unit = {
        //    val binString = char.toBinaryString.padTo(8, '0').reverse
        val binString = char.toBinaryString.reverse.padTo(8, '0').reverse
        val bits      = binString.map(_ == '1')
        println(s"Transmitting character: $char, with bits: ${binString}")
        transactionRx(dut, bits, clocksPerBit, 8)

    }

    def transactionRx(
        dut: UartRx,
        bits: Seq[Boolean],
        clocksPerBit: Int,
        numBits: Int
    ): Unit = {

        // Transmit the start bit
        transmitBitRx(dut, false, clocksPerBit)
        // Transmit numBits bits
        for (i <- 0 until numBits) {
            transmitBitRx(dut, bits(i), clocksPerBit)
        }
        // Transmit the stop bit
        UartRxTestUtils.transmitBitRx(dut, true, clocksPerBit)
    }

    def transmitBitRx(dut: UartRx, bit: Boolean, clocksPerBit: Int): Unit = {
        dut.io.rx.poke(bit.B)
        dut.clock.setTimeout(clocksPerBit + 1)
        for (i <- 0 until clocksPerBit) {
            dut.clock.step()
        }
    }
}
