// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart.types.bundle

import chisel3._
import tech.rocksavage.chiselware.uart.types.param.UartParams

/** Interrupt‐style I/O bundle for the UART receiver.
 *
 * This bundle exposes a pulse indicating that a new data word
 * has been received and is available for processing.
 *
 * @param params UART configuration parameters (bit‐widths, timing, etc.).
 */
class UartInterruptBundle(params: UartParams) extends Bundle {
  /** Asserted for one cycle when a new data word has been received. */
  val dataReceived = Output(Bool())
}