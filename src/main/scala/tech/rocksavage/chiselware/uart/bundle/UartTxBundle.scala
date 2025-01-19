// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)
package tech.rocksavage.chiselware.uart.bundle

import chisel3._
import chisel3.util._
import tech.rocksavage.chiselware.uart.param.UartParams

// From the perspective of the UART which is transmitting data
class UartTxBundle(params: UartParams) extends Bundle {

  val tx = Flipped(new UartBundle(params))

  // Configuration inputs
  val clocksPerBitDb = Input(UInt(log2Ceil(params.maxClocksPerBit).W))  // Clocks per bit
  val numOutputBitsDb = Input(UInt(log2Ceil(params.maxOutputBits).W))  // Number of output bits
  val useParityDb = Input(Bool())  // Whether to use parity
}