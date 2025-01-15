// (c) 2024 Rocksavage Technology, Inc.
// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.timer.bundle

import chisel3._
import tech.rocksavage.chiselware.timer.param.TimerParams

/**
 * A bundle representing the output signals for a timer module.
 *
 * @param params The configuration parameters for the timer, including the width of the count registers.
 */
class TimerOutputBundle(params: TimerParams) extends Bundle {

  /** Current count value of the timer. */
  val count = Output(UInt(params.countWidth.W))

  /** Signal indicating that the timer has reached its maximum count value. */
  val maxReached = Output(Bool())

  /** PWM output signal with a duty cycle controlled by `pwmCeiling`. */
  val pwm = Output(Bool())
}