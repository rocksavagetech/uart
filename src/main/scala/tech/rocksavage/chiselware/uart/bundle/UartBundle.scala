package tech.rocksavage.chiselware.uart.bundle

import chisel3._
import chisel3.util._
import tech.rocksavage.chiselware.uart.param.UartParams

/** Combined I/O bundle for both UART receiver and transmitter.
  *
  * @param params
  *   Configuration parameters for the UART.
  */
class UartBundle(params: UartParams) extends Bundle {
    // UART receiver signals
    // 'rx' is the asynchronous serial input.
    val rx = Input(Bool())
    // Data received from RX.
    val dataOut = Output(UInt(params.maxOutputBits.W))
    // Indicates that the received data is valid.
    val valid = Output(Bool())
    // Error signal from the receiver.
    val error = Output(UInt(8.W)) // Adjust width/type as needed for UartRxError

    // UART transmitter signals
    // 'load' when high initiates a new transmission.
    val load = Input(Bool())
    // Parallel data to transmit.
    val dataIn = Input(UInt(params.maxOutputBits.W))
    // Debug/configuration signals
    val clocksPerBitDb  = Input(UInt((log2Ceil(params.maxClocksPerBit) + 1).W))
    val numOutputBitsDb = Input(UInt((log2Ceil(params.maxOutputBits) + 1).W))
    val useParityDb     = Input(Bool())

    // UART serial output
    val tx = Output(Bool())
}
