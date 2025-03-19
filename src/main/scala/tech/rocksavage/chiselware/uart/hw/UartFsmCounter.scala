// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)
package tech.rocksavage.chiselware.uart.hw

import chisel3._
import chisel3.util.log2Ceil
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

class UartFsmCounter(params: UartParams) extends Module {
    val io = IO(new Bundle {

        // #### Input Signals ####
        val clockCounterMax =
            Input(UInt((log2Ceil(params.maxClockFrequency) + 1).W))
        val clockCounterIncrement = Input(Bool())

        // #### Settings Signals ####
        val numOutputBits = Input(UInt((log2Ceil(params.maxOutputBits) + 1).W))
        val useParity     = Input(Bool())

        // ### Output Signals ###
        val clockCounter =
            Output(UInt((log2Ceil(params.maxClockFrequency) + 1).W))
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
    clockCounterReg      := clockCounterIncrmentResult._1
    clockCounterOverflow := clockCounterIncrmentResult._2

    val bitCounterMax = WireDefault(io.numOutputBits + 2.U)
    when(io.useParity) {
        bitCounterMax := io.numOutputBits + 3.U
    }
    val bitCounterIncrement = WireDefault(clockCounterOverflow)
    val bitCounterOverflow  = WireDefault(false.B)
    val bitCounterIncrementResult = increment(
      bitCounterReg,
      bitCounterMax,
      bitCounterIncrement
    )
    bitCounterReg      := bitCounterIncrementResult._1
    bitCounterOverflow := bitCounterIncrementResult._2

    // if at the end of the transmission (bitcounter max - 1 & clock counter max - 1)
    // then we clear all clocks and reset the active register
    when(
      bitCounterReg === bitCounterMax - 1.U && clockCounterReg === io.clockCounterMax - 1.U
    ) {
        clockCounterReg      := 0.U
        clockCounterOverflow := false.B
        bitCounterReg        := 0.U
        bitCounterOverflow   := false.B
    }

    // Output
    io.clockCounter := clockCounterReg
    io.bitCounter   := bitCounterReg

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
}
