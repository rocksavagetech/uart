// (c) 2024 Rocksavage Technology, Inc.
// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart

import chisel3._
import chiseltest._

object UartUtils {
  def transmitBit(dut: UartRx, bit: Boolean, clocksPerBit: Int): Unit = {
    dut.io.rx.poke(bit.B)

    dut.clock.setTimeout(clocksPerBit + 1)
    for (i <- 0 until clocksPerBit) {
      dut.clock.step()
    }
  }
}
