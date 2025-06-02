package tech.rocksavage.chiselware.uart.hw

import chisel3._
import tech.rocksavage.chiselware.uart.types.param.UartParams

/** Object defining the states for the Baud Generator state machine.
 */
object BaudGenState extends ChiselEnum {

  /** Idle state of the Baud Generator.
   */
  val Idle,

  /** Updating state of the Baud Generator.
   */
  Updating = Value
}

/** UartBaudRateGenerator is a Chisel module that computes the number of clock
 * cycles per UART bit.
 *
 * It uses an iterative multi-cycle divider to compute clocksPerBit = clkFreq /
 * desiredBaud.
 *
 * @param p
 * UART parameters.
 */
class UartBaudRateGenerator(p: UartParams) extends Module {

  /** IO bundle for the UartBaudRateGenerator.
   *
   *   - desiredBaud: The desired baud rate (in Hz) programmed via APB
   *     registers.
   *   - update: Asserted when a new baud configuration is loaded (from a
   *     double-buffered "updateBaud" register).
   *   - clkFreq: The system clock frequency in Hz.
   *   - clocksPerBit: Computed clocks per UART bit (clkFreq divided by
   *     desiredBaud).
   *   - valid: Indicates when the divider has finished computation.
   */
  val io = IO(new Bundle {

    /** The desired baud rate in Hz (for example, 115200). */
    val desiredBaud = Input(UInt(32.W))

    /** Assert this to start a new baud rate update. */
    val update = Input(Bool())

    /** The system clock frequency in Hz (for example, 25_000_000). */
    val clkFreq = Input(UInt(32.W))

    /** Computed clocks per UART bit (clkFreq ÷ desiredBaud). */
    val clocksPerBit = Output(UInt(32.W))

    /** High when the divider has finished computation. */
    val valid = Output(Bool())
  })

  /** Create an instance of the Divider module (iterative multi-cycle
   * divider).
   */
  val divider = Module(new Divider())

  // Note: The divider expects numerator and denominator.
  // Here we calculate: clocksPerBit = clkFreq / desiredBaud

  /** State register for the baud generator state machine, initialized to
   * Idle.
   *
   * Note: The divider expects numerator and denominator. Here we calculate:
   * clocksPerBit = clkFreq / desiredBaud
   */
  val state = RegInit(BaudGenState.Idle)

  /** Register to hold the updated clocks-per-bit value. */
  val updatedClocksPerBit = RegInit(0.U(32.W))

  /** Default assignment of the computed clocks per bit. */
  io.clocksPerBit := updatedClocksPerBit

  /** Default valid signal driven by the divider's valid output. */
  io.valid := divider.io.valid

  /** Register to latch the numerator (clkFreq) for the division. */
  val numeratorReg = RegInit(0.U(32.W))

  /** Register to latch the denominator (desiredBaud) for the division. */
  val denominatorReg = RegInit(0.U(32.W))

  /** Multiplexed numerator based on the current state.
   *
   * When Idle, use the current clock frequency input; otherwise, use the
   * stored numerator.
   *
   * This Allows the Clock Divider latches to hit timing requirements
   */
  val muxedNum = Mux(state === BaudGenState.Idle, io.clkFreq, numeratorReg)

  // Current baud is twice the desired baud, this should correct that error
  val actualDesiredBaud = io.desiredBaud >> 1.U

  /** Multiplexed denominator based on the current state.
   *
   * When Idle, use the current desired baud input; otherwise, use the stored
   * denominator.
   *
   * This Allows the Clock Divider latches to hit timing requirements
   */
  val muxedDen =
    Mux(state === BaudGenState.Idle, actualDesiredBaud, denominatorReg)

  /** Control signal to start the divider. Default is false. */
  divider.io.start := false.B

  /** Set the divider's numerator input. */
  divider.io.numerator := muxedNum

  /** Set the divider's denominator input. */
  divider.io.denominator := muxedDen

  // State machine for handling update and computation.
  when(state === BaudGenState.Idle) {

    /** In Idle state, if an update is requested, latch the new parameters
     * and start division.
     */
    when(io.update) {

      /** Latch the current clock frequency into numerator register. */
      numeratorReg := muxedNum

      /** Latch the current desired baud into denominator register. */
      denominatorReg := muxedDen

      /** Assert start signal to begin divider operation. */
      divider.io.start := true.B

      /** Transition state to Updating. */
      state := BaudGenState.Updating
    }
  }.otherwise { // state === Updating
    /** In Updating state, keep the divider start signal deasserted. */
    divider.io.start := false.B

    /** Once the divider has computed the result, update registers and
     * return to Idle state.
     */
    when(divider.io.valid) {

      /** Capture the computed clocks per bit result. */
      updatedClocksPerBit := divider.io.result

      /** Transition back to Idle state after computation completes. */
      state := BaudGenState.Idle

      /** Reset numerator register. */
      numeratorReg := 0.U

      /** Reset denominator register. */
      denominatorReg := 0.U
    }
  }
}
