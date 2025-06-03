package tech.rocksavage.chiselware.uart.types.bundle

import chisel3._
import tech.rocksavage.chiselware.uart.types.error.UartErrorBundle
import tech.rocksavage.chiselware.uart.types.param.UartParams

/** Basic UART I/O bundle shared by RX and TX.
 *
 * Contains serial line, data, error flags, FIFOs and control bundles.
 *
 * @param params UART parameters (bit-widths, bufferSize, etc.)
 */
class UartBundle(params: UartParams) extends Bundle {
  /** Asynchronous serial input. */
  val rx = Input(Bool())
  /** Parallel data received. */
  val dataOut = Output(UInt(params.maxOutputBits.W))
  /** Aggregated error signals. */
  val error = Output(new UartErrorBundle())

  /** Transmit-side control (load, data, parity, baud, etc.). */
  val txControlBundle = new UartTxControlBundle(params)
  /** Receive-side control (read, parity, baud, etc.). */
  val rxControlBundle = new UartRxControlBundle(params)

  /** Status of the RX FIFO (full/empty/almost levels). */
  val rxFifoStatus = new FifoStatusBundle(params)
  /** Status of the TX FIFO (full/empty/almost levels). */
  val txFifoStatus = new FifoStatusBundle(params)

  /** Asynchronous serial output. */
  val tx = Output(Bool())
}
