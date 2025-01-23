// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)
package tech.rocksavage.chiselware.uart.bundle

import chisel3._
import chisel3.util._
import tech.rocksavage.chiselware.uart.error.UartTxError
import tech.rocksavage.chiselware.uart.param.UartParams

// From the perspective of the UART which is transmitting data
class UartTxBundle(params: UartParams) extends Bundle {
  val tx    = Output(Bool())
  val data  = Input(UInt(params.maxOutputBits.W))
  val valid = Output(Bool()) // Changed from Input to Output
  val ready = Output(Bool())
  val error = Output(UartTxError())
  // Configuration inputs
  val clocksPerBitDb  = Input(UInt(log2Ceil(params.maxClocksPerBit).W))
  val numOutputBitsDb = Input(UInt(log2Ceil(params.maxOutputBits).W))
  val useParityDb     = Input(Bool())
}
