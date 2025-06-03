// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart.types.error

import chisel3._

/** Error codes surfaced by the UART receiver (Rx).
 *
 *  - None             : No error detected.
 *  - StartBitError    : Start bit was not detected as logic low.
 *  - StopBitError     : Stop bit was not detected as logic high.
 *  - ParityError      : Parity bit did not match computed parity.
 *  - FifoUnderflow    : Received data FIFO was empty on read.
 *  - FifoOverflow     : Received data FIFO was full on write.
 */
object UartRxError extends ChiselEnum {
  val None, StartBitError, StopBitError, ParityError,
  FifoUnderflow, FifoOverflow = Value
}
