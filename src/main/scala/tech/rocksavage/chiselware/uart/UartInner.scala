package tech.rocksavage.chiselware.uart

import chisel3._
import tech.rocksavage.chiselware.uart.bundle.UartBundle
import tech.rocksavage.chiselware.uart.param.UartParams

/** A merged UART module that includes both the receiver and transmitter.
  *
  * @param params
  *   Configuration parameters for the UART.
  * @param formal
  *   A boolean to enable formal verification.
  */
class UartInner(params: UartParams, formal: Boolean = true) extends Module {
    // Use the combined bundle for I/O.
    val io = IO(new UartBundle(params))

    // Instantiate the receiver module.
    val rxModule = Module(new UartRx(params, formal))
    // Instantiate the transmitter module.
    val txModule = Module(new UartTx(params, formal))

    // -------------------------
    // Connect the receiver side
    // -------------------------
    // Connect the asynchronous serial input.
    rxModule.io.rx := io.rx
    // Hook up the output signals from the RX module.
    io.dataOut := rxModule.io.data
    io.valid   := rxModule.io.valid
    io.error   := rxModule.io.error.asUInt // Cast if necessary

    // -------------------------
    // Connect the transmitter side
    // -------------------------
    // Connect configuration and data inputs.
    txModule.io.load            := io.load
    txModule.io.data            := io.dataIn
    txModule.io.clocksPerBitDb  := io.clocksPerBitDb
    txModule.io.numOutputBitsDb := io.numOutputBitsDb
    txModule.io.useParityDb     := io.useParityDb

    // Connect the TX output.
    io.tx := txModule.io.tx
}
