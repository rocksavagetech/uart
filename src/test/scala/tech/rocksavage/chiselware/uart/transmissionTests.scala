// transmissionTests.scala
package tech.rocksavage.chiselware.uart

import chisel3._
import chiseltest._
import tech.rocksavage.chiselware.apb.ApbTestUtils._
import tech.rocksavage.chiselware.uart.param.UartParams

object transmissionTests {
  def basicRxTest(dut: UartRx, params: UartParams): Unit = {
    implicit val clock = dut.clock
    dut.clock.setTimeout(2000) // Add reasonable timeout

    val clocksPerBit = 217
    val numOutputBits = 8

    // Reset the device
    dut.io.rx.poke(1.U)
    dut.io.rxConfig.clocksPerBitDb.poke(clocksPerBit.U)
    dut.io.rxConfig.numOutputBitsDb.poke(numOutputBits.U)
    dut.io.rxConfig.useParityDb.poke(false.B)
    dut.io.rxConfig.parityOddDb.poke(false.B)  // Explicitly set parity mode

    val chars = Seq('s', 'B', 'C', 'D', 'E', 'F', 'G', 'H')
    for (char <- chars) {
      println(s"Testing character: $char")
      UartTestUtils.transactionChar(dut, char, clocksPerBit)
      
      // Wait for valid signal with timeout
      var timeout = 0
      while (!dut.io.valid.peek().litToBoolean && timeout < clocksPerBit * 12) {
        dut.clock.step(1)
        timeout += 1
      }
      
      if (timeout >= clocksPerBit * 12) {
        throw new RuntimeException(s"Timeout waiting for valid signal on character $char")
      }
      
      dut.io.data.expect(char.U)
      dut.clock.step(clocksPerBit) // Wait between characters
    }
  }

  def basicTxTest(dut: UartTx, params: UartParams): Unit = {
    implicit val clock = dut.clock
    dut.clock.setTimeout(2000) // Add reasonable timeout

    val clocksPerBit = 217
    val numOutputBits = 8

    // Drive the configuration inputs
    dut.io.txConfig.clocksPerBitDb.poke(clocksPerBit.U)
    dut.io.txConfig.numOutputBitsDb.poke(numOutputBits.U)
    dut.io.txConfig.useParityDb.poke(false.B)
    dut.io.txConfig.parityOddDb.poke(false.B)  // Explicitly set parity mode

    // Before starting transmission, the transmitter is idle so the output should be high
    dut.io.tx.expect(true.B)

    dut.clock.step(2)

    def expectConstantTx(expected: Boolean, cycles: Int): Unit = {
      var cycleCount = 0
      while (cycleCount < cycles) {
        dut.io.tx.expect(expected.B)
        dut.clock.step(1)
        cycleCount += 1
      }
    }

    val data: Int = 65 // ASCII 'A'
    val dataBits: Seq[Boolean] = (0 until numOutputBits).map { i =>
      ((data >> i) & 1) == 1
    }
    
    // Build the expected sequence
    val expectedSequence: Seq[Boolean] = Seq(false) ++ dataBits ++ Seq(true)
    println(s"Starting transmission of data: ${data.toChar}")

    // Initiate transmission
    dut.io.txConfig.data.poke(data.U)
    dut.io.txConfig.load.poke(true.B)
    dut.clock.step(1)
    dut.io.txConfig.load.poke(false.B)

    // Check each bit with a timeout
    for ((expectedBit, index) <- expectedSequence.zipWithIndex) {
      println(s"Checking bit $index: expected $expectedBit")
      expectConstantTx(expectedBit, clocksPerBit)
    }

    // Verify return to idle
    dut.clock.step(clocksPerBit)
    dut.io.tx.expect(true.B)
  }
}