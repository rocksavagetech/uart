// (c) 2024 Rocksavage Technology, Inc.
// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.timer.bundle

import chisel3._
import tech.rocksavage.chiselware.timer.param.TimerParams

/**
 * A bundle representing the input and output signals for a timer module.
 *
 * @param params The configuration parameters for the timer, including the width of the count registers.
 */
class TimerBundle(params: TimerParams) extends Bundle {

  /** Input bundle for the timer */
  val timerInputBundle = new TimerInputBundle(params)

  /** Output bundle for the timer */
  val timerOutputBundle = new TimerOutputBundle(params)
}