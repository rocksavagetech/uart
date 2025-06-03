// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)
package tech.rocksavage.chiselware.uart.hw

import chisel3._
import chisel3.util.log2Ceil
import tech.rocksavage.chiselware.uart.types.param.UartParams

/** Clock and bit-cell counter for the UART FSM.
 *
 * This module increments `clockCounter` on each cycle where
 * `clockCounterIncrement` is true.  When `clockCounter` reaches
 * `clockCounterMax`, it rolls over and pulses an overflow to
 * advance the `bitCounter`.  The `bitCounter` tracks the start bit,
 * data bits, optional parity bit, and stop bit(s), then resets at
 * the end of the frame.
 *
 * @param params UART configuration parameters (maxClockFrequency, maxOutputBits)
 */
class UartFsmCounter(params: UartParams) extends Module {
  val io = IO(new Bundle {

    /** Maximum clock count per UART bit (derived from baud rate). */
    val clockCounterMax =
      Input(UInt((log2Ceil(params.maxClockFrequency) + 1).W))
    /** Enable incrementing the clock counter this cycle. */
    val clockCounterIncrement = Input(Bool())
    /** Number of data bits in the frame (excludes parity & stop bits). */
    val numOutputBits = Input(UInt((log2Ceil(params.maxOutputBits) + 1).W))
    /** When true, a parity bit is present after the data bits. */
    val useParity = Input(Bool())

    /** Current clock-cycle counter within a bit cell. */
    val clockCounter =
      Output(UInt((log2Ceil(params.maxClockFrequency) + 1).W))
    /** Current bit-cell index (0 = start bit, 1..N = data bits, etc.). */
    val bitCounter = Output(UInt((log2Ceil(params.maxOutputBits) + 1).W))
  })

  // Counters

  val clockCounterReg = RegInit(
    0.U((log2Ceil(params.maxClockFrequency) + 1).W)
  )
  val bitCounterReg = RegInit(0.U((log2Ceil(params.maxOutputBits) + 1).W))

  val clockCounterOverflow = WireInit(false.B)
  val clockCounterIncrmentResult = increment(
    clockCounterReg,
    io.clockCounterMax,
    io.clockCounterIncrement
  )
  clockCounterReg := clockCounterIncrmentResult._1
  clockCounterOverflow := clockCounterIncrmentResult._2

  val bitCounterMax = WireDefault(io.numOutputBits + 2.U)
  when(io.useParity) {
    bitCounterMax := io.numOutputBits + 3.U
  }
  val bitCounterIncrement = WireDefault(clockCounterOverflow)
  val bitCounterOverflow = WireDefault(false.B)
  val bitCounterIncrementResult = increment(
    bitCounterReg,
    bitCounterMax,
    bitCounterIncrement
  )
  bitCounterReg := bitCounterIncrementResult._1
  bitCounterOverflow := bitCounterIncrementResult._2

  // if at the end of the transmission (bitcounter max - 1 & clock counter max - 1)
  // then we clear all clocks and reset the active register
  when(
    bitCounterReg === bitCounterMax - 1.U && clockCounterReg === io.clockCounterMax - 1.U
  ) {
    clockCounterReg := 0.U
    clockCounterOverflow := false.B
    bitCounterReg := 0.U
    bitCounterOverflow := false.B
  }

  // Output
  io.clockCounter := clockCounterReg
  io.bitCounter := bitCounterReg

  /** Increment a counter value with rollover.
   *
   * @param value     Current counter value.
   * @param max       Rollover threshold (when ≥ max).
   * @param condition When true, attempt to increment.
   * @return A tuple (nextValue, didOverflow) where `didOverflow`
   *         is asserted when the increment wraps to zero.
   */
  def increment(value: UInt, max: UInt, condition: Bool): (UInt, Bool) = {
    val incrementedInner = WireDefault(value)
    val incremented = WireDefault(value)
    val overflow = WireDefault(false.B)

    when(condition) {
      incrementedInner := value + 1.U

      when((incrementedInner >= max) || (incrementedInner < value)) {
        incremented := 0.U
        overflow := true.B
      }.otherwise {
        incremented := incrementedInner
      }
    }

    (incremented, overflow)
  }
}
