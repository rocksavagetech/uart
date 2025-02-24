package tech.rocksavage.chiselware.uart.bundle

import chisel3._
import chisel3.util.log2Ceil
import tech.rocksavage.chiselware.uart.param.UartParams

class UartInnerBundle(params: UartParams) extends UartBundle(params) {
    val rxClocksPerBit = Output(
      UInt((log2Ceil(params.maxClockFrequency) + 1).W)
    )
    val txClocksPerBit = Output(
      UInt((log2Ceil(params.maxClockFrequency) + 1).W)
    )
}
