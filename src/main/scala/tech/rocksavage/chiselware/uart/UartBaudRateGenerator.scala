package tech.rocksavage.chiselware.uart

import chisel3._
import tech.rocksavage.chiselware.uart.param.UartParams // reuse our multi‐cycle divider

// Define a simple state machine for the baud generator.
object BaudGenState extends ChiselEnum {
    val Idle, Updating = Value
}

class UartBaudRateGenerator(p: UartParams) extends Module {
    val io = IO(new Bundle {
        // desired baud rate (in Hz) is programmed via APB registers—
        // for example, 115200.
        val desiredBaud = Input(UInt(32.W))
        // When asserted (from a double‐buffered “updateBaud” register),
        // start the new calculation.
        val update = Input(Bool())
        // The system clock frequency in Hz. (For example, 25_000_000)
        val clkFreq = Input(UInt(32.W))
        // Computed clocks per uart bit: (clkFreq ÷ desiredBaud)
        val clocksPerBit = Output(UInt(32.W))
        // Valid goes high when the divider has finished computation.
        val valid = Output(Bool())
    })

    // Create a divider module (it uses an iterative multi‐cycle algorithm)
    val divider = Module(new Divider())
    // Note: The divider expects numerator and denominator.
    // Here we calculate: clocksPerBit = clkFreq / desiredBaud

    // State register for the baud generator state machine.
    val state = RegInit(BaudGenState.Idle)
    // Register to hold the updated clocks-per-bit value.
    val updatedClocksPerBit = RegInit(0.U(32.W))

    // Default outputs.

    io.clocksPerBit := updatedClocksPerBit
    io.valid        := divider.io.valid

    val numeratorReg   = RegInit(0.U(32.W))
    val denominatorReg = RegInit(0.U(32.W))

    val muxedNum = Mux(state === BaudGenState.Idle, io.clkFreq, numeratorReg)
    val muxedDen =
        Mux(state === BaudGenState.Idle, io.desiredBaud, denominatorReg)

    // By default, drive the divider off.
    divider.io.start       := false.B
    divider.io.numerator   := muxedNum
    divider.io.denominator := muxedDen

    when(state === BaudGenState.Idle) {
        when(io.update) {
            // When a new baud is programmed, start the divider.
            numeratorReg   := io.clkFreq
            denominatorReg := io.desiredBaud

            divider.io.start := true.B
            state            := BaudGenState.Updating
        }
    }.otherwise { // state === Updating
        // Keep divider.io.start low.
        divider.io.start := false.B
        when(divider.io.valid) {
            updatedClocksPerBit := divider.io.result
            // Once computation is finished, return to Idle.
            state          := BaudGenState.Idle
            numeratorReg   := 0.U
            denominatorReg := 0.U
        }
    }
}
