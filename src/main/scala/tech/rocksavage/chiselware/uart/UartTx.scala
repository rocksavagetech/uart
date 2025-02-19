// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)
package tech.rocksavage.chiselware.uart

import chisel3._
import chisel3.util._
import tech.rocksavage.chiselware.uart.bundle.UartTxBundle
import tech.rocksavage.chiselware.uart.param.UartParams

/** A UART transmitter module that handles the transmission of UART data.
  * @param params
  *   Configuration parameters for the UART.
  * @param formal
  *   A boolean value to enable formal verification.
  */
class UartTx(params: UartParams, formal: Boolean = true) extends Module {
    val io = IO(new UartTxBundle(params))

    // ###################
    // Input Control State Registers
    // ###################
    // These registers hold the configuration settings loaded at the start of a transmission.
    val clocksPerBitReg = RegInit(0.U((log2Ceil(params.maxClocksPerBit) + 1).W))
    val numOutputBitsReg = RegInit(0.U((log2Ceil(params.maxOutputBits) + 1).W))
    val useParityReg     = RegInit(false.B)
    // New registers for parity (nomenclature as in UartRx.scala)
    val parityOddReg = RegInit(false.B)
    val clocksPerBitDbReg = RegInit(
      0.U((log2Ceil(params.maxClocksPerBit) + 1).W)
    )
    val numOutputBitsDbReg = RegInit(
      0.U((log2Ceil(params.maxOutputBits) + 1).W)
    )
    val useParityDbReg = RegInit(false.B)
    val parityOddDbReg = RegInit(false.B) // New parity configuration register

    val clocksPerBitNext = WireInit(
      0.U((log2Ceil(params.maxClocksPerBit) + 1).W)
    )
    val numOutputBitsNext = WireInit(
      0.U((log2Ceil(params.maxOutputBits) + 1).W)
    )
    val useParityNext = WireInit(false.B)
    val parityOddNext = WireInit(false.B) // New NEXT signal for parity odd
    val dataNext      = WireInit(0.U(params.maxOutputBits.W))
    val loadNext      = WireInit(false.B)

    clocksPerBitReg  := clocksPerBitNext
    numOutputBitsReg := numOutputBitsNext
    useParityReg     := useParityNext
    parityOddReg     := parityOddNext // Latch new parity configuration

    clocksPerBitDbReg  := io.txConfig.clocksPerBitDb
    numOutputBitsDbReg := io.txConfig.numOutputBitsDb
    useParityDbReg     := io.txConfig.useParityDb
    parityOddDbReg     := io.txConfig.parityOddDb // load configuration

    // ###################
    // State FSM
    // ###################

    val uartFsm = Module(new UartFsm(params, formal))

    val stateWire    = uartFsm.io.state
    val sampleWire   = uartFsm.io.sample
    val completeWire = uartFsm.io.complete

    val startTransaction = (stateWire === UartState.Idle) && loadNext

    uartFsm.io.startTransaction := startTransaction
    uartFsm.io.clocksPerBitReg  := clocksPerBitReg
    uartFsm.io.numOutputBitsReg := numOutputBitsReg
    uartFsm.io.useParityReg     := useParityReg

    // Latch incoming data and capture txData for parity calculation.
    // (txDataReg holds the unshifted data for parity computation.)
    val txDataReg = RegInit(0.U(params.maxOutputBits.W))
    when(startTransaction) {
        txDataReg := io.txConfig.data
    }
    dataNext := io.txConfig.data
    loadNext := io.txConfig.load

    // ###################
    // Shift Register for Storing Data to Transmit
    // ###################
    // In the idle state, when a new transmission is requested (via io.load), the transmit data is loaded.
    val dataShiftReg  = RegInit(0.U(params.maxOutputBits.W))
    val dataShiftNext = WireInit(0.U(params.maxOutputBits.W))
    dataShiftReg := dataShiftNext

    // ###################
    // Output Register
    // ###################
    // The TX output should be high in idle.
    val txNext = WireInit(true.B)
    // The calculateTxOut now also has a branch for the Parity state.
    txNext := calculateTxOut(stateWire, dataShiftReg, txDataReg, parityOddReg)
    io.tx  := txNext

    // ###################
    // Finite State Machine (FSM)
    // ###################
    // Updated state transitions to include parity mode.

    clocksPerBitNext := Mux(
      stateWire === UartState.Idle,
      clocksPerBitDbReg,
      clocksPerBitReg
    )

    numOutputBitsNext := Mux(
      stateWire === UartState.Idle,
      numOutputBitsDbReg,
      numOutputBitsReg
    )

    useParityNext := Mux(
      stateWire === UartState.Idle,
      useParityDbReg,
      useParityReg
    )

    parityOddNext := Mux(
      stateWire === UartState.Idle,
      parityOddDbReg,
      parityOddReg
    )

    dataShiftNext := calculateDataShiftNext(
      dataShiftReg,
      dataNext,
      startTransaction,
      sampleWire
    )

    // -------------------------
    // FSM and Data Handling Functions
    // -------------------------

    def calculateDataShiftNext(
        dataShiftReg: UInt,
        loadData: UInt,
        startTransaction: Bool,
        sampleWire: Bool
    ): UInt = {
        val dataShiftNext = WireDefault(dataShiftReg)

        when(startTransaction) {
            dataShiftNext := Reverse(loadData)
        }
        when(sampleWire) {
            dataShiftNext := Cat(0.U(1.W), dataShiftReg >> 1)
        }
        dataShiftNext
    }

    // The TX output is driven based on the current FSM state.
    // For parity state we compute the parity bit from the full (latched) data.
    def calculateTxOut(
        stateReg: UartState.Type,
        dataShiftReg: UInt,
        txData: UInt,
        parityOddReg: Bool
    ): Bool = {
        val txOut = WireInit(true.B)
        switch(stateReg) {
            is(UartState.Idle) {
                txOut := true.B
            }
            is(UartState.Start) {
                txOut := false.B
            }
            is(UartState.Data) {
                txOut := dataShiftReg(0)
            }
            is(UartState.Parity) {
                val dataParity = txData.xorR
                txOut := Mux(parityOddReg, ~dataParity, dataParity)
            }
            is(UartState.Stop) {
                txOut := true.B
            }
        }
        txOut
    }
}
