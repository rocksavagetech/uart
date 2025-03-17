// (c) 2024 Rocksavage Technology, Inc.
// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart.testutils

import chisel3._
import chiseltest._
import tech.rocksavage.chiselware.uart.UartTx

object UartTxTestUtils {

    def setBaudRateTx(dut: UartTx, baudRate: Int, clockFrequency: Int): Unit = {
        dut.io.txConfig.baud.poke(baudRate.U)
        dut.io.txConfig.clockFreq.poke(clockFrequency.U)
        dut.clock.step(1)
        dut.io.txConfig.updateBaud.poke(true.B)
        dut.clock.step(1)
        dut.io.txConfig.updateBaud.poke(false.B)
        dut.clock.step(1)
        dut.clock.step(36)

        dut.io.clocksPerBit.expect((clockFrequency / (baudRate / 2)).U)
    }

}
