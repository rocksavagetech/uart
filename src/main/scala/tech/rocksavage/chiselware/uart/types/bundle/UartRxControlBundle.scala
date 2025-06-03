// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart.types.bundle

import chisel3._
import chisel3.util._
import tech.rocksavage.chiselware.uart.types.param.UartParams

/** Control bundle for UART receiver.
 *
 * @param params UART configuration, especially bufferSize and bit-width limits
 */
class UartRxControlBundle(params: UartParams) extends Bundle {
  /** Desired baud rate (encoded as clocks-per-bit). */
  val baud = Input(UInt((log2Ceil(params.maxBaudRate) + 1).W))
  /** System clock frequency (for dynamic baud updates). */
  val clockFreq = Input(UInt((log2Ceil(params.maxClockFrequency) + 1).W))
  /** Trigger an update to the baud generator. */
  val updateBaud = Input(Bool())
  /** Data-bit count (1–maxOutputBits). */
  val numOutputBitsDb = Input(UInt((log2Ceil(params.maxOutputBits) + 1).W))
  /** Enable parity checking. */
  val useParityDb = Input(Bool())
  /** If true, expect odd parity; else even. */
  val parityOddDb = Input(Bool())
  /** Clear any sticky RX errors. */
  val clearErrorDb = Input(Bool())
  /** Dequeue one word from RX FIFO. */
  val rxDataRegRead = Input(Bool())
  /** Threshold for “almost empty” FIFO flag. */
  val almostEmptyLevel = Input(UInt((log2Ceil(params.bufferSize) + 1).W))
  /** Threshold for “almost full” FIFO flag. */
  val almostFullLevel = Input(UInt((log2Ceil(params.bufferSize) + 1).W))
  /** If true, present received bits LSB first. */
  val lsbFirst = Input(Bool())
  /** Flush the RX FIFO when asserted. */
  val flush = Input(Bool())
}
