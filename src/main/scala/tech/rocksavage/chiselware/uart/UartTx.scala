// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)
package tech.rocksavage.chiselware.uart

import chisel3._
import chisel3.util._
import tech.rocksavage.chiselware.uart.bundle.UartTxBundle
import tech.rocksavage.chiselware.uart.error.UartTxError
import tech.rocksavage.chiselware.uart.param.UartParams

class UartTx(params: UartParams) extends Module {
  val io = IO(new UartTxBundle(params))

  // Internal Registers
  val stateReg     = RegInit(UartState.Idle)
  val bitCounter   = RegInit(0.U(log2Ceil(params.dataWidth).W))
  val clockCounter = RegInit(0.U(log2Ceil(params.maxClocksPerBit).W))
  val shiftReg     = RegInit(0.U(params.dataWidth.W))
  val errorReg     = RegInit(UartTxError.None)

  // Configuration Registers
  val clocksPerBitReg  = RegInit(0.U(log2Ceil(params.maxClocksPerBit).W))
  val numOutputBitsReg = RegInit(0.U(log2Ceil(params.maxOutputBits).W))
  val useParityReg     = RegInit(false.B)

  // Update configuration registers when in Idle state
  when(stateReg === UartState.Idle) {
    clocksPerBitReg  := io.clocksPerBitDb
    numOutputBitsReg := io.numOutputBitsDb
    useParityReg     := io.useParityDb
  }

  // FSM for UART Transmission
  switch(stateReg) {
    is(UartState.Idle) {
      when(io.tx.ready && io.tx.valid) {
        stateReg     := UartState.Start
        bitCounter   := 0.U
        clockCounter := 0.U
        shiftReg     := io.tx.data
      }
    }
    is(UartState.Start) {
      when(clockCounter === clocksPerBitReg) {
        stateReg := UartState.Data
        clockCounter := 0.U
      }.otherwise {
        clockCounter := clockCounter + 1.U
      }
    }
    is(UartState.Data) {
      when(clockCounter === clocksPerBitReg) {
        when(bitCounter === (numOutputBitsReg - 1.U)) {
          stateReg := UartState.Stop
        }.otherwise {
          bitCounter   := bitCounter + 1.U
          clockCounter := 0.U
          shiftReg     := shiftReg >> 1
        }
      }.otherwise {
        clockCounter := clockCounter + 1.U
      }
    }
    is(UartState.Stop) {
      when(clockCounter === clocksPerBitReg) {
        stateReg := UartState.Idle
        errorReg := UartTxError.None
      }.otherwise {
        clockCounter := clockCounter + 1.U
      }
    }
  }

  // Output Logic
  io.tx.rxtx := MuxLookup(stateReg.asUInt, true.B)(Seq(
    UartState.Start.asUInt -> false.B,
    UartState.Data.asUInt  -> shiftReg(0),
    UartState.Stop.asUInt  -> true.B
  ))
  io.tx.valid := stateReg === UartState.Idle
  io.tx.error := errorReg
}