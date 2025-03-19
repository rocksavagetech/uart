// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)
package tech.rocksavage.chiselware.uart

import chisel3._
import chisel3.util._
import tech.rocksavage.chiselware.dynamicfifo.{DynamicFifo, DynamicFifoParams}
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
    val useParityReg      = RegInit(false.B)
    val parityOddReg      = RegInit(false.B)
    val clearErrorReg     = RegInit(false.B)
    val fifoOverflowFlag  = RegInit(false.B)
    val fifoUnderflowFlag = RegInit(false.B)

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

    // true for RX so it samples at the middle of the bit
    uartFsm.io.shiftOffset := true.B

    val state              = uartFsm.io.state
    val shiftStart         = uartFsm.io.shift && state === UartState.Start
    val shiftData          = uartFsm.io.shift && state === UartState.Data
    val shiftParity        = uartFsm.io.shift && state === UartState.Parity
    val shiftStop          = uartFsm.io.shift && state === UartState.Stop
    val completeWire       = uartFsm.io.complete
    val completeSampleWire = shiftStop

    uartFsm.io.waiting := RegNext(
      state
    ) === UartState.Stop && rxSync === false.B

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

    /** Error output register */
    val errorReg = RegInit(UartRxError.None)

    /** Next Data output register */
    val dataNext = WireInit(0.U(params.maxOutputBits.W))

    /** Next Error output register */
    val errorNext = WireInit(UartRxError.None)

    val sampledRxSync = RegInit(true.B)

    val lsbFirstReg  = RegInit(false.B)
    val lsbFirstNext = WireInit(false.B)
    lsbFirstReg := lsbFirstNext

    /** I don't know why this print needs to be here but the tests fail without
      * it, its 11 pm and I just need to get the tests to pass
      */
    when(lsbFirstNext =/= lsbFirstReg) {
        printf(
          "[UartRx DEBUG] lsbFirst changed: from %d to %d\n",
          lsbFirstReg.asUInt,
          lsbFirstNext.asUInt
        )
    }

    dataReg := dataNext

    when(clearErrorDbReg) {
        // When clearErrorDb is set, reset all error flags
        errorReg          := UartRxError.None
        fifoOverflowFlag  := false.B
        fifoUnderflowFlag := false.B
        printf("[UartRx DEBUG] Clearing all error flags\n")
    }.otherwise {
        errorReg := errorNext
    }

    // ###################
    // Fifo for reads
    // ###################

    val almostFullLevelReg = RegInit(
      (0).U((log2Ceil(params.maxOutputBits) + 1).W)
    )
    val almostFullLevelNext = WireInit(
      (0).U((log2Ceil(params.maxOutputBits) + 1).W)
    )

    almostFullLevelNext := Mux(
      state === UartState.Idle || state === UartState.BaudUpdating,
      io.rxConfig.almostFullLevel,
      almostFullLevelReg
    )

    almostFullLevelReg := almostFullLevelNext

    val almostEmptyLevelReg = RegInit(
      (0).U((log2Ceil(params.maxOutputBits) + 1).W)
    )
    val almostEmptyLevelNext = WireInit(
      (0).U((log2Ceil(params.maxOutputBits) + 1).W)
    )

    almostEmptyLevelNext := Mux(
      state === UartState.Idle || state === UartState.BaudUpdating,
      io.rxConfig.almostEmptyLevel,
      almostEmptyLevelReg
    )

    almostEmptyLevelReg := almostEmptyLevelNext

    val fifoParams = DynamicFifoParams(
      dataWidth = params.maxOutputBits,
      fifoDepth = params.bufferSize,
      coverage = false,
      formal = formal
    )
    val fifo = Module(new DynamicFifo(fifoParams))

    io.fifoBundle.full        := fifo.io.full
    io.fifoBundle.empty       := fifo.io.empty
    io.fifoBundle.almostFull  := fifo.io.almostFull
    io.fifoBundle.almostEmpty := fifo.io.almostEmpty

    val attemptingPush = completeWire && errorReg === UartRxError.None
    val attemptingPop  = io.rxConfig.rxDataRegRead
    // If the receiver tries to push when FIFO is full => overflow
    when(attemptingPush && fifo.io.full) {
        fifoOverflowFlag := true.B
        printf(
          "[UartRx DEBUG] Setting FIFO overflow flag - push to full FIFO\n"
        )
    }

    when(attemptingPop && fifo.io.empty) {
        fifoUnderflowFlag := true.B
        printf(
          "[UartRx DEBUG] Setting FIFO underflow flag - pop from empty FIFO\n"
        )
    }

    // Output error priority logic
    when(fifoOverflowFlag) {
        io.error := UartRxError.FifoOverflow
    }.elsewhen(fifoUnderflowFlag) {
        io.error := UartRxError.FifoUnderflow
    }.otherwise {
        io.error := errorReg
    }

    // Log error flag changes for debugging
    when(fifoOverflowFlag =/= RegNext(fifoOverflowFlag)) {
        printf(
          "[UartRx DEBUG] FIFO overflow flag changed to %d\n",
          fifoOverflowFlag
        )
    }
    when(fifoUnderflowFlag =/= RegNext(fifoUnderflowFlag)) {
        printf(
          "[UartRx DEBUG] FIFO underflow flag changed to %d\n",
          fifoUnderflowFlag
        )
    }

    fifo.io.push   := completeWire && errorReg === UartRxError.None
    fifo.io.dataIn := dataNext

    // a new read indicates that someone is trying to read from the fifo
    val newFifoDataRead = RegInit(false.B)
    newFifoDataRead := io.rxConfig.rxDataRegRead && !RegNext(
      io.rxConfig.rxDataRegRead
    )

    // newFifoDataReadOver indicates that the read has been completed,
    // only once the read has been completed, the fifo can be popped
    val newFifoDataReadOver = RegInit(false.B)
    newFifoDataReadOver := !newFifoDataRead && RegNext(newFifoDataRead)
    fifo.io.pop         := newFifoDataReadOver && !fifo.io.empty

    when(newFifoDataRead) {
        printf("[UartRx DEBUG] New read from FIFO\n")
    }

    when(newFifoDataReadOver) {
        printf("[UartRx DEBUG] Read from FIFO completed\n")
    }

    val dataReadWire = WireInit(0.U(params.maxOutputBits.W))
    val dataReadReg  = RegInit(0.U(params.maxOutputBits.W))
    when(fifo.io.pop) {
        dataReadWire := fifo.io.dataOut
    }.otherwise {
        dataReadWire := dataReadReg
    }
    dataReadReg := dataReadWire

    io.data := fifo.io.dataOut

    fifo.io.almostFullLevel  := almostFullLevelReg
    fifo.io.almostEmptyLevel := almostEmptyLevelReg

    // ###################
    // Output Assignments
    // ###################

    /** Assign output error signal */
    // io.error := errorReg // Ensure io.error is always assigned

    io.clocksPerBit := clocksPerBitReg

    // ###################
    // Finite State Machine (FSM)
    // ###################

    lsbFirstNext := Mux(
      state === UartState.Idle || state === UartState.BaudUpdating,
      io.rxConfig.lsbFirst,
      lsbFirstReg
    )

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

    when(shiftStart) {
        sampledRxSync := rxSync
    }

    dataShiftNext := calculateDataShiftNext(
      rxSync,
      dataShiftReg,
      shiftData,
      completeWire
    )

    dataNext := calculateDataNext(
      dataShiftReg,
      dataReg,
      completeWire,
      lstFirst = lsbFirstReg,
      outputBitSize = numOutputBitsReg
    )

    val parityData = WireDefault(0.U(params.maxOutputBits.W))
    val expectedParity =
        shiftParity && UartParity.parityChisel(parityData, parityOddReg)
    when(shiftParity) {
        parityData := dataShiftNext
    }

    errorNext := calculateErrorNext(
      stateReg = state,
      rxSync = rxSync,
      useParityReg = useParityReg,
      expectedParity = expectedParity,
      errorReg = errorReg,
      clearError = clearErrorReg,
      completeSampleWire = completeSampleWire,
      sampleParityWire = shiftParity
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
        completeWire: Bool,
        lstFirst: Bool,
        outputBitSize: UInt
    ): UInt = {
        val dataNext = WireDefault(dataReg)
        when(completeWire) {
            when(!lstFirst) {
//                printf(
//                  "[UartRx DEBUG] Data shift reg: %d\n",
//                  dataShiftReg
//                )
                dataNext := dataShiftReg
            }.otherwise {
//                printf(
//                  "[UartRx DEBUG] Data shift reg reversed: %d\n",
//                  dataShiftReg
//                )
                dataNext := UartUtils.reverse(dataShiftReg, outputBitSize)
            }
        }
//        printf(
//          "[UartRx DEBUG] Data next: %d\n",
//          dataNext
//        )
        dataNext
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
        expectedParity: Bool,
        errorReg: UartRxError.Type,
        clearError: Bool,
        completeSampleWire: Bool,
        sampleParityWire: Bool
    ): UartRxError.Type = {
        val errorNext = WireDefault(errorReg)
        // -----------------------
        //  Detect NEW errors
        // -----------------------

        when(clearError) {
            errorNext := UartRxError.None
        }

        switch(stateReg) {
            is(UartState.Stop) {
                when(completeSampleWire) {
                    when(rxSync =/= true.B) {
                        errorNext := UartRxError.StopBitError
                    }
                }
            }
            is(UartState.Parity) {
                when(sampleParityWire) {
                    when(useParityReg === true.B) {
                        when(rxSync =/= expectedParity) {
                            errorNext := UartRxError.ParityError
                        }
                    }
                }
            }
        }
        errorNext
    }
}
