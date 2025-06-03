// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)
package tech.rocksavage.chiselware.uart.types.error

import chisel3._
import tech.rocksavage.chiselware.addrdecode.AddrDecodeError

/** Bundle grouping all UART‐related error indicators.
 *
 * This bundle collects the various error signals that can be
 * reported by the UART frontend (address decode), the receiver,
 * and the transmitter, as well as any top‐level errors (e.g.
 * invalid register programming).
 */
class UartErrorBundle extends Bundle {
  /** Top‐level errors (e.g. invalid register programming). */
  val topError = UartTopError()
  /** Errors detected by the UART receiver (framing, parity, FIFO). */
  val rxError = UartRxError()
  /** Errors detected by the UART transmitter (parity, stop‐bit, FIFO). */
  val txError = UartTxError()
  /** Address‐decode errors from the register interface. */
  val addrDecodeError = AddrDecodeError()
}
