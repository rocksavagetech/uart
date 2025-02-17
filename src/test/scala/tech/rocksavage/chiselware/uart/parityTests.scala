package tech.rocksavage.chiselware.uart

import chisel3._
import chiseltest._
import tech.rocksavage.chiselware.uart.param.UartParams


object parityTests {
  def rxOddParityTest(dut: UartRx, params: UartParams): Unit = {
    implicit val clock = dut.clock
    dut.clock.setTimeout(2000)
    val clocksPerBit = 217
    val numOutputBits = 8

    // Configure for odd parity
    dut.io.rxConfig.clocksPerBitDb.poke(clocksPerBit.U)
    dut.io.rxConfig.numOutputBitsDb.poke(numOutputBits.U)
    dut.io.rxConfig.useParityDb.poke(true.B)
    dut.io.rxConfig.parityOddDb.poke(true.B)

    val data: Int = 65
    val dataBits = (0 until numOutputBits).map(i => ((data >> i) & 1) == 1).reverse
    val expectedParity = true // computed as 1
    
    // Build the sequence: start (0), 8 data bits, parity bit, stop (1)
    val expectedSequence = Seq(false) ++ dataBits ++ Seq(expectedParity, true)

    // Transmit each bit for one bit-period
    for (bit <- expectedSequence) {
      dut.io.rx.poke(bit.B)
      dut.clock.setTimeout(clocksPerBit + 1)
      dut.clock.step(clocksPerBit)
    }
    
    // After stop bit, data output should be available
    dut.io.data.expect(data.U)
  }

  def txOddParityTest(dut: UartTx, params: UartParams): Unit = {
    implicit val clock = dut.clock
    dut.clock.setTimeout(2000)
    val clocksPerBit = 217
    val numOutputBits = 8

    // Configure TX for odd parity
    dut.io.txConfig.clocksPerBitDb.poke(clocksPerBit.U)
    dut.io.txConfig.numOutputBitsDb.poke(numOutputBits.U)
    dut.io.txConfig.useParityDb.poke(true.B)
    dut.io.txConfig.parityOddDb.poke(true.B)

    // Before loading transmission, TX should be idle high
    dut.io.tx.expect(true.B)
    
    val data: Int = 65
    val dataBits = (0 until numOutputBits).map(i => ((data >> i) & 1) == 1).reverse
    
    // Compute parity
    val numOnes = dataBits.count(identity)
    val evenParity = numOnes % 2 == 0
    val expectedParity = !evenParity

    // Expected output sequence:
    // Start bit (0), then 8 data bits, parity bit, then stop bit (1)
    val expectedSequence = Seq(false) ++ dataBits ++ Seq(expectedParity, true)

    dut.clock.step(2)

    // Initiate transmission
    dut.io.txConfig.data.poke(data.U)
    dut.io.txConfig.load.poke(true.B)
    dut.clock.step() // latch load
    dut.io.txConfig.load.poke(false.B)

    // Helper function
    def expectConstantTx(expected: Boolean): Unit = {
      dut.io.tx.expect(expected.B)
      dut.clock.setTimeout(clocksPerBit + 1)
      dut.clock.step(clocksPerBit)
    }

    // Check the entire sequence
    for (expectedBit <- expectedSequence) {
      expectConstantTx(expectedBit)
    }

    // After transmission, transmitter returns idle high
    dut.io.tx.expect(true.B)
  }
}
