// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart.types.bundle

import chisel3._
import chisel3.util.log2Ceil
import tech.rocksavage.chiselware.uart.types.error.UartRxError
import tech.rocksavage.chiselware.uart.types.param.UartParams

// from the perspective of the UART which is receiving data
class UartRxBundle(params: UartParams) extends Bundle {
    val rx    = Input(Bool())
    val data  = Output(UInt(params.maxOutputBits.W))
    val error = Output(UartRxError())

    // configuration inputs
    val rxConfig     = new UartRxControlBundle(params)
    val clocksPerBit = Output(UInt((log2Ceil(params.maxClockFrequency) + 1).W))

    val fifoBundle = new FifoStatusBundle(params)

}
