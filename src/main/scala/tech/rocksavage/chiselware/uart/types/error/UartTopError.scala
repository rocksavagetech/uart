// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart.types.error

import chisel3._

object UartTopError extends ChiselEnum {
  val None, InvalidRegisterProgramming = Value
}
