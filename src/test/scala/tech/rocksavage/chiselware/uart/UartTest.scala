// (c) 2024 Rocksavage Technology, Inc.
// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart

import chisel3._
import chiseltest._
import chiseltest.simulator.VerilatorCFlags
import firrtl2.TargetDirAnnotation
import firrtl2.annotations.Annotation
import org.scalatest.flatspec.AnyFlatSpec
import tech.rocksavage.chiselware.uart.param.UartParams

class UartTest extends AnyFlatSpec with ChiselScalatestTester {
    val verbose  = false
    val numTests = 2
    val testName = System.getProperty("testName")
    println(s"Argument passed: $testName")

    // System properties for flags
    val enableVcd    = System.getProperty("enableVcd", "true").toBoolean
    val enableFst    = System.getProperty("enableFst", "false").toBoolean
    val useVerilator = System.getProperty("useVerilator", "false").toBoolean

    val buildRoot = "out"
    val testDir   = buildRoot + "/test"

    println(
      s"Test: $testName, VCD: $enableVcd, FST: $enableFst, Verilator: $useVerilator"
    )

    // Constructing the backend annotations based on the flags
    val backendAnnotations = {
        var annos: Seq[Annotation] = Seq() // Initialize with correct type

        if (enableVcd) annos = annos :+ chiseltest.simulator.WriteVcdAnnotation
        if (enableFst) annos = annos :+ chiseltest.simulator.WriteFstAnnotation
        if (useVerilator) {
            annos = annos :+ chiseltest.simulator.VerilatorBackendAnnotation
            annos = annos :+ VerilatorCFlags(Seq("--std=c++17"))
        }
        annos = annos :+ TargetDirAnnotation(testDir)

        annos
    }

    val uartParams = UartParams(
      dataWidth = 32,
      addressWidth = 32,
      maxClocksPerBit = 217, // Example: 25 MHz clock / 115200 baud rate
      maxOutputBits = 8,
      syncDepth = 2
    )

    "UartRx" should "pass a basic test" in {
        test(new UartRx(uartParams)).withAnnotations(backendAnnotations) {
            dut =>
                implicit val clock = dut.clock

                val clocksPerBit  = 217
                val numOutputBits = 8

                // Reset the device
                dut.io.rx.poke(1.U)
                dut.io.rxConfig.clocksPerBitDb.poke(clocksPerBit.U)
                dut.io.rxConfig.numOutputBitsDb.poke(numOutputBits.U)
                dut.io.rxConfig.useParityDb.poke(false.B)

                val chars = Seq('s', 'B', 'C', 'D', 'E', 'F', 'G', 'H')
                for (char <- chars) {
                    UartTestUtils.transactionChar(dut, char, clocksPerBit)
                    dut.io.data.expect(char.U)
                }
        }
    }

    "UartTx" should "pass a basic test" in {
        test(new UartTx(uartParams)).withAnnotations(backendAnnotations) {
            dut =>
                implicit val clock = dut.clock

                // TX configuration parameters.
                val clocksPerBit  = 217
                val numOutputBits = 8

                // Drive the configuration inputs.
                dut.io.txConfig.clocksPerBitDb.poke(clocksPerBit.U)
                dut.io.txConfig.numOutputBitsDb.poke(numOutputBits.U)
                dut.io.txConfig.useParityDb.poke(false.B)

                // Before starting transmission, the transmitter is idle so the output should be high.
                dut.io.tx.expect(true.B)

                dut.clock.step()
                dut.clock.step()

                // Helper function that steps through a bit period (for 'cycles' clock cycles)
                // and checks that the TX output remains constant.
                def expectConstantTx(expected: Boolean, cycles: Int): Unit = {
                    dut.io.tx.expect(expected.B)
                    dut.clock.setTimeout(cycles + 2)
                    for (i <- 0 until cycles + 1) {
                        dut.clock.step()
                    }

                }

                // Choose a known data value to send. Here, we use ASCII 'A' (65 decimal).
                // When transmitting, the sequence will be:
                //   • Start bit : 0
                //   • Data bits: LSB first (for 65, binary is 01000001, so the sequence is: 1,0,0,0,0,0,1,0)
                //   • Stop bit : 1
                val data: Int = 65

                // Compute the eight data bits (LSB first).
                val dataBits: Seq[Boolean] = (0 until numOutputBits).map { i =>
                    ((data >> i) & 1) == 1
                }
                // Build the entire expected sequence.
                val expectedSequence: Seq[Boolean] =
                    Seq(false) ++ dataBits ++ Seq(true)
                println(
                  s"Expected TX sequence (per bit period): $expectedSequence"
                )

                // Initiate transmission by pulsing the load signal with the new data.
                dut.io.txConfig.data.poke(data.U)
                dut.io.txConfig.load.poke(true.B)
                dut.clock
                    .step() // Allow the module to latch the new data and configuration.
                dut.io.txConfig.load.poke(false.B) // Deassert load.

                // For each bit period in the expected sequence, check that the TX output stays at the expected level.
                // That is: start (0), eight data bits, then stop bit (1).
                for (expectedBit <- expectedSequence) {
                    expectConstantTx(expectedBit, clocksPerBit)
                }

                // After the entire transmission, the TX module should return to the idle state (output high).
                expectConstantTx(true, 10)
        }
    }
}
