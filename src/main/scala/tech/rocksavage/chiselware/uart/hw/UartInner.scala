package tech.rocksavage.chiselware.uart.hw

import chisel3._
import tech.rocksavage.chiselware.addrdecode.AddrDecodeError
import tech.rocksavage.chiselware.uart.types.bundle.UartInnerBundle
import tech.rocksavage.chiselware.uart.types.error.UartTopError
import tech.rocksavage.chiselware.uart.types.param.UartParams

/** A merged UART module that includes both the receiver and transmitter.
 *
 * @param params
 * Configuration parameters for the UART.
 * @param formal
 * A boolean to enable formal verification.
 */
class UartInner(params: UartParams, formal: Boolean = true) extends Module {
  /** Combined receiver/transmitter I/O. */
  val io = IO(new UartInnerBundle(params))

  // Instantiate the receiver module
  val rxModule = Module(new UartRx(params, formal))
  // Instantiate the transmitter module
  val txModule = Module(new UartTx(params, formal))

  // Connect the RX side
  rxModule.io.rx := io.rx
  io.dataOut := rxModule.io.data
  io.error.rxError := rxModule.io.error
  io.error.txError := txModule.io.error
  io.error.topError := UartTopError.None
  io.error.addrDecodeError := AddrDecodeError.None

  io.rxClocksPerBit := rxModule.io.clocksPerBit
  io.txClocksPerBit := txModule.io.clocksPerBit

  // Connect control signals
  rxModule.io.rxConfig <> io.rxControlBundle
  txModule.io.txConfig <> io.txControlBundle

  io.rxFifoStatus <> rxModule.io.fifoBundle
  io.txFifoStatus <> txModule.io.fifoBundle

  // Connect the TX output
  io.tx := txModule.io.tx

  when(txModule.io.txConfig.load) {
    printf(
      p"[UartInner.scala DEBUG] TX loading data=${txModule.io.txConfig.data}\n"
    )
  }
}
