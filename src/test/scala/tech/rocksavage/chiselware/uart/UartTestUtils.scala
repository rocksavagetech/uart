// (c) 2024 Rocksavage Technology, Inc.
// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart

import chisel3._
import chiseltest._
import tech.rocksavage.chiselware.apb.ApbTestUtils.{readAPB, writeAPB}

object UartTestUtils {

    def setBaudRateRx(dut: UartRx, baudRate: Int, clockFrequency: Int): Unit = {

        implicit val clock = dut.clock

        dut.io.rxConfig.baud.poke(baudRate.U)
        dut.io.rxConfig.clockFreq.poke(clockFrequency.U)
        dut.clock.step(1)
        dut.io.rxConfig.updateBaud.poke(true.B)
        dut.clock.step(1)
        dut.io.rxConfig.updateBaud.poke(false.B)
        dut.clock.step(1)
        dut.clock.step(40)

        dut.io.clocksPerBit.expect((clockFrequency / baudRate).U)
    }

    def setBaudRateTx(dut: UartTx, baudRate: Int, clockFrequency: Int): Unit = {
        implicit val clock = dut.clock
        dut.io.txConfig.baud.poke(baudRate.U)
        dut.io.txConfig.clockFreq.poke(clockFrequency.U)
        dut.clock.step(1)
        dut.io.txConfig.updateBaud.poke(true.B)
        dut.clock.step(1)
        dut.io.txConfig.updateBaud.poke(false.B)
        dut.clock.step(1)
        dut.clock.step(40)

        dut.io.clocksPerBit.expect((clockFrequency / baudRate).U)
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
        dut.clock.step(40)

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

    def transactionChar(dut: UartRx, char: Char, clocksPerBit: Int): Unit = {
//    val binString = char.toBinaryString.padTo(8, '0').reverse
        val binString = char.toBinaryString.reverse.padTo(8, '0').reverse
        val bits      = binString.map(_ == '1')
        println(s"Transmitting character: $char, with bits: ${binString}")
        transaction(dut, bits, clocksPerBit, 8)
    }

    def transaction(
        dut: UartRx,
        bits: Seq[Boolean],
        clocksPerBit: Int,
        numBits: Int
    ): Unit = {

        // Transmit the start bit
        println("--- Transmitting start bit")
        transmitBit(dut, false, clocksPerBit)
        // Transmit numBits bits
        println("--- Transmitting data bits")
        for (i <- 0 until numBits) {
            transmitBit(dut, bits(i), clocksPerBit)
        }
        // Transmit the stop bit
        println("--- Transmitting stop bit")
        UartTestUtils.transmitBit(dut, true, clocksPerBit)
    }

    def transmitBit(dut: UartRx, bit: Boolean, clocksPerBit: Int): Unit = {
        if (bit) println("--- --- 1")
        else
            println("--- --- 0")
        dut.io.rx.poke(bit.B)

        println(s"Transmitting bit for $clocksPerBit cycles")
        dut.clock.setTimeout(clocksPerBit + 1)
        for (i <- 0 until clocksPerBit) {
            dut.clock.step()
        }
    }
}
