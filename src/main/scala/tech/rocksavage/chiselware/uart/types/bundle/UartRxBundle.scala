// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart.types.bundle

import chisel3._
import chisel3.util.log2Ceil
import tech.rocksavage.chiselware.uart.types.error.UartRxError
import tech.rocksavage.chiselware.uart.types.param.UartParams

/** I/O bundle for the UART receiver side.
 *
 * This bundle packages:
 *  - the raw serial input (`rx`),
 *  - the assembled parallel data word (`data`),
 *  - the receiver error code (`error`),
 *  - all RX control signals (`rxConfig`),
 *  - the observed clocks‐per‐bit value (`clocksPerBit`),
 *  - and the FIFO status flags (`fifoBundle`).
 *
 * @param params UART configuration parameters, including
 *               data‐width, buffer size, clock/baud limits, etc.
 */
class UartRxBundle(params: UartParams) extends Bundle {
  /** Asynchronous serial input line. */
  val rx = Input(Bool())

  /** Parallel data word assembled from received bits. */
  val data = Output(UInt(params.maxOutputBits.W))

  /** Receiver error flags (framing, parity, overflow, underflow). */
  val error = Output(UartRxError())

  /** Configuration and control inputs for the RX path:
   * bit‐width, parity, baud update, FIFO read/flush, etc. */
  val rxConfig = new UartRxControlBundle(params)

  /** Effective clocks‐per‐bit as reported by the baud‐rate generator. */
  val clocksPerBit = Output(UInt((log2Ceil(params.maxClockFrequency) + 1).W))

  /** Status flags for the RX FIFO (full, empty, almostFull, almostEmpty). */
  val fifoBundle = new FifoStatusBundle(params)
}
