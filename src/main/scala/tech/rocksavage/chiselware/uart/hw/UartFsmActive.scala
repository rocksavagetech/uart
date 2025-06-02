// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)
package tech.rocksavage.chiselware.uart.hw

import chisel3._
import tech.rocksavage.chiselware.uart.types.param.UartParams

// Inputs:
// Start Transaction - Moves From Idle (or Stop) to Start
//     - Also Starts Timers, cycle after start transaction, clock counter is at 1
// Clocks Per Bit Reg - Number of Clocks per bit
//     - Used to determine when to sample data
//     - Also used to determine when to increment the bit counter
// Num Output Bits Reg - Number of bits to output
//     - Used to determine when to stop the transaction
// Use Parity Reg - Use Parity Bit

class UartFsmActive(params: UartParams) extends Module {
  val io = IO(new Bundle {

    // #### Input Signals ####

    val idleTransactionStarted = Input(Bool())
    val transactionCompletedAndNotWaiting = Input(Bool())
    val stateStoppedAndWaiting = Input(Bool())

    // ### Output Signals ###
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
