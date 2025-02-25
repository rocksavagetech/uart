package tech.rocksavage.chiselware.uart

import chisel3._
import tech.rocksavage.chiselware.uart.bundle.UartInnerBundle
import tech.rocksavage.chiselware.uart.error.UartErrorObject
import tech.rocksavage.chiselware.uart.param.UartParams

/** A merged UART module that includes both the receiver and transmitter.
  *
  * @param params
  *   Configuration parameters for the UART.
  * @param formal
  *   A boolean to enable formal verification.
  */
class UartInner(params: UartParams, formal: Boolean = true) extends Module {
    // Use the combined bundle for I/O
    val io = IO(new UartInnerBundle(params))

    // Instantiate the receiver module
    val rxModule = Module(new UartRx(params, formal))
    // Instantiate the transmitter module
    val txModule = Module(new UartTx(params, formal))

    // Connect the RX side
    rxModule.io.rx    := io.rx
    io.dataOut        := rxModule.io.data
    io.rxValid        := rxModule.io.valid
    io.error.rxError  := rxModule.io.error
    io.error.topError := UartErrorObject.None

    io.rxClocksPerBit := rxModule.io.clocksPerBit
    io.txClocksPerBit := txModule.io.clocksPerBit

    // Connect control signals
    rxModule.io.rxConfig <> io.rxControlBundle
    txModule.io.txConfig <> io.txControlBundle

    // Connect the TX output
    io.tx := txModule.io.tx

    when(txModule.io.txConfig.load) {
        printf(
          p"[UartInner.scala DEBUG] TX loading data=${txModule.io.txConfig.data}\n"
        )
    }
}
