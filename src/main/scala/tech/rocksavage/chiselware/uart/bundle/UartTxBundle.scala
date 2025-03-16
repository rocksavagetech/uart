// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)
package tech.rocksavage.chiselware.uart.bundle

import chisel3._
import chisel3.util.log2Ceil
import tech.rocksavage.chiselware.uart.error.UartTxError
import tech.rocksavage.chiselware.uart.param.UartParams

/** Bundle defining the I/O for a UART transmitter.
  *
  * @param params
  *   Configuration parameters for the UART.
  */
class UartTxBundle(params: UartParams) extends Bundle {
    val txConfig     = new UartTxControlBundle(params)
    val clocksPerBit = Output(UInt((log2Ceil(params.maxClockFrequency) + 1).W))

    // The UART serial output.
    val tx = Output(Bool())

    val error = Output(UartTxError())

    val fifoBundle = Output(new FifoStatusBundle(params))
}
