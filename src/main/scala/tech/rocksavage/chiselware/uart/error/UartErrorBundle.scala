// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)
package tech.rocksavage.chiselware.uart.error

import chisel3._
import tech.rocksavage.chiselware.addrdecode.AddrDecodeError

/** Bundle defining the error type for a UART.
  */
class UartErrorBundle() extends Bundle {
    val topError        = UartTopError()
    val rxError         = UartRxError()
    val txError         = UartTxError()
    val addrDecodeError = AddrDecodeError()
}
