// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart.types.enums

import chisel3._

object UartState extends ChiselEnum {
  val Idle, BaudUpdating, Start, Data, Parity, Stop = Value
}
