// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)
package tech.rocksavage.chiselware.uart

import chisel3._
import chisel3.util._
import tech.rocksavage.chiselware.uart.bundle.UartRxBundle
import tech.rocksavage.chiselware.uart.error.UartRxError
import tech.rocksavage.chiselware.uart.param.UartParams

/** A UART receiver module that handles the reception of UART data.
  *
  * @param params
  *   Configuration parameters for the UART.
  * @param formal
  *   A boolean value to enable formal verification.
  */
class UartRx(params: UartParams, formal: Boolean = true) extends Module {

    /** Input/Output interface for the UART receiver */
    val io = IO(new UartRxBundle(params))

    // ###################
    // Internal
    // ###################

    /** Current state of the UART FSM */
    val stateReg = RegInit(UartState.Idle)

    /** Bit counter for received bits */
    val bitCounterReg = RegInit(0.U((log2Ceil(params.dataWidth) + 1).W))

    /** Clock counter for timing */
    val clockCounterReg = RegInit(0.U((log2Ceil(params.maxClocksPerBit) + 1).W))

    /** Next state of the UART FSM */
    val stateNext = WireInit(UartState.Idle)

    /** Next bitcounter value */
    val bitCounterNext = WireInit(0.U((log2Ceil(params.dataWidth) + 1).W))

    /** Next clock counter value */
    val clockCounterNext = WireInit(
      0.U((log2Ceil(params.maxClocksPerBit) + 1).W)
    )

    stateReg        := stateNext
    bitCounterReg   := bitCounterNext
    clockCounterReg := clockCounterNext

    // ###################
    // Input Control State Registers
    // ###################

    /** Clocks per bit register */
    val clocksPerBitReg = RegInit(0.U((log2Ceil(params.maxClocksPerBit) + 1).W))

    /** Number of output bits register */
    val numOutputBitsReg = RegInit(0.U((log2Ceil(params.maxOutputBits) + 1).W))

    /** Parity usage register */
    val useParityReg = RegInit(false.B)

    val clocksPerBitDbReg = RegInit(
      0.U((log2Ceil(params.maxClocksPerBit) + 1).W)
    )

    val numOutputBitsDbReg = RegInit(
      0.U((log2Ceil(params.maxOutputBits) + 1).W)
    )

    val useParityDbReg = RegInit(false.B)

    /** Next Clocks per bit register */
    val clocksPerBitNext = WireInit(
      0.U((log2Ceil(params.maxClocksPerBit) + 1).W)
    )

    /** Next Number of output bits register */
    val numOutputBitsNext = WireInit(
      0.U((log2Ceil(params.maxOutputBits) + 1).W)
    )

    /** Next Parity usage register */
    val useParityNext = WireInit(false.B)

    clocksPerBitReg  := clocksPerBitNext
    numOutputBitsReg := numOutputBitsNext
    useParityReg     := useParityNext

    clocksPerBitDbReg  := io.rxConfig.clocksPerBitDb
    numOutputBitsDbReg := io.rxConfig.numOutputBitsDb
    useParityDbReg     := io.rxConfig.useParityDb

    // ###################
    // RX Input Synchronization
    // ###################

    /** Shift register for synchronizing received data */
    val rxSyncRegs = RegInit(VecInit(Seq.fill(params.syncDepth)(true.B)).asUInt)

    /** Next shift register for synchronizing received data */
    val rxSyncNext = WireInit(0.U(params.syncDepth.W))

    rxSyncRegs := rxSyncNext

    /** Current synchronized rx input */
    val rxSync = rxSyncRegs(params.syncDepth - 1)

    // ###################
    // Shift Register for Storing Received Data
    // ###################

    /** Shift register for storing received data */
    val dataShiftReg = RegInit(0.U(params.maxOutputBits.W))

    /** Next shift register for storing received data */
    val dataShiftNext = WireInit(0.U(params.maxOutputBits.W))

    dataShiftReg := dataShiftNext

    // ###################
    // Output Registers
    // ###################

    /** Data output register */
    val dataReg = RegInit(0.U(params.maxOutputBits.W))

    /** Valid output register */
    val validReg = RegInit(false.B)

    /** Error output register */
    val errorReg = RegInit(UartRxError.None)

    /** Next Data output register */
    val dataNext = WireInit(0.U(params.maxOutputBits.W))

    /** Next Valid output register */
    val validNext = WireInit(false.B)

    /** Next Error output register */
    val errorNext = WireInit(UartRxError.None)

    dataReg  := dataNext
    validReg := validNext
    errorReg := errorNext

    // ###################
    // Output Assignments
    // ###################

    /** Assign output data */
    io.data := dataReg

    /** Assign output valid signal */
    io.valid := validReg

    /** Assign output error signal */
    io.error := errorReg // Ensure io.error is always assigned

    // ###################
    // Finite State Machine (FSM)
    // ###################

    stateNext := calculateStateNext(
      stateReg,
      rxSync,
      clockCounterReg,
      clocksPerBitReg,
      bitCounterReg,
      numOutputBitsReg
    )

    bitCounterNext := calculateBitCounterNext(
      stateReg,
      rxSync,
      clockCounterReg,
      clocksPerBitReg,
      bitCounterReg,
      numOutputBitsReg
    )

    clockCounterNext := calculateClockCounterNext(
      stateReg,
      rxSync,
      clockCounterReg,
      clocksPerBitReg
    )

    clocksPerBitNext := calculateClocksPerBitNext(
      stateReg,
      clocksPerBitReg,
      clocksPerBitDbReg
    )

    numOutputBitsNext := calculateNumOutputBitsNext(
      stateReg,
      numOutputBitsReg,
      numOutputBitsDbReg
    )

    useParityNext := calculateUseParityNext(
      stateReg,
      useParityReg,
      useParityDbReg
    )

    rxSyncNext := calculateRxSyncNext(
      rxSyncRegs,
      io.rx
    )

    dataShiftNext := calculateDataShiftNext(
      stateReg,
      rxSync,
      clockCounterReg,
      clocksPerBitReg,
      bitCounterReg,
      numOutputBitsReg,
      dataShiftReg
    )

    dataNext := calculateDataNext(
      stateReg,
      clockCounterReg,
      clocksPerBitReg,
      bitCounterReg,
      numOutputBitsReg,
      dataShiftReg
    )

    validNext := calculateValidNext(
      stateReg,
      clockCounterReg,
      clocksPerBitReg,
      bitCounterReg,
      numOutputBitsReg
    )

    errorNext := calculateErrorNext(
      stateReg,
      rxSync,
      clockCounterReg,
      clocksPerBitReg
    )

    /** Computes the next state of the UART FSM.
      *
      * @return
      *   The next state of the UART.
      */
    def calculateStateNext(
        stateReg: UartState.Type,
        rxSync: Bool,
        clockCounterReg: UInt,
        clocksPerBitReg: UInt,
        bitCounterReg: UInt,
        numOutputBitsReg: UInt
    ): UartState.Type = {

        /** Next state wire */
        val stateNext = WireInit(UartState.Idle)
        switch(stateReg) {
            is(UartState.Idle) {
                when(rxSync === false.B) {
                    stateNext := UartState.Start
                }
            }
            is(UartState.Start) {
                when(rxSync =/= true.B) {
                    stateNext := UartState.Data
                }
            }
            is(UartState.Data) {
                when(clockCounterReg === clocksPerBitReg) {
                    when(bitCounterReg === (numOutputBitsReg)) {
                        stateNext := UartState.Stop
                    }.otherwise {
                        stateNext := UartState.Data
                    }
                }.otherwise {
                    stateNext := UartState.Data
                }
            }
            is(UartState.Stop) {
                when(clockCounterReg =/= clocksPerBitReg) {
                    stateNext := UartState.Stop
                }
            }
        }
        stateNext
    }

    /** Computes the next bit counter value.
      *
      * @return
      *   The next bit counter value.
      */
    def calculateBitCounterNext(
        stateReg: UartState.Type,
        rxSync: Bool,
        clockCounterReg: UInt,
        clocksPerBitReg: UInt,
        bitCounterReg: UInt,
        numOutputBitsReg: UInt
    ): UInt = {
        val bitCounterNext = WireInit(0.U((log2Ceil(params.dataWidth) + 1).W))

        // default for bitCounterNext is the current value of bitCounterReg
        bitCounterNext := bitCounterReg

        switch(stateReg) {
            is(UartState.Idle) {
                when(rxSync === false.B) {
                    bitCounterNext := 0.U
                }
            }
            is(UartState.Data) {
                when(clockCounterReg === clocksPerBitReg) {
                    when(bitCounterReg =/= (numOutputBitsReg)) {
                        bitCounterNext := bitCounterReg + 1.U
                    }
                }
            }
        }
        bitCounterNext
    }

    /** Computes the next clock counter value.
      *
      * @return
      *   The next clock counter value.
      */
    def calculateClockCounterNext(
        stateReg: UartState.Type,
        rxSync: Bool,
        clockCounterReg: UInt,
        clocksPerBitReg: UInt
    ): UInt = {
        val clockCounterNext = WireInit(
          0.U((log2Ceil(params.maxClocksPerBit) + 1).W)
        )

        // default for clockCounterNext is the current value of clockCounterReg
        clockCounterNext := clockCounterReg
        switch(stateReg) {
            is(UartState.Idle) {
                when(rxSync === false.B) {
                    clockCounterNext := 0.U
                }
            }
            is(UartState.Data) {
                when(clockCounterReg === clocksPerBitReg) {
                    clockCounterNext := 0.U
                }.otherwise {
                    clockCounterNext := clockCounterReg + 1.U
                }
            }
            is(UartState.Stop) {
                when(clockCounterReg =/= clocksPerBitReg) {
                    clockCounterReg := clockCounterReg + 1.U
                }
            }
        }
        clockCounterNext
    }

    /** Computes the next rxSync value.
      *
      * @return
      *   The next rxSync value.
      */
    def calculateRxSyncNext(
        rxSyncRegs: UInt,
        rx: Bool
    ): UInt = {
        val rxSyncNext = WireInit(0.U(params.syncDepth.W))
        rxSyncNext := Cat(rxSyncRegs(params.syncDepth - 2, 0), rx)
        rxSyncNext
    }

    /** Computes the next clocksPerBit value.
      *
      * @return
      *   The next clocksPerBit value.
      */
    def calculateClocksPerBitNext(
        stateReg: UartState.Type,
        clocksPerBitReg: UInt,
        clocksPerBitDbReg: UInt
    ): UInt = {
        val clocksPerBitNext = WireInit(
          0.U((log2Ceil(params.maxClocksPerBit) + 1).W)
        )

        // default for clocksPerBitNext is the current value of clocksPerBitReg
        clocksPerBitNext := clocksPerBitReg

        switch(stateReg) {
            is(UartState.Idle) {
                clocksPerBitNext := clocksPerBitDbReg
            }
        }
        clocksPerBitNext
    }

    /** Computes the next numOutputBits value.
      *
      * @return
      *   The next numOutputBits value.
      */
    def calculateNumOutputBitsNext(
        stateReg: UartState.Type,
        numOutputBitsReg: UInt,
        numOutputBitsDbReg: UInt
    ): UInt = {
        val numOutputBitsNext = WireInit(
          0.U((log2Ceil(params.maxOutputBits) + 1).W)
        )

        // default for numOutputBitsNext is the current value of numOutputBitsReg
        numOutputBitsNext := numOutputBitsReg

        switch(stateReg) {
            is(UartState.Idle) {
                numOutputBitsNext := numOutputBitsDbReg
            }
        }
        numOutputBitsNext
    }

    /** Computes the next useParity value.
      *
      * @return
      *   The next useParity value.
      */
    def calculateUseParityNext(
        stateReg: UartState.Type,
        useParityReg: Bool,
        useParityDbReg: Bool
    ): Bool = {
        val useParityNext = WireInit(false.B)

        // default for useParityNext is the current value of useParityReg
        useParityNext := useParityReg
        switch(stateReg) {
            is(UartState.Idle) {
                useParityNext := useParityDbReg
            }
        }
        useParityNext
    }

    /** Computes the next dataShift value.
      *
      * @return
      *   The next dataShift value.
      */
    def calculateDataShiftNext(
        stateReg: UartState.Type,
        rxSync: Bool,
        clockCounterReg: UInt,
        clocksPerBitReg: UInt,
        bitCounterReg: UInt,
        numOutputBitsReg: UInt,
        dataShiftReg: UInt
    ): UInt = {
        val dataShiftNext = WireInit(0.U(params.maxOutputBits.W))

        // default for dataShiftNext is the current value of dataShiftReg
        dataShiftNext := dataShiftReg

        switch(stateReg) {
            is(UartState.Data) {
                when(clockCounterReg === clocksPerBitReg) {
                    when(bitCounterReg === (numOutputBitsReg)) {
                        dataShiftNext := 0.U
                    }.otherwise {
                        dataShiftNext := Cat(
                          dataShiftReg(params.maxOutputBits - 2, 0),
                          rxSync
                        )
                    }
                }
            }
            is(UartState.Stop) {
                when(clockCounterReg === clocksPerBitReg) {
                    when(rxSync === true.B) {
                        dataShiftReg := 0.U
                    }
                }
            }
        }
        dataShiftNext
    }

    /** Computes the next data value.
      *
      * @return
      *   The next data value.
      */
    def calculateDataNext(
        stateReg: UartState.Type,
        clockCounterReg: UInt,
        clocksPerBitReg: UInt,
        bitCounterReg: UInt,
        numOutputBitsReg: UInt,
        dataShiftReg: UInt
    ): UInt = {
        val dataNext = WireInit(0.U(params.maxOutputBits.W))

        // default for dataNext is the current value of dataReg
        dataNext := dataReg
        switch(stateReg) {
            is(UartState.Data) {
                when(clockCounterReg === clocksPerBitReg) {
                    when(bitCounterReg === (numOutputBitsReg)) {
                        dataNext := dataShiftReg
                    }
                }
            }
        }
        dataNext
    }

    /** Computes the next valid value.
      *
      * @return
      *   The next valid value.
      */
    def calculateValidNext(
        stateReg: UartState.Type,
        clockCounterReg: UInt,
        clocksPerBitReg: UInt,
        bitCounterReg: UInt,
        numOutputBitsReg: UInt
    ): Bool = {
        val validNext = WireInit(false.B)
        switch(stateReg) {
            is(UartState.Data) {
                when(clockCounterReg === clocksPerBitReg) {
                    when(bitCounterReg === (numOutputBitsReg)) {
                        validNext := true.B
                    }
                }
            }
        }
        validNext
    }

    /** Computes the next error value.
      *
      * @return
      *   The next error value.
      */
    def calculateErrorNext(
        stateReg: UartState.Type,
        rxSync: Bool,
        clockCounterReg: UInt,
        clocksPerBitReg: UInt
    ): UartRxError.Type = {
        val errorNext = WireInit(UartRxError.None)
        switch(stateReg) {
            is(UartState.Start) {
                when(rxSync === true.B) {
                    errorNext := UartRxError.StartBitError
                }
            }
            is(UartState.Stop) {
                when(clockCounterReg === clocksPerBitReg) {
                    when(rxSync =/= true.B) {
                        errorNext := UartRxError.StopBitError
                    }
                }
            }
        }
        errorNext
    }
}
