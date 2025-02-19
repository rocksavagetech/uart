// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)
package tech.rocksavage.chiselware.uart

import chisel3._
import chisel3.util._
import tech.rocksavage.chiselware.uart.param.UartParams

class UartFsm(params: UartParams, formal: Boolean = true) extends Module {
    val io = IO(new Bundle {
        val startTransaction = Input(Bool())

        val clocksPerBitReg =
            Input(UInt((log2Ceil(params.maxClocksPerBit) + 1).W))
        val numOutputBitsReg =
            Input(UInt((log2Ceil(params.maxOutputBits) + 1).W))
        val useParityReg = Input(Bool())

        val state    = Output(UartState())
        val sample   = Output(Bool())
        val complete = Output(Bool())
    })

    // internal signals
    val clockCounterReg = RegInit(0.U((log2Ceil(params.maxClocksPerBit) + 1).W))
    val bitCounterReg   = RegInit(0.U((log2Ceil(params.maxOutputBits) + 1).W))

    // output signals
    val stateReg  = RegInit(UartState.Idle)
    val stateNext = WireInit(stateReg)

    val sampleNext = WireDefault(false.B)

    val complete = WireDefault(false.B)

    stateReg := stateNext

    io.state    := stateReg
    io.sample   := sampleNext
    io.complete := complete

    // FSM
    switch(stateReg) {
        is(UartState.Idle) {
            when(io.startTransaction) {
                stateNext := UartState.Start
            }
        }
        is(UartState.Start) {
            when(clockCounterReg === io.clocksPerBitReg) {
                stateNext := UartState.Data
            }
        }
        is(UartState.Data) {
            when(clockCounterReg === io.clocksPerBitReg - 1.U) {

                sampleNext := true.B

                when(bitCounterReg === io.numOutputBitsReg - 1.U) {
                    when(io.useParityReg) {
                        stateNext := UartState.Parity
                    }.otherwise {
                        complete  := true.B
                        stateNext := UartState.Stop
                    }
                }.otherwise {
                    stateNext := UartState.Data
                }
            }
        }
        is(UartState.Parity) {
            when(clockCounterReg === io.clocksPerBitReg) {
                complete  := true.B
                stateNext := UartState.Stop
            }
        }
        is(UartState.Stop) {
            when(clockCounterReg === io.clocksPerBitReg) {
                stateNext := UartState.Idle
            }
        }
    }
}
