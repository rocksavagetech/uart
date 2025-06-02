// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart.types.bundle

import chisel3._
import chisel3.util._
import tech.rocksavage.chiselware.uart.types.param.UartParams

// from the perspective of the UART which is receiving data
class UartTxControlBundle(params: UartParams) extends Bundle {

  // When high, initiates a new UART transmission.
  val load = Input(Bool())
  // Parallel data to be transmitted.
  val data = Input(UInt(params.maxOutputBits.W))
  // configuration inputs
  val numOutputBitsDb = Input(UInt((log2Ceil(params.maxOutputBits) + 1).W))
  val useParityDb = Input(Bool())
  val parityOddDb = Input(Bool())
  val baud = Input(UInt((log2Ceil(params.maxBaudRate) + 1).W))
  val clockFreq = Input(UInt((log2Ceil(params.maxClockFrequency) + 1).W))
  val updateBaud = Input(Bool())
  val clearErrorDb = Input(Bool())
  val txDataRegWrite = Input(Bool())
  val almostEmptyLevel = Input(UInt((log2Ceil(params.bufferSize) + 1).W))
  val almostFullLevel = Input(UInt((log2Ceil(params.bufferSize) + 1).W))
  val lsbFirst = Input(Bool())
  val flush = Input(Bool())
}
