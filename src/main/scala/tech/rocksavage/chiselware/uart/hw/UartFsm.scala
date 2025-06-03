// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)
package tech.rocksavage.chiselware.uart.hw

import chisel3._
import chisel3.util._
import tech.rocksavage.chiselware.uart.types.enums.UartState
import tech.rocksavage.chiselware.uart.types.param.UartParams

/** A finite‐state machine that sequences UART bit‐cell transfers
 * for transmit or receive, with optional parity, runtime baud updates,
 * and valid/complete strobes.
 *
 * This FSM works in tandem with [[UartFsmCounter]] (for clock/bit counting)
 * and [[UartFsmActive]] (for breaking combinational loops).  It transitions
 * through Idle, Start, Data, Parity, Stop, and BaudUpdating states,
 * generating shift, complete, and nextTransaction pulses.
 *
 * @param params UART configuration parameters (max clock frequency,
 *               max data bits, parity, etc.)
 */
class UartFsm(params: UartParams) extends Module {
  /** The UART‐FSM IO bundle. */
  val io = IO(new Bundle {

    // ############# input signals

    // #### Control Signals ####
    /** Assert to begin a new character transfer (Idle/Stop → Start). */
    val startTransaction = Input(Bool())
    /** When true, sample at half‐bit time (RX); otherwise shift at end‐of‐bit (TX). */
    val shiftOffset = Input(Bool())
    /** Held high while FSM must pause between characters or in RX dead‐time. */
    val waiting = Input(Bool())

    // #### Settings Signals ####
    /** System clocks per UART bit, derived from baud rate. */
    val clocksPerBit =
      Input(UInt((log2Ceil(params.maxClockFrequency) + 1).W))
    /** Number of data bits in the frame (excludes parity & stop bits). */
    val numOutputBits =
      Input(UInt((log2Ceil(params.maxOutputBits) + 1).W))
    /** When true, include a parity bit after data bits. */
    val useParity = Input(Bool())

    // #### Baud Settings ####
    /** Assert to start a new baud‐rate update cycle. */
    val updateBaud = Input(Bool())

    /** High once the newly programmed baud is valid and stable. */
    val baudValid = Input(Bool())

    // ############# output signals
    /** The current FSM state (Idle, Start, Data, Parity, Stop, BaudUpdating). */
    val state = Output(UartState())
    /** Pulses true each time a bit‐cell boundary is reached (shift/sample). */
    val shift = Output(Bool())
    /** Pulses true when the current character transaction completes. */
    val complete = Output(Bool())
    /** Pulses true to immediately start the next transaction. */
    val nextTransaction = Output(Bool())
  })

  // #### State Counter
  val counter = Module(new UartFsmCounter(params))
  counter.io.clockCounterMax := io.clocksPerBit
  counter.io.numOutputBits := io.numOutputBits
  counter.io.useParity := io.useParity

  // #### State Active Mealy
  /** High exactly when the current frame has finished. */
  val combComplete = WireInit(false.B)
  val combComplete1 = WireInit(false.B)
  combComplete1 := (counter.io.clockCounter === io.clocksPerBit - 1.U)
  val combComplete2 = WireInit(false.B)
  combComplete2 := (
    (!io.useParity && counter.io.bitCounter === io.numOutputBits + 1.U) || (io.useParity && counter.io.bitCounter === io.numOutputBits + 2.U)
    )
  combComplete := combComplete1 && combComplete2

  /** True during the active phase of a frame (start→stop). */
  val active = WireInit(false.B)
  val activeFsm = Module(new UartFsmActive(params))
  activeFsm.io.idleTransactionStarted := (RegNext(
    io.state
  ) === UartState.Idle) && (io.startTransaction)
  activeFsm.io.transactionCompletedAndNotWaiting := !io.waiting && combComplete
  activeFsm.io.stateStoppedAndWaiting := (RegNext(
    io.state
  ) === UartState.Stop) && io.waiting

  active := activeFsm.io.active

  // #### Counter - Active
  counter.io.clockCounterIncrement := active

  // #### State Machine
  val prevState = RegInit(UartState.Idle)
  val state = WireDefault(UartState.Idle)
  when(io.updateBaud || prevState === UartState.BaudUpdating) {
    state := stateCaseBaud(
      updateBaud = io.updateBaud,
      baudValid = io.baudValid,
      prevState = prevState
    )
  }.otherwise {
    state := stateCase(
      numBits = io.numOutputBits,
      bitCount = counter.io.bitCounter,
      parity = io.useParity,
      active = active,
      activePrev = RegNext(active),
      waiting = io.waiting,
      nextTransaction = io.nextTransaction
    )
  }

  prevState := state
  io.state := state

  // Outputs

  val whenShift = WireInit(false.B)
  when(io.shiftOffset) {
    // RX
    whenShift := (counter.io.clockCounter === (io.clocksPerBit >> 1.U))
  }.otherwise {
    // TX
    whenShift := (counter.io.clockCounter === io.clocksPerBit - 1.U) &&
      ((counter.io.bitCounter =/= io.numOutputBits) || (io.clocksPerBit === 1.U))
  }

  io.shift := whenShift && active &&
    (state =/= UartState.Start) &&
    (prevState =/= UartState.Idle) &&
    (prevState =/= UartState.BaudUpdating)
  io.complete := combComplete
  io.nextTransaction := (io.startTransaction) || (RegNext(
    combComplete
  ) && io.waiting)

  /** Compute next state when in a baud‐update cycle.
   *
   * @param updateBaud True to begin a baud‐rate update
   * @param baudValid  True once the new baud rate is stable
   * @param prevState  FSM state in the previous cycle
   * @return Next UartState for baud‐update sequencing
   */
  def stateCaseBaud(
                     updateBaud: Bool,
                     baudValid: Bool,
                     prevState: UartState.Type
                   ): UartState.Type = {
    val state = WireDefault(UartState.Idle)

    when(prevState === UartState.Idle && updateBaud) {
      state := UartState.BaudUpdating
    }.elsewhen(prevState === UartState.BaudUpdating && baudValid) {
      state := UartState.Idle
    }.elsewhen(prevState === UartState.BaudUpdating && !baudValid) {
      state := UartState.BaudUpdating
    }
    state
  }

  /** Compute next state during normal data transfer (start→stop).
   *
   * @param numBits         Number of data bits configured
   * @param bitCount        Current bit‐counter value (0=start bit)
   * @param parity          True if parity bit is enabled
   * @param active          High while in an active frame
   * @param activePrev      Active flag from the prior cycle
   * @param waiting         True if FSM must pause after frame
   * @param nextTransaction True if a new start was requested
   * @return Next UartState for data/parity/stop transitions
   */
  def stateCase(
                 numBits: UInt,
                 bitCount: UInt,
                 parity: Bool,
                 active: Bool,
                 activePrev: Bool,
                 waiting: Bool,
                 nextTransaction: Bool
               ): UartState.Type = {
    val state = WireDefault(UartState.Idle)
    when(active) {
      when(nextTransaction) {
        state := UartState.Start
      }.elsewhen(bitCount === 0.U) {
        state := UartState.Start
      }.elsewhen(bitCount > 0.U && bitCount <= numBits) {
        state := UartState.Data
      }.elsewhen(parity && bitCount === numBits + 1.U) {
        state := UartState.Parity
      }.elsewhen(!parity && bitCount === numBits + 1.U) {
        state := UartState.Stop
      }.elsewhen(parity && bitCount === numBits + 2.U) {
        state := UartState.Stop
      }
    }.elsewhen(activePrev && waiting) {
      state := UartState.Start
    }
    state
  }

}
