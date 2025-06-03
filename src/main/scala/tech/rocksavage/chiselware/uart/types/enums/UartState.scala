// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart.types.enums

import chisel3._

/** Enumeration of the finite‐state machine states for the UART controller.
 *
 * These states govern the bit‐level framing and timing of UART
 * transmissions and receptions:
 *
 *  - Idle: No active transaction; line remains high.
 *  - BaudUpdating: Updating the baud generator before starting.
 *  - Start: Transmitting or sampling the start bit (logic low).
 *  - Data: Transmitting or sampling data bits.
 *  - Parity: Transmitting or sampling the parity bit.
 *  - Stop: Transmitting or sampling the stop bit (logic high).
 */
object UartState extends ChiselEnum {
  /** No transaction in progress; output remains idle (high). */
  val Idle: Type = Value

  /** Updating the baud rate generator; line remains idle until valid. */
  val BaudUpdating: Type = Value

  /** Start bit period: asserts or samples logic low. */
  val Start: Type = Value

  /** Data bit period: shifts or samples each data bit in turn. */
  val Data: Type = Value

  /** Parity bit period: computes or checks parity over the data bits. */
  val Parity: Type = Value

  /** Stop bit period: asserts or samples logic high to end the frame. */
  val Stop: Type = Value
}
