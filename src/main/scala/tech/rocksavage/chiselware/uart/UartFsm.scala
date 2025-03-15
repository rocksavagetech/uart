// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)
package tech.rocksavage.chiselware.uart

import chisel3._
import chisel3.util._
import tech.rocksavage.chiselware.uart.param.UartParams

// Inputs:
// Start Transaction - Moves From Idle (or Stop) to Start
//     - Also Starts Timers, cycle after start transaction, clock counter is at 1
// Clocks Per Bit Reg - Number of Clocks per bit
//     - Used to determine when to sample data
//     - Also used to determine when to increment the bit counter
// Num Output Bits Reg - Number of bits to output
//     - Used to determine when to stop the transaction
// Use Parity Reg - Use Parity Bit

class UartFsm(params: UartParams) extends Module {
    val io = IO(new Bundle {

        // ############# input signals

        // #### Control Signals ####
        val startTransaction = Input(Bool())
        val shiftOffset      = Input(Bool())
        val waiting          = Input(Bool())

        // #### Settings Signals ####
        val clocksPerBit =
            Input(UInt((log2Ceil(params.maxClockFrequency) + 1).W))
        val numOutputBits =
            Input(UInt((log2Ceil(params.maxOutputBits) + 1).W))
        val useParity = Input(Bool())

        // #### Baud Settings ####
        val updateBaud = Input(Bool())
        val baudValid  = Input(Bool())

        // ############# output signals
        val state           = Output(UartState())
        val shift           = Output(Bool())
        val complete        = Output(Bool())
        val nextTransaction = Output(Bool())
    })

    // #### State Counter
    val counter = Module(new UartFsmCounter(params))
    counter.io.clockCounterMax := io.clocksPerBit
    counter.io.numOutputBits   := io.numOutputBits
    counter.io.useParity       := io.useParity

    // #### State Active Mealy
    // This is a combinational Equivalent to io.complete to avoid a comb loop
    val combComplete  = WireInit(false.B)
    val combComplete1 = WireInit(false.B)
    combComplete1 := (counter.io.clockCounter === io.clocksPerBit - 1.U)
    val combComplete2 = WireInit(false.B)
    combComplete2 := (
      (!io.useParity && counter.io.bitCounter === io.numOutputBits + 1.U) || (io.useParity && counter.io.bitCounter === io.numOutputBits + 2.U)
    )
    combComplete := combComplete1 && combComplete2

    val active    = WireInit(false.B)
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
    val state     = WireDefault(UartState.Idle)
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
    io.state  := state

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

    // Helper Functions
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
