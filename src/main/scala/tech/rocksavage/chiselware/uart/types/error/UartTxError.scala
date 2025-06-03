// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)
package tech.rocksavage.chiselware.uart.types.error

import chisel3._

/** Error codes surfaced by the UART transmitter (Tx).
 *
 *  - None          : No error detected.
 *  - ParityError   : Parity bit generation failed or configuration mismatch.
 *  - StopBitError  : Stop bit was not driven as logic high.
 *  - FifoUnderflow : Transmit data FIFO was empty when a transaction started.
 *  - FifoOverflow  : Transmit data FIFO was full on write.
 */
object UartTxError extends ChiselEnum {
  val None, ParityError, StopBitError, FifoUnderflow, FifoOverflow =
    Value // Error types for UART Tx
}
