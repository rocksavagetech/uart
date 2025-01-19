// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart.bundle

import chisel3._
import chisel3.util._
import tech.rocksavage.chiselware.uart.error.UartRxError
import tech.rocksavage.chiselware.uart.param.UartParams

// from the perspective of the RX UART
class UartBundle(params: UartParams) extends Bundle {
  val rxtx  = Input(Bool())
  val data  = Input(UInt(params.dataWidth.W))
  val valid = Output(Bool())
  val ready = Input(Bool())
  val error = Output(UartRxError())
}
