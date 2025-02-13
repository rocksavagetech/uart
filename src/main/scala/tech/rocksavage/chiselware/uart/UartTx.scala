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
    // Internal Registers
    // ###################
    // Current state of the UART FSM
    val stateReg = RegInit(UartState.Idle)
    // Bit counter for transmitted bits
    val bitCounterReg = RegInit(0.U((log2Ceil(params.dataWidth) + 1).W))
    // Clock counter for timing
    val clockCounterReg = RegInit(0.U((log2Ceil(params.maxClocksPerBit) + 1).W))
    // Next values for the FSM registers
    val stateNext      = WireInit(UartState.Idle)
    val bitCounterNext = WireInit(0.U((log2Ceil(params.dataWidth) + 1).W))
    val clockCounterNext = WireInit(
      0.U((log2Ceil(params.maxClocksPerBit) + 1).W)
    )
    stateReg        := stateNext
    bitCounterReg   := bitCounterNext
    clockCounterReg := clockCounterNext

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

    clocksPerBitReg    := clocksPerBitNext
    numOutputBitsReg   := numOutputBitsNext
    useParityReg       := useParityNext
    parityOddReg       := parityOddNext // Latch new parity configuration
    clocksPerBitDbReg  := io.txConfig.clocksPerBitDb
    numOutputBitsDbReg := io.txConfig.numOutputBitsDb
    useParityDbReg     := io.txConfig.useParityDb
    parityOddDbReg := io.txConfig.parityOddDb // load configuration

    // Latch incoming data and capture txData for parity calculation.
    // (txDataReg holds the unshifted data for parity computation.)
    val txDataReg = RegInit(0.U(params.maxOutputBits.W))
    when(stateReg === UartState.Idle && loadNext) {
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
    txNext := calculateTxOut(stateReg, dataShiftReg, txDataReg, parityOddReg)
    io.tx  := txNext

    // ###################
    // Finite State Machine (FSM)
    // ###################
    // Updated state transitions to include parity mode.
    stateNext := calculateStateNext(
      stateReg,
      loadNext,
      clockCounterReg,
      clocksPerBitReg,
      bitCounterReg,
      numOutputBitsReg,
      useParityReg
    )
    bitCounterNext := calculateBitCounterNext(
      stateReg,
      clockCounterReg,
      clocksPerBitReg,
      bitCounterReg,
      numOutputBitsReg
    )
    clockCounterNext := calculateClockCounterNext(
      stateReg,
      clockCounterReg,
      clocksPerBitReg
    )
    clocksPerBitNext := calculateClocksPerBitNext(
      stateReg,
      clocksPerBitReg,
      clocksPerBitDbReg,
      loadNext
    )
    numOutputBitsNext := calculateNumOutputBitsNext(
      stateReg,
      numOutputBitsReg,
      numOutputBitsDbReg,
      loadNext
    )
    useParityNext := calculateUseParityNext(
      stateReg,
      useParityReg,
      useParityDbReg,
      loadNext
    )
    parityOddNext := calculateParityOddNext(
      stateReg,
      parityOddReg,
      parityOddDbReg
    )
    dataShiftNext := calculateDataShiftNext(
      stateReg,
      clockCounterReg,
      clocksPerBitReg,
      dataShiftReg,
      dataNext,
      loadNext
    )

    // -------------------------
    // FSM and Data Handling Functions
    // -------------------------
    def calculateStateNext(
        stateReg: UartState.Type,
        load: Bool,
        clockCounterReg: UInt,
        clocksPerBitReg: UInt,
        bitCounterReg: UInt,
        numOutputBitsReg: UInt,
        useParity: Bool
    ): UartState.Type = {
        val stateNext = WireInit(stateReg)
        switch(stateReg) {
            is(UartState.Idle) {
                when(load) {
                    stateNext := UartState.Start
                }
            }
            is(UartState.Start) {
                when(clockCounterReg === clocksPerBitReg) {
                    stateNext := UartState.Data
                }
            }
            is(UartState.Data) {
                when(clockCounterReg === clocksPerBitReg - 1.U) {
                    when(bitCounterReg === numOutputBitsReg - 1.U) {
                        when(useParity) {
                            stateNext := UartState.Parity
                        }.otherwise {
                            stateNext := UartState.Stop
                        }
                    }.otherwise {
                        stateNext := UartState.Data
                    }
                }
            }
            is(UartState.Parity) {
                when(clockCounterReg === clocksPerBitReg) {
                    stateNext := UartState.Stop
                }
            }
            is(UartState.Stop) {
                when(clockCounterReg === clocksPerBitReg) {
                    stateNext := UartState.Idle
                }
            }
        }
        stateNext
    }

    def calculateBitCounterNext(
        stateReg: UartState.Type,
        clockCounterReg: UInt,
        clocksPerBitReg: UInt,
        bitCounterReg: UInt,
        numOutputBitsReg: UInt
    ): UInt = {
        val bitCounterNext = WireInit(bitCounterReg)
        switch(stateReg) {
            is(UartState.Idle) {
                bitCounterNext := 0.U
            }
            is(UartState.Data) {
                when(clockCounterReg === clocksPerBitReg) {
                    when(bitCounterReg =/= numOutputBitsReg) {
                        bitCounterNext := bitCounterReg + 1.U
                    }.otherwise {
                        bitCounterNext := 0.U
                    }
                }
            }
            // In Parity and Stop states, the bit counter is reset.
            is(UartState.Parity) {
                bitCounterNext := 0.U
            }
            is(UartState.Stop) {
                bitCounterNext := 0.U
            }
        }
        bitCounterNext
    }

    def calculateClockCounterNext(
        stateReg: UartState.Type,
        clockCounterReg: UInt,
        clocksPerBitReg: UInt
    ): UInt = {
        val clockCounterNext = WireInit(clockCounterReg)
        switch(stateReg) {
            is(UartState.Idle) {
                clockCounterNext := 0.U
            }
            is(
              UartState.Start,
              UartState.Data,
              UartState.Parity,
              UartState.Stop
            ) {
                when(clockCounterReg === clocksPerBitReg) {
                    clockCounterNext := 0.U
                }.otherwise {
                    clockCounterNext := clockCounterReg + 1.U
                }
            }
        }
        clockCounterNext
    }

    def calculateClocksPerBitNext(
        stateReg: UartState.Type,
        clocksPerBitReg: UInt,
        clocksPerBitDbReg: UInt,
        load: Bool
    ): UInt = {
        val clocksPerBitNext = WireInit(clocksPerBitReg)
        when(stateReg === UartState.Idle) {
            clocksPerBitNext := clocksPerBitDbReg
        }
        clocksPerBitNext
    }

    def calculateNumOutputBitsNext(
        stateReg: UartState.Type,
        numOutputBitsReg: UInt,
        numOutputBitsDbReg: UInt,
        load: Bool
    ): UInt = {
        val numOutputBitsNext = WireInit(numOutputBitsReg)
        when(stateReg === UartState.Idle) {
            numOutputBitsNext := numOutputBitsDbReg
        }
        numOutputBitsNext
    }

    def calculateUseParityNext(
        stateReg: UartState.Type,
        useParityReg: Bool,
        useParityDbReg: Bool,
        load: Bool
    ): Bool = {
        val useParityNext = WireInit(useParityReg)
        when(stateReg === UartState.Idle) {
            useParityNext := useParityDbReg
        }
        useParityNext
    }

    def calculateParityOddNext(
        stateReg: UartState.Type,
        parityOddReg: Bool,
        parityOddDbReg: Bool
    ): Bool = {
        val parityOddNext = WireInit(parityOddReg)
        switch(stateReg) {
            is(UartState.Idle) {
                parityOddNext := parityOddDbReg
            }
        }
        parityOddNext
    }

    def calculateDataShiftNext(
        stateReg: UartState.Type,
        clockCounterReg: UInt,
        clocksPerBitReg: UInt,
        dataShiftReg: UInt,
        loadData: UInt,
        load: Bool
    ): UInt = {
        val dataShiftNext = WireInit(dataShiftReg)
        switch(stateReg) {
            is(UartState.Idle) {
                when(load) {
                    // Load new data when starting transmission.
                    dataShiftNext := loadData
                }
            }
            is(UartState.Data) {
                when(clockCounterReg === clocksPerBitReg - 1.U) {
                    dataShiftNext := Cat(0.U(1.W), dataShiftReg >> 1)
                }
            }
            is(UartState.Stop) {
                dataShiftNext := dataShiftReg
            }
            is(UartState.Parity) {
                // In parity state, no shifting occurs.
                dataShiftNext := dataShiftReg
            }
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
