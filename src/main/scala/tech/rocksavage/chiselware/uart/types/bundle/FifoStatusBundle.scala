package tech.rocksavage.chiselware.uart.types.bundle

import chisel3._
import tech.rocksavage.chiselware.uart.types.param.UartParams

class FifoStatusBundle(params: UartParams) extends Bundle {
  val full = Output(Bool())
  val empty = Output(Bool())
  //    val count       = Output(UInt(params.bufferSize.W))
  val almostEmpty = Output(Bool())
  val almostFull = Output(Bool())
}
