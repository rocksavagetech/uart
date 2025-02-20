// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)
package tech.rocksavage.chiselware.uart

import chisel3._
import chisel3.util._
import tech.rocksavage.chiselware.uart.param.UartParams

class UartFsm(params: UartParams, formal: Boolean = true) extends Module {
    val io = IO(new Bundle {

        // ############# input signals
        val startTransaction = Input(Bool())
        val clocksPerBitReg =
            Input(UInt((log2Ceil(params.maxClocksPerBit) + 1).W))
        val numOutputBitsReg =
            Input(UInt((log2Ceil(params.maxOutputBits) + 1).W))
        val useParityReg = Input(Bool())

        // ############# output signals
        val state        = Output(UartState())
        val sampleStart  = Output(Bool())
        val sampleData   = Output(Bool())
        val sampleParity = Output(Bool())
        val complete     = Output(Bool())
    })

    val activeReg = RegInit(false.B)

    val stateReg            = RegInit(UartState.Idle)
    val sampleReg           = RegInit(false.B)
    val incrementCounterReg = RegInit(false.B)
    val completeReg         = RegInit(false.B)

    val clockCounterReg = RegInit(0.U((log2Ceil(params.maxClocksPerBit) + 1).W))
    val bitCounterReg   = RegInit(0.U((log2Ceil(params.maxOutputBits) + 1).W))

    // internal signals
    activeReg := computeActiveNext(
      activeReg,
      io.startTransaction,
      io.complete
    )

    clockCounterReg := Mux(
      !completeReg,
      incrementCounter(
        clockCounterReg,
        io.clocksPerBitReg - 1.U,
        condition = stateReg =/= UartState.Idle
      ),
      0.U
    )

    val sampleNext =
        (clockCounterReg === ((io.clocksPerBitReg - 1.U) >> 1.U))
    val incrementCounterNext = clockCounterReg === (io.clocksPerBitReg - 1.U)

    bitCounterReg := Mux(
      !completeReg,
      incrementCounter(
        bitCounterReg,
        io.numOutputBitsReg - 1.U,
        condition = incrementCounterNext && (stateReg === UartState.Data)
      ),
      0.U
    )

    val completeNext = computeCompleteNext(
      stateReg = stateReg,
      sampleNext = sampleNext,
      bitCounterReg = bitCounterReg,
      numOutputBitsReg = io.numOutputBitsReg,
      useParityReg = io.useParityReg,
      clocksPerBitReg = io.clocksPerBitReg,
      clockCounterReg = clockCounterReg
    )

    val stateNext = computeStateNext(
      stateReg = stateReg,
      startTransaction = io.startTransaction,
      clockCounterReg = clockCounterReg,
      bitCounterReg = bitCounterReg,
      clocksPerBitReg = io.clocksPerBitReg,
      numOutputBitsReg = io.numOutputBitsReg,
      useParityReg = io.useParityReg,
      sampleNext = sampleNext
    )

    sampleReg           := sampleNext
    incrementCounterReg := incrementCounterNext
    completeReg         := completeNext
    stateReg            := stateNext

    io.state        := stateReg
    io.sampleStart  := (stateReg === UartState.Start) && sampleNext
    io.sampleData   := (stateReg === UartState.Data) && sampleNext
    io.sampleParity := (stateReg === UartState.Parity) && sampleNext
    io.complete     := completeNext

    def incrementCounter(counter: UInt, max: UInt, condition: Bool): UInt = {
        val next = WireDefault(counter)
        when(condition) {
            when(counter === max) {
                next := 0.U
            }.otherwise {
                next := counter + 1.U
            }
        }
        next
    }

    def computeStateNext(
        stateReg: UartState.Type,
        startTransaction: Bool,
        clockCounterReg: UInt,
        bitCounterReg: UInt,
        clocksPerBitReg: UInt,
        numOutputBitsReg: UInt,
        useParityReg: Bool,
        sampleNext: Bool
    ): UartState.Type = {
        val stateNext = WireDefault(stateReg)

        switch(stateReg) {
            is(UartState.Idle) {
                when(startTransaction) {
                    stateNext := UartState.Start
                }
            }
            is(UartState.Start) {
                when(sampleNext) {
                    stateNext := UartState.Data
                }
            }
            is(UartState.Data) {
                when(sampleNext) {
                    when(bitCounterReg === numOutputBitsReg - 1.U) {
                        when(useParityReg) {
                            stateNext := UartState.Parity
                        }.otherwise {
                            stateNext := UartState.Stop
                        }
                    }
                }
            }
            is(UartState.Parity) {
                when(sampleNext) {
                    stateNext := UartState.Stop
                }
            }
            is(UartState.Stop) {
                when(sampleNext) {
                    stateNext := UartState.Idle
                }
            }
        }
        stateNext
    }

    def computeCompleteNext(
        stateReg: UartState.Type,
        sampleNext: Bool,
        bitCounterReg: UInt,
        numOutputBitsReg: UInt,
        useParityReg: Bool,
        clocksPerBitReg: UInt,
        clockCounterReg: UInt
    ): Bool = {
        val completeNext = WireDefault(false.B)

        switch(stateReg) {
            is(UartState.Data) {
                when(clockCounterReg === (clocksPerBitReg - 1.U)) {
                    when(bitCounterReg === numOutputBitsReg - 1.U) {
                        when(!useParityReg) {
                            completeNext := true.B
                        }
                    }
                }
            }
            is(UartState.Parity) {
                when(clockCounterReg === (clocksPerBitReg - 1.U)) {
                    completeNext := true.B
                }
            }
        }
        completeNext
    }

    def computeActiveNext(
        activeReg: Bool,
        startTransaction: Bool,
        completeWire: Bool
    ): Bool = {
        val activeNext = WireDefault(activeReg)
        when(completeWire) {
            activeNext := false.B
        }
        when(startTransaction) {
            activeNext := true.B
        }
        activeNext
    }
}
