// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)
package tech.rocksavage.chiselware.uart.types.bundle

import chisel3._
import chisel3.util.log2Ceil
import tech.rocksavage.chiselware.uart.types.error.UartTxError
import tech.rocksavage.chiselware.uart.types.param.UartParams

/** I/O bundle for the UART transmitter side.
 *
 * This bundle contains:
 *  - the TX control inputs (`txConfig`),
 *  - the observed clocks‐per‐bit from the baud generator,
 *  - the serial output (`tx`),
 *  - the transmitter error code (`error`),
 *  - and FIFO status flags (`fifoBundle`).
 *
 * @param params UART configuration parameters (data‐width, buffer size, clock/baud limits, etc.)
 */
class UartTxBundle(params: UartParams) extends Bundle {
  /** Control inputs for the TX path: load, data, parity, baud update, FIFO thresholds, etc. */
  val txConfig = new UartTxControlBundle(params)

  /** Effective clocks‐per‐bit as reported by the baud‐rate generator. */
  val clocksPerBit = Output(UInt((log2Ceil(params.maxClockFrequency) + 1).W))

  /** Asynchronous serial output line. */
  val tx = Output(Bool())

  /** Transmitter error flags (underflow, overflow, framing). */
  val error = Output(UartTxError())

  /** Status flags for the TX FIFO (full, empty, almostFull, almostEmpty). */
  val fifoBundle = Output(new FifoStatusBundle(params))
}