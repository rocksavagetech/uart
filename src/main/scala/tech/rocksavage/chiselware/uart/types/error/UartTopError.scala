// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart.types.error

import chisel3._

/** Top‐level UART error codes (configuration and decode).
 *
 *  - None                        : No top‐level error.
 *  - InvalidRegisterProgramming : An attempt was made to program a register
 *    with an out‐of‐range or unsupported value.
 */
object UartTopError extends ChiselEnum {
  val None, InvalidRegisterProgramming = Value
}
