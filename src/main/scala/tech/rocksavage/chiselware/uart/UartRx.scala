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
    // RX Input Synchronization
    // ###################

    /** Shift register for synchronizing received data */
    val rxSyncRegs = RegInit(VecInit(Seq.fill(params.syncDepth)(true.B)).asUInt)

    /** Next shift register for synchronizing received data */
    val rxSyncNext = WireInit(1.U(params.syncDepth.W))

    rxSyncRegs := rxSyncNext

    /** Current synchronized rx input */
    val rxSync = Wire(Bool())
    rxSync := rxSyncRegs(params.syncDepth - 1)

    // ###################
    // Input Control State Registers
    // ###################

    val baudReg      = RegInit(0.U((log2Ceil(params.maxBaudRate) + 1).W))
    val clockFreqReg = RegInit(0.U((log2Ceil(params.maxClockFrequency) + 1).W))

    /** Clocks per bit register */
    val clocksPerBitReg = RegInit(
      0.U((log2Ceil(params.maxClockFrequency) + 1).W)
    )

    /** Number of output bits register */
    val numOutputBitsReg = RegInit(0.U((log2Ceil(params.maxOutputBits) + 1).W))

    /** Parity usage register */
    val useParityReg  = RegInit(false.B)
    val parityOddReg  = RegInit(false.B)
    val clearErrorReg = RegInit(false.B)

    val numOutputBitsDbReg = RegInit(
      0.U((log2Ceil(params.maxOutputBits) + 1).W)
    )
    val useParityDbReg  = RegInit(false.B)
    val parityOddDbReg  = RegInit(false.B)
    val clearErrorDbReg = RegInit(false.B)

    /** Next Number of output bits register */
    val numOutputBitsNext = WireInit(
      0.U((log2Ceil(params.maxOutputBits) + 1).W)
    )

    /** Next Parity usage register */
    val useParityNext = WireInit(false.B)
    val parityOddNext = WireInit(false.B)

    val clearErrorNext = WireInit(false.B)

    numOutputBitsReg := numOutputBitsNext
    useParityReg     := useParityNext
    parityOddReg     := parityOddNext
    clearErrorReg    := clearErrorNext

    numOutputBitsDbReg := io.rxConfig.numOutputBitsDb
    useParityDbReg     := io.rxConfig.useParityDb
    parityOddDbReg     := io.rxConfig.parityOddDb
    clearErrorDbReg    := io.rxConfig.clearErrorDb

    val updateBaudDb = RegInit(false.B)
    when(io.rxConfig.updateBaud) {
        updateBaudDb := true.B
    }

    // ###################
    // State FSM
    // ###################

    val uartFsm = Module(new UartFsm(params))

    val state = uartFsm.io.state
    val sampleStartWire =
        uartFsm.io.sample && uartFsm.io.state === UartState.Start
    val sampleDataWire =
        uartFsm.io.sample && uartFsm.io.state === UartState.Data
    val sampleParityWire =
        uartFsm.io.sample && uartFsm.io.state === UartState.Parity
    val completeWire = uartFsm.io.complete

    val startTransaction =
        (rxSync === false.B) && (RegNext(state) === UartState.Idle)

    uartFsm.io.startTransaction := startTransaction
    uartFsm.io.clocksPerBit     := clocksPerBitReg
    uartFsm.io.numOutputBits    := numOutputBitsReg
    uartFsm.io.useParity        := useParityReg
    uartFsm.io.updateBaud       := io.rxConfig.updateBaud

    // ###################
    // Baud Rate Calculation
    // ###################

    when(updateBaudDb) {
        baudReg      := io.rxConfig.baud
        clockFreqReg := io.rxConfig.clockFreq
    }

    val baudGen = Module(new UartBaudRateGenerator(params))
    baudGen.io.clkFreq     := io.rxConfig.clockFreq
    baudGen.io.desiredBaud := io.rxConfig.baud
    baudGen.io.update      := RegNext(state) === UartState.BaudUpdating

    // The effective clocks-per-bit for the UART will come from the baud generator.
    val effectiveClocksPerBit = baudGen.io.clocksPerBit

    // Once the baud rate is valid, the state can go back to idle.
    uartFsm.io.baudValid := baudGen.io.valid

    clocksPerBitReg := effectiveClocksPerBit

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

    val sampledRxSync = RegInit(true.B)

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

    io.clocksPerBit := clocksPerBitReg

    // ###################
    // Finite State Machine (FSM)
    // ###################

    numOutputBitsNext := Mux(
      state === UartState.Idle || state === UartState.BaudUpdating,
      numOutputBitsDbReg,
      numOutputBitsReg
    )

    useParityNext := Mux(
      state === UartState.Idle || state === UartState.BaudUpdating,
      useParityDbReg,
      useParityReg
    )

    parityOddNext := Mux(
      state === UartState.Idle || state === UartState.BaudUpdating,
      parityOddDbReg,
      parityOddReg
    )

    clearErrorNext := Mux(
      state === UartState.Idle || state === UartState.BaudUpdating,
      clearErrorDbReg,
      clearErrorReg
    )

    rxSyncNext := calculateRxSyncNext(
      rxSyncRegs,
      io.rx
    )

    when(sampleStartWire) {
        sampledRxSync := rxSync
    }

    dataShiftNext := calculateDataShiftNext(
      rxSync,
      dataShiftReg,
      sampleDataWire,
      completeWire
    )

    dataNext := calculateDataNext(
      dataShiftReg,
      dataReg,
      completeWire
    )

    validNext := calculateValidNext(
      validReg,
      completeWire,
      startTransaction
    )

    errorNext := calculateErrorNext(
      stateReg = state,
      rxSync = rxSync,
      useParityReg = useParityReg,
      parityOddReg = parityOddReg,
      dataShiftReg = dataShiftReg,
      errorReg = errorReg,
      clearError = clearErrorReg,
      completeWire = completeWire,
      sampleParityWire = sampleParityWire
    )

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

    /** Computes the next dataShift value.
      *
      * @return
      *   The next dataShift value.
      */
    def calculateDataShiftNext(
        rxSync: Bool,
        dataShiftReg: UInt,
        sampleWire: Bool,
        completeWire: Bool
    ): UInt = {
        val dataShiftNext = WireDefault(dataShiftReg)

        when(sampleWire) {
            dataShiftNext := Cat(
              dataShiftReg(params.maxOutputBits - 2, 0),
              rxSync
            )
        }
        when(completeWire) {
            dataShiftNext := 0.U
        }
        dataShiftNext
    }

    /** Computes the next data value.
      *
      * @return
      *   The next data value.
      */
    def calculateDataNext(
        dataShiftReg: UInt,
        dataReg: UInt,
        completeWire: Bool
    ): UInt = {
        val dataNext = WireDefault(dataReg)
        when(completeWire) {
            dataNext := dataShiftReg
        }
        dataNext
    }

    /** Computes the next valid value.
      *
      * @return
      *   The next valid value.
      */
    def calculateValidNext(
        validReg: Bool,
        completeWire: Bool,
        startTransaction: Bool
    ): Bool = {
        val validNext = WireDefault(validReg)
        when(completeWire) {
            validNext := true.B
        }
        when(startTransaction) {
            validNext := false.B
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
        useParityReg: Bool,
        parityOddReg: Bool,
        dataShiftReg: UInt,
        errorReg: UartRxError.Type,
        clearError: Bool,
        completeWire: Bool,
        sampleParityWire: Bool
    ): UartRxError.Type = {
        val errorNext = WireDefault(errorReg)
        // -----------------------
        //  Detect NEW errors
        // -----------------------

        val rxSyncDelay = RegNext(rxSync)

        when(clearError) {
            errorNext := UartRxError.None
        }

        switch(stateReg) {
            is(UartState.Stop) {
                when(completeWire) {
                    when(rxSync =/= true.B) {
                        errorNext := UartRxError.StopBitError
                    }
                }
            }
            is(UartState.Parity) {
                when(sampleParityWire) {
                    val expectedParity =
                        UartParity.parityChisel(dataShiftReg, parityOddReg)
                    when(useParityReg === true.B) {
                        when(rxSyncDelay =/= expectedParity) {
                            errorNext := UartRxError.ParityError
                        }
                    }
                }
            }
        }
        errorNext
    }
}
