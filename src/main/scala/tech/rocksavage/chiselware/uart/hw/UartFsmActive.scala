// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)
package tech.rocksavage.chiselware.uart.hw

import chisel3._
import tech.rocksavage.chiselware.uart.types.param.UartParams

/** Generates the `active` signal for the UART finite-state machine.
 *
 * The `active` flag is asserted from the beginning of a UART frame
 * (Start bit) until the frame’s stop bit completes and no new
 * transaction is immediately requested.  Internally this is a
 * Mealy‐style machine that latches its previous `active` state.
 *
 * @param params UART configuration parameters (max clocks per bit, data-bit limits, parity, etc.)
 */
class UartFsmActive(params: UartParams) extends Module {
  /** Input/Output bundle for the UART-FSM “active” generator. */
  val io = IO(new Bundle {
    /** Asserted when a new transaction is requested from Idle or Stop state. */
    val idleTransactionStarted = Input(Bool())
    /** Asserted when the current transaction has completed and no pause is required. */
    val transactionCompletedAndNotWaiting = Input(Bool())
    /** Asserted when FSM was in Stop state and is now waiting before the next frame. */
    val stateStoppedAndWaiting = Input(Bool())
    /** High while the UART frame (start–data–[parity]–stop) is active. */
    val active = Output(Bool())
  })

  val activeMealy = WireInit(false.B)
  val activePrev = RegInit(false.B)

  when(io.idleTransactionStarted) {
    // 1. Start Transaction From a Completely Idle State when a new transaction is requested
    activeMealy := true.B
  }.elsewhen(io.stateStoppedAndWaiting) {
    // 2. This should only happen when another transaction starts immediately after the previous one
    activeMealy := true.B

  }.elsewhen(activePrev && io.transactionCompletedAndNotWaiting) {
    // 3. Stop Transaction when the transaction is complete and another transaction is not waiting
    activeMealy := false.B
  }.otherwise(
    activeMealy := activePrev
  )
  activePrev := activeMealy

  // Output
  io.active := activeMealy
}
