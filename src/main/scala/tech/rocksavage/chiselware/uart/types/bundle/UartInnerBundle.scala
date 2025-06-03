package tech.rocksavage.chiselware.uart.types.bundle

import chisel3._
import chisel3.util.log2Ceil
import tech.rocksavage.chiselware.uart.types.param.UartParams

/** I/O bundle for the merged UART (receiver + transmitter).
 *
 * Extends the common UartBundle with separate
 * clock-per-bit outputs for RX and TX sides.
 *
 * @param params UART configuration parameters
 */
class UartInnerBundle(params: UartParams) extends UartBundle(params) {
  /** RX-side observed clocks-per-bit (propagated from UartRx). */
  val rxClocksPerBit = Output(
    UInt((log2Ceil(params.maxClockFrequency) + 1).W)
  )
  /** TX-side observed clocks-per-bit (propagated from UartTx). */
  val txClocksPerBit = Output(
    UInt((log2Ceil(params.maxClockFrequency) + 1).W)
  )
}
