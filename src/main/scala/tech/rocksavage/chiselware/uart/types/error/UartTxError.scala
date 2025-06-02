// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)
package tech.rocksavage.chiselware.uart.types.error

import chisel3._

object UartTxError extends ChiselEnum {
  val None, ParityError, StopBitError, FifoUnderflow, FifoOverflow =
    Value // Error types for UART Tx
}
