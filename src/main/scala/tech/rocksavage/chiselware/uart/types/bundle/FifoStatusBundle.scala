package tech.rocksavage.chiselware.uart.types.bundle

import chisel3._
import tech.rocksavage.chiselware.uart.types.param.UartParams

/** Status flags for a simple hardware FIFO.
 *
 * @param params FIFO depth (to size the “count” if exposed)
 */
class FifoStatusBundle(params: UartParams) extends Bundle {
  /** Asserted when FIFO is full. */
  val full = Output(Bool())
  /** Asserted when FIFO is empty. */
  val empty = Output(Bool())
  /** Asserted when FIFO occupancy ≤ almostEmptyLevel. */
  val almostEmpty = Output(Bool())
  /** Asserted when FIFO occupancy ≥ almostFullLevel. */
  val almostFull = Output(Bool())
}
