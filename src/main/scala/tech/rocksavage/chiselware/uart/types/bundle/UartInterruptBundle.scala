// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart.types.bundle

import chisel3._
import tech.rocksavage.chiselware.uart.types.param.UartParams

// from the perspective of the UART which is receiving data
class UartInterruptBundle(params: UartParams) extends Bundle {
    val dataReceived = Output(Bool())
}
