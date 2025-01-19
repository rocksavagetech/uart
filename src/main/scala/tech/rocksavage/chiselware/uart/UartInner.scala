// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)
package tech.rocksavage.chiselware.uart

import chisel3._
import chisel3.util._
import tech.rocksavage.chiselware.uart.bundle.UartBundle
import tech.rocksavage.chiselware.uart.param.UartParams

class UartInner(params: UartParams) extends Module {
  val io = IO(new Bundle {
    // External UART interface
    val uart = new UartBundle(params)
    // Configuration inputs
    val clocksPerBitDb  = Input(UInt(log2Ceil(params.maxClocksPerBit).W))
    val numOutputBitsDb = Input(UInt(log2Ceil(params.maxOutputBits).W))
    val useParityDb     = Input(Bool())
    val syncDepthDb     = Input(UInt(log2Ceil(params.syncDepth).W))
  })

  // Instantiate the UART transmitter
  val uartTx = Module(new UartTx(params))
  uartTx.io.clocksPerBitDb  := io.clocksPerBitDb
  uartTx.io.numOutputBitsDb := io.numOutputBitsDb
  uartTx.io.useParityDb     := io.useParityDb
  uartTx.io.tx <> io.uart

  // Instantiate the UART receiver
  val uartRx = Module(new UartRx(params))
  uartRx.io.clocksPerBitDb  := io.clocksPerBitDb
  uartRx.io.numOutputBitsDb := io.numOutputBitsDb
  uartRx.io.useParityDb     := io.useParityDb
  uartRx.io.syncDepthDb     := io.syncDepthDb
  uartRx.io.rx <> io.uart

  // Connect the UART transmitter and receiver to the external interface
  io.uart.rxtx  := uartTx.io.tx.rxtx
  io.uart.data  := uartRx.io.rx.data
  io.uart.valid := uartRx.io.rx.valid
  io.uart.ready := uartTx.io.tx.ready
  io.uart.error := uartRx.io.rx.error
}
