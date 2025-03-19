// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart.bundle

import chisel3._
import chisel3.util._
import tech.rocksavage.chiselware.uart.param.UartParams

// from the perspective of the UART which is receiving data
class UartRxControlBundle(params: UartParams) extends Bundle {

    // configuration inputs
    val baud       = Input(UInt((log2Ceil(params.maxBaudRate) + 1).W))
    val clockFreq  = Input(UInt((log2Ceil(params.maxClockFrequency) + 1).W))
    val updateBaud = Input(Bool())
    val numOutputBitsDb  = Input(UInt((log2Ceil(params.maxOutputBits) + 1).W))
    val useParityDb      = Input(Bool())
    val parityOddDb      = Input(Bool())
    val clearErrorDb     = Input(Bool())
    val rxDataRegRead    = Input(Bool())
    val almostEmptyLevel = Input(UInt((log2Ceil(params.maxOutputBits) + 1).W))
    val almostFullLevel  = Input(UInt((log2Ceil(params.maxOutputBits) + 1).W))
    val lsbFirst        = Input(Bool())

}
