// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)
package tech.rocksavage.chiselware.uart

import chisel3._
import chisel3.util._
import tech.rocksavage.chiselware.dynamicfifo.{DynamicFifo, DynamicFifoParams}
import tech.rocksavage.chiselware.uart.bundle.UartTxBundle
import tech.rocksavage.chiselware.uart.error.UartTxError
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
    val clocksPerBitReg = RegInit(
      0.U((log2Ceil(params.maxClockFrequency) + 1).W)
    )
    val numOutputBitsReg = RegInit(0.U((log2Ceil(params.maxOutputBits) + 1).W))
    val useParityReg     = RegInit(false.B)
    val parityOddReg     = RegInit(false.B)

    val numOutputBitsDbReg = RegInit(
      0.U((log2Ceil(params.maxOutputBits) + 1).W)
    )
    val useParityDbReg = RegInit(false.B)
    val parityOddDbReg = RegInit(false.B) // New parity configuration register

    val numOutputBitsNext = WireInit(
      0.U((log2Ceil(params.maxOutputBits) + 1).W)
    )
    val useParityNext = WireInit(false.B)
    val parityOddNext = WireInit(false.B) // New NEXT signal for parity odd
    val dataNext      = WireInit(0.U(params.maxOutputBits.W))
    val loadNext      = WireInit(false.B)
    
    val clearErrorReg = RegInit(false.B)
    val clearErrorDbReg = RegInit(false.B)
    val clearErrorNext = WireInit(false.B)
    clearErrorReg    := clearErrorNext
    clearErrorDbReg    := io.txConfig.clearErrorDb

    val fifoEmptyReg = RegInit(true.B)

    numOutputBitsReg := numOutputBitsNext
    useParityReg     := useParityNext
    parityOddReg     := parityOddNext // Latch new parity configuration

    numOutputBitsDbReg := io.txConfig.numOutputBitsDb
    useParityDbReg     := io.txConfig.useParityDb
    parityOddDbReg     := io.txConfig.parityOddDb // load configuration

    // ###################
    // State FSM
    // ###################

    val uartFsm = Module(new UartFsm(params))

    val state            = uartFsm.io.state
    val sampleStartWire  = uartFsm.io.sample && state === UartState.Start
    val sampleDataWire   = uartFsm.io.sample && state === UartState.Data
    val sampleParityWire = uartFsm.io.sample && state === UartState.Parity
    val completeWire     = uartFsm.io.complete
    val applyShiftReg    = sampleDataWire || sampleParityWire
    val txErrorReg = RegInit(UartTxError.None)

    val active = RegInit(false.B)
    when((RegNext(state) === UartState.Idle) && (loadNext)) {
        active := true.B
    }.elsewhen(fifoEmptyReg) {
        active := false.B
    }
    val startTransaction =
        (RegNext(state) === UartState.Idle) && (loadNext || active)

    uartFsm.io.startTransaction := startTransaction
    uartFsm.io.clocksPerBit     := clocksPerBitReg
    uartFsm.io.numOutputBits    := numOutputBitsReg
    uartFsm.io.useParity        := useParityReg
    uartFsm.io.updateBaud       := io.txConfig.updateBaud

    // ###################
    // Baud Rate Calculation
    // ###################

    val baudGen = Module(new UartBaudRateGenerator(params))
    baudGen.io.clkFreq     := io.txConfig.clockFreq
    baudGen.io.desiredBaud := io.txConfig.baud
    baudGen.io.update      := RegNext(state) === UartState.BaudUpdating

    // The effective clocks-per-bit for the UART will come from the baud generator.
    val effectiveClocksPerBit = baudGen.io.clocksPerBit

    // Once the baud rate is valid, the state can go back to idle.
    uartFsm.io.baudValid := baudGen.io.valid

    clocksPerBitReg := effectiveClocksPerBit

    // Latch incoming data and capture txData for parity calculation.
    // (txDataReg holds the unshifted data for parity computation.)
    val txData = WireInit(0.U(params.maxOutputBits.W))
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
    // Fifo for reads
    // ###################
    val fifoParams = DynamicFifoParams(
      dataWidth = params.maxOutputBits,
      fifoDepth = params.bufferSize,
      coverage = false,
      formal = formal
    )
    val fifo = Module(new DynamicFifo(fifoParams))

    when(io.txConfig.load) {
        // On any cycle we assert push+full => overflow
        when (fifo.io.full) {
            txErrorReg := UartTxError.FifoOverflow
            printf("[UartTx DEBUG] Setting overflow error - FIFO full\n")
        }.elsewhen (fifo.io.empty) {
            // If your design tries to pop when empty => underflow
            txErrorReg := UartTxError.FifoUnderflow
            printf("[UartTx DEBUG] Setting underflow error - FIFO empty\n")
        }.elsewhen(uartFsm.io.complete){
            txErrorReg := UartTxError.None
            printf("[UartTx DEBUG] Clearing error after completion\n")
        }
    }

    // Debug status changes
    when(txErrorReg =/= RegNext(txErrorReg)) {
        printf("[UartTx DEBUG] Error state changed: from %d to %d\n", 
            RegNext(txErrorReg).asUInt, txErrorReg.asUInt)
    }

    when(clearErrorDbReg) {
        // When clearErrorDb is set, reset
        txErrorReg := UartTxError.None
    }
    //Error Output
    io.error := txErrorReg
  
    fifo.io.push   := RegNext(io.txConfig.txDataRegWrite)
    fifo.io.pop    := startTransaction
    fifo.io.dataIn := io.txConfig.data

    fifo.io.almostFullLevel  := 0.U
    fifo.io.almostEmptyLevel := 0.U
    fifoEmptyReg             := fifo.io.empty

    txData := fifo.io.dataOut

    // ###################
    // Output Register
    // ###################
    // The TX output should be high in idle.
    val txNext = WireInit(true.B)
    // The calculateTxOut now also has a branch for the Parity state.
    txNext := calculateTxOut(state, dataShiftReg, txData, parityOddReg)
    io.tx  := txNext

    io.clocksPerBit := clocksPerBitReg

    // ###################
    // Finite State Machine (FSM)
    // ###################
    // Updated state transitions to include parity mode.

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

    dataShiftNext := calculateDataShiftNext(
      dataShiftReg,
      fifo.io.dataOut,
      startTransaction,
      applyShiftReg,
      numOutputBitsReg
    )

    // -------------------------
    // FSM and Data Handling Functions
    // -------------------------

    def calculateDataShiftNext(
        dataShiftReg: UInt,
        loadData: UInt,
        startTransaction: Bool,
        applyShiftReg: Bool,
        numOutputBits: UInt
    ): UInt = {
        val dataShiftNext = WireDefault(dataShiftReg)

        when(startTransaction) {
            dataShiftNext := reverse(loadData, numOutputBits)
        }
        when(applyShiftReg) {
            dataShiftNext := Cat(0.U(1.W), dataShiftReg >> 1)
        }
        dataShiftNext
    }

    def reverse(data: UInt, width: UInt): UInt = {
        // Assume the maximum width is that of data.
        val maxWidth = data.getWidth

        // Precompute the reversed value for each possible width (from 0 up to maxWidth).
        val lookup: Seq[UInt] = Seq.tabulate(maxWidth + 1) { w =>
            if (w == 0) {
                0.U(maxWidth.W)
            } else {
                // Extract the lower w bits, reverse them and then pad with zeros.
                val reversed =
                    VecInit((0 until w).map(i => data(i)).reverse).asUInt
                Cat(0.U((maxWidth - w).W), reversed)
            }
        }

        // Build mapping for each width value
        val mapping: Seq[(UInt, UInt)] = lookup.zipWithIndex.map {
            case (rev, idx) =>
                (idx.U, rev)
        }

        // Use the curried form of MuxLookup - notice the second parameter list for mapping
        MuxLookup[UInt](width, 0.U(maxWidth.W))(mapping)
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
            is(UartState.BaudUpdating) {
                txOut := true.B
            }
            is(UartState.Start) {
                txOut := false.B
            }
            is(UartState.Data) {
                txOut := dataShiftReg(0)
            }
            is(UartState.Parity) {
                txOut := UartParity.parityChisel(txData, parityOddReg)
            }
            is(UartState.Stop) {
                txOut := true.B
            }
        }
        txOut
    }
}
