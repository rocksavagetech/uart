package tech.rocksavage.chiselware.uart.types.bundle

import chisel3._
import tech.rocksavage.chiselware.uart.types.error.UartErrorBundle
import tech.rocksavage.chiselware.uart.types.param.UartParams

/** Combined I/O bundle for both UART receiver and transmitter.
 *
 * @param params
 * Configuration parameters for the UART.
 */
class UartBundle(params: UartParams) extends Bundle {
  // UART receiver signals
  // 'rx' is the asynchronous serial input.
  val rx = Input(Bool())
  // Data received from RX.
  val dataOut = Output(UInt(params.maxOutputBits.W))
  // Error signal from the receiver.
  val error = Output(new UartErrorBundle())

  // Config Signals
  val txControlBundle = new UartTxControlBundle(params)
  val rxControlBundle = new UartRxControlBundle(params)

  val rxFifoStatus = new FifoStatusBundle(params)
  val txFifoStatus = new FifoStatusBundle(params)

  // UART serial output
  val tx = Output(Bool())
}
