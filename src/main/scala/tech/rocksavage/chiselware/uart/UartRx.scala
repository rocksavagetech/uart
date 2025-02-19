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
    val stateNext = WireInit(stateReg)

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
    val parityOddReg = RegInit(false.B)

    val clearErrorReg = RegInit(false.B)

    val clocksPerBitDbReg = RegInit(
      0.U((log2Ceil(params.maxClocksPerBit) + 1).W)
    )

    val numOutputBitsDbReg = RegInit(
      0.U((log2Ceil(params.maxOutputBits) + 1).W)
    )

    val useParityDbReg = RegInit(false.B)
    val parityOddDbReg = RegInit(false.B)

    val clearErrorDbReg = RegInit(false.B)

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
    val parityOddNext = WireInit(false.B)

    val clearErrorNext = WireInit(false.B)

    clocksPerBitReg  := clocksPerBitNext
    numOutputBitsReg := numOutputBitsNext
    useParityReg     := useParityNext
    parityOddReg     := parityOddNext
    clearErrorReg    := clearErrorNext

    clocksPerBitDbReg  := io.rxConfig.clocksPerBitDb
    numOutputBitsDbReg := io.rxConfig.numOutputBitsDb
    useParityDbReg     := io.rxConfig.useParityDb
    parityOddDbReg     := io.rxConfig.parityOddDb
    clearErrorDbReg    := io.rxConfig.clearErrorDb

    // ###################
    // RX Input Synchronization
    // ###################

    // Use 3 registers for better metastability handling
    val rxSyncRegs = RegInit(VecInit(Seq.fill(3)(true.B)))
    val rxSync     = rxSyncRegs(2) // Use last register as synchronized value

    // Synchronize input
    rxSyncRegs(0) := io.rx
    rxSyncRegs(1) := rxSyncRegs(0)
    rxSyncRegs(2) := rxSyncRegs(1)

    // Add edge detection
    val rxFallingEdge = rxSyncRegs(2) && !rxSyncRegs(1)
    val rxRisingEdge  = !rxSyncRegs(2) && rxSyncRegs(1)

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

    parityOddNext := calculateParityOddNext(
      stateReg,
      parityOddReg,
      parityOddDbReg
    )

    clearErrorNext := calculateClearErrorNext(
      stateReg,
      clearErrorReg,
      clearErrorDbReg
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
      numOutputBitsReg,
      useParityReg
    )

    errorNext := calculateErrorNext(
      stateReg,
      rxSync,
      clockCounterReg,
      clocksPerBitReg,
      errorReg,
      clearErrorReg
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
        val stateNext = WireInit(stateReg) // Default to current state
        switch(stateReg) {
            is(UartState.Idle) {
                when(rxFallingEdge) { // Use edge detection for start bit
                    stateNext := UartState.Start
                    // printf(p"[UartRx.scala DEBUG] Start bit detected\n")
                }
            }
            is(UartState.Start) {
                when(clockCounterReg === clocksPerBitReg) {
                    when(rxSync === false.B) {
                        stateNext := UartState.Data
                        // printf(p"[UartRx.scala DEBUG] Moving to Data state\n")
                    }.otherwise {
                        stateNext := UartState.Idle
                        // printf(p"[UartRx.scala DEBUG] False start bit\n")
                    }
                }
            }
            is(UartState.Data) {
                when(clockCounterReg === clocksPerBitReg) {
                    when(bitCounterReg === (numOutputBitsReg - 1.U)) {
                        when(useParityReg) {
                            stateNext := UartState.Parity
                        }.otherwise {
                            stateNext := UartState.Stop
                        }
                    }
                }
            }
            is(UartState.Parity) {
                // Wait one full bit time for the parity bit
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
        val bitCounterNext = WireInit(bitCounterReg)
        switch(stateReg) {
            is(UartState.Idle) {
                bitCounterNext := 0.U
            }
            is(UartState.Start) {
                bitCounterNext := 0.U // Reset counter at start
            }
            is(UartState.Data) {
                when(clockCounterReg === clocksPerBitReg) {
                    when(bitCounterReg < (numOutputBitsReg - 1.U)) {
                        bitCounterNext := bitCounterReg + 1.U
                    }
                }
            }
            is(UartState.Parity) {
                bitCounterNext := 0.U
            }
            is(UartState.Stop) {
                bitCounterNext := 0.U // Reset for next transaction
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

    // /** Computes the next rxSync value.
    //   *
    //   * @return
    //   *   The next rxSync value.
    //   */
    // def calculateRxSyncNext(
    //     rxSyncRegs: UInt,
    //     rx: Bool
    // ): UInt = {
    //   val rxSyncNext = WireInit(0.U(params.syncDepth.W))
    //   rxSyncNext := Cat(rxSyncRegs(params.syncDepth - 2, 0), rx)
    //   rxSyncNext
    // }

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

    /** Computes the next parityOdd value.
      *
      * @return
      *   The next parityOdd value.
      */
    def calculateParityOddNext(
        value: UartState.Type,
        bool: Bool,
        bool1: Bool
    ): Bool = {
        val parityOddNext = WireInit(false.B)
        parityOddNext := bool
        switch(value) {
            is(UartState.Idle) {
                parityOddNext := bool1
            }
        }
        parityOddNext
    }

    def calculateClearErrorNext(
        stateReg: UartState.Type,
        clearErrorReg: Bool,
        clearErrorDbReg: Bool
    ): Bool = {
        val clearErrorNext = WireInit(false.B)
        clearErrorNext := clearErrorReg
        switch(stateReg) {
            is(UartState.Idle) {
                clearErrorNext := clearErrorDbReg
            }
        }
        clearErrorNext
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
        val dataShiftNext = WireInit(dataShiftReg)
        switch(stateReg) {
            is(UartState.Idle) {
                dataShiftNext := 0.U
                // printf(p"[UartRx.scala DEBUG] Idle, rxSync=${rxSync}\n")
            }
            is(UartState.Data) {
                // Sample exactly in the middle of the bit
                when(clockCounterReg === (clocksPerBitReg >> 1)) {
                    // printf(p"[UartRx.scala DEBUG] Sampling bit=${rxSync} at pos=${bitCounterReg}\n")
                    // Shift in LSB first
                    dataShiftNext := Cat(
                      rxSync,
                      dataShiftReg(params.maxOutputBits - 1, 1)
                    )
                }
            }
            is(UartState.Parity) {
                // Do not shift data here, we have a single parity bit
                dataShiftNext := dataShiftReg
            }
            is(UartState.Stop) {
                // printf(p"[UartRx.scala DEBUG] Stop bit, rxSync=${rxSync}\n")
                dataShiftNext := dataShiftReg
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
        val dataNext = WireInit(dataReg)
        switch(stateReg) {
            is(UartState.Data) {
                when(
                  clockCounterReg === clocksPerBitReg &&
                      bitCounterReg === (numOutputBitsReg - 1.U)
                ) {
                    // Only update at end of last bit
                    dataNext := dataShiftReg
                    printf(
                      p"[UartRx.scala DEBUG] Captured data=${dataShiftReg}\n"
                    )
                }
            }
            is(UartState.Stop) {
                // Hold the data through stop bit
                dataNext := dataReg
            }
            is(UartState.Parity) {
                // Keep data stable in Parity
                dataNext := dataReg
            }
            is(UartState.Idle) {
                when(validReg) {
                    // Only clear after valid has been asserted
                    dataNext := 0.U
                }.otherwise {
                    dataNext := dataReg
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
        numOutputBitsReg: UInt,
        useParityReg: Bool
    ): Bool = {
        val validNext = WireInit(false.B)
        switch(stateReg) {
            is(UartState.Stop) {
                when(clockCounterReg === clocksPerBitReg) {
                    when(rxSync) { // Verify stop bit is high
                        validNext := true.B
                        printf(p"[UartRx.scala DEBUG] Valid data captured\n")
                    }
                }
            }
            is(UartState.Idle) {
                validNext := false.B
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
        clocksPerBitReg: UInt,
        errorReg: UartRxError.Type,
        clearError: Bool
    ): UartRxError.Type = {

        val errorNext = WireDefault(errorReg)

        // -----------------------
        //  Detect NEW errors
        // -----------------------
        when(clearError) {
            errorNext := UartRxError.None
        }
        switch(stateReg) {
            is(UartState.Start) {
                when(clockCounterReg === clocksPerBitReg && rxSync === true.B) {
                    printf("[UartRx.scala DEBUG] StartBitError detected!\n")
                    errorNext := UartRxError.StartBitError
                }
            }
            is(UartState.Parity) {
                when(clockCounterReg === clocksPerBitReg) {
                    val dataOnes = PopCount(dataShiftReg) // # of 1 bits
                    val dataParity = dataOnes(0) // 1 => odd # of ones
                    val expected   = Mux(parityOddReg, !dataParity, dataParity)
                    when(rxSync =/= expected) {
                        printf(
                          p"[UartRx.scala DEBUG] ParityError mismatch bit=$rxSync, expected=$expected\n"
                        )
                        errorNext := UartRxError.ParityError
                    }
                }
            }
            is(UartState.Stop) {
                when(clockCounterReg === clocksPerBitReg && rxSync =/= true.B) {
                    printf(
                      p"[UartRx.scala DEBUG] StopBitError detected! rxSync=$rxSync\n"
                    )
                    errorNext := UartRxError.StopBitError
                }
            }
        }
        errorNext
    }

}
