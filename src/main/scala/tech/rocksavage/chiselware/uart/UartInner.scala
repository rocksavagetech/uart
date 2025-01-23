package tech.rocksavage.chiselware.uart

import chisel3._
import tech.rocksavage.chiselware.uart.bundle.{UartRxBundle, UartTxBundle}
import tech.rocksavage.chiselware.uart.param.UartParams

class UartInner(params: UartParams, formal: Boolean = true) extends Module {
  val io = IO(new Bundle {
    val rx = new UartRxBundle(params)
    val tx = new UartTxBundle(params)
  })

  // Instantiate the UART transmitter
  val uartTx = Module(new UartTx(params, formal))
  io.tx <> uartTx.io

  // Instantiate the UART receiver
  val uartRx = Module(new UartRx(params, formal))
  io.rx <> uartRx.io
}
