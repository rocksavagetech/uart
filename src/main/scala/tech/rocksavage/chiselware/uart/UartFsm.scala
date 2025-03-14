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
        val state    = Output(UartState())
        val shift    = Output(Bool())
        val complete = Output(Bool())
    })

    val startTransaction = WireDefault(io.startTransaction)
    val stopTransaction  = WireDefault(false.B)

    // Internal State
    val activePrev = RegInit(false.B)
    val active     = WireDefault(activePrev)
    active := activePrev
    when(!activePrev && startTransaction) {
        active := true.B
    }
    when(activePrev && stopTransaction) {
        active := false.B
    }

    activePrev := active

    // Counters
    val clockCounterMax       = WireDefault(io.clocksPerBit)
    val clockCounterIncrement = WireDefault(active)

    val clockCounter = RegInit(0.U((log2Ceil(params.maxClockFrequency) + 1).W))
    val clockCounterOverflow = WireInit(false.B)

    val clockCounterIncrmentResult = increment(
      clockCounter,
      clockCounterMax,
      clockCounterIncrement
    )
    clockCounter         := clockCounterIncrmentResult._1
    clockCounterOverflow := clockCounterIncrmentResult._2

    val bitCounterMax = WireDefault(io.numOutputBits + 2.U)
    when(io.useParity) {
        bitCounterMax := io.numOutputBits + 3.U
    }
    val bitCounterIncrement = WireDefault(clockCounterOverflow)

    val bitCounter = RegInit(0.U((log2Ceil(params.maxOutputBits) + 1).W))
    val bitCounterOverflow = WireDefault(false.B)

    val bitCounterIncrementResult = increment(
      bitCounter,
      bitCounterMax,
      bitCounterIncrement
    )
    bitCounter         := bitCounterIncrementResult._1
    bitCounterOverflow := bitCounterIncrementResult._2

    // if at the end of the transmission (bitcounter max - 1 & clock counter max - 1)
    // then we clear all clocks and reset the active register
    when(
      bitCounter === bitCounterMax - 1.U && clockCounter === clockCounterMax - 1.U
    ) {
        clockCounter         := 0.U
        clockCounterOverflow := false.B
        bitCounter           := 0.U
        bitCounterOverflow   := false.B
    }

    // State (Combinational)
    val prevState = RegInit(UartState.Idle)
    val state = stateCase(
      numBits = io.numOutputBits,
      bitCount = bitCounter,
      parity = io.useParity,
      active = active,
      updateBaud = io.updateBaud,
      baudValid = io.baudValid,
      clockCounterOverflow = clockCounterOverflow,
      startTransaction = startTransaction,
      prevState = prevState
    )
    prevState := state
    io.state  := state

    // Outputs

    val whenShift = WireInit(false.B)
    when(io.shiftOffset) {
        // RX
        whenShift := (clockCounter === (io.clocksPerBit >> 1.U))
    }.otherwise {
        // TX
        whenShift := (clockCounter === clockCounterMax - 1.U) &&
            ((bitCounter =/= io.numOutputBits) || (io.clocksPerBit === 1.U))
    }

//    io.sample := (state =/= UartState.Idle) && (state =/= UartState.BaudUpdating) && (clockCounter === (io.clocksPerBit >> 1.U))
//    io.completeSample := (state === UartState.Stop) && (clockCounter === (io.clocksPerBit >> 1.U))
    io.shift := whenShift && (prevState =/= UartState.Start) &&
        (prevState =/= UartState.Idle) &&
        (prevState =/= UartState.BaudUpdating)
    io.complete := (state === UartState.Stop) && (clockCounter === clockCounterMax - 1.U)
//    io.shiftRegister := state === UartState.Data || state === UartState.Parity

    when(io.complete) {
        activePrev := false.B
    }

    // Helper Functions
    def increment(value: UInt, max: UInt, condition: Bool): (UInt, Bool) = {
        val incrementedInner = WireDefault(value)
        val incremented      = WireDefault(value)
        val overflow         = WireDefault(false.B)

        when(condition) {
            incrementedInner := value + 1.U

            when((incrementedInner >= max) || (incrementedInner < value)) {
                incremented := 0.U
                overflow    := true.B
            }.otherwise {
                incremented := incrementedInner
            }
        }

        (incremented, overflow)
    }

    def stateCase(
        numBits: UInt,
        bitCount: UInt,
        parity: Bool,
        active: Bool,
        updateBaud: Bool,
        baudValid: Bool,
        clockCounterOverflow: Bool,
        startTransaction: Bool,
        prevState: UartState.Type
    ): UartState.Type = {

        val state      = WireDefault(UartState.Idle)
        val stateInner = WireDefault(UartState.Idle)

        when(prevState === UartState.Idle && updateBaud) {
            stateInner := UartState.BaudUpdating
        }
        when(prevState === UartState.BaudUpdating && baudValid) {
            stateInner := UartState.Idle
        }
        when(prevState === UartState.BaudUpdating && !baudValid) {
            stateInner := UartState.BaudUpdating
        }

        when(stateInner =/= UartState.BaudUpdating) {
            when(active) {
                when(startTransaction) {
                    state := UartState.Start
                }.elsewhen(bitCounter === 0.U) {
                    state := UartState.Start
                }.elsewhen(bitCount > 0.U && bitCount <= numBits) {
                    state := UartState.Data
                }.elsewhen(parity && bitCount === numBits + 1.U) {
                    state := UartState.Parity
                }.elsewhen(!parity && bitCount === numBits + 1.U) {
                    state := UartState.Stop
                }.elsewhen(parity && bitCount === numBits + 2.U) {
                    state := UartState.Stop
                }.otherwise {
                    printf("Skipping Idle and going straight to start state")
                    state := UartState.Start
                }
            }
        }.otherwise(state := baudUpdating)
        state
    }
}
