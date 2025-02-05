// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)
package tech.rocksavage.chiselware.uart.bundle

import chisel3._
import chisel3.util._
import tech.rocksavage.chiselware.uart.param.UartParams

/** Bundle defining the I/O for a UART transmitter.
  *
  * @param params
  *   Configuration parameters for the UART.
  */
class UartTxBundle(params: UartParams) extends Bundle {
    // When high, initiates a new UART transmission.
    val load = Input(Bool())
    // Parallel data to be transmitted.
    val data = Input(UInt(params.maxOutputBits.W))
    // Debug/configuration signals to update the transmission settings.
    // The number of clocks per bit.
    val clocksPerBitDb = Input(UInt((log2Ceil(params.maxClocksPerBit) + 1).W))
    // Number of data bits to output.
    val numOutputBitsDb = Input(UInt((log2Ceil(params.maxOutputBits) + 1).W))
    // Indicates whether parity is used.
    val useParityDb = Input(Bool())

    // The UART serial output.
    val tx = Output(Bool())
}
