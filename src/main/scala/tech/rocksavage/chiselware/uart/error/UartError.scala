// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)
package tech.rocksavage.chiselware.uart.error

import chisel3._

/** Bundle defining the error type for a UART.
  */
class UartError() extends Bundle {
    val rxError  = UartRxError()
    val topError = UartErrorObject()
}
