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

class UartRxTest extends AnyFlatSpec with ChiselScalatestTester {
  val verbose = false
  val numTests = 2
  val testName = System.getProperty("testName")
  println(s"Argument passed: $testName")

  // System properties for flags
  val enableVcd = System.getProperty("enableVcd", "false").toBoolean
  val enableFst = System.getProperty("enableFst", "true").toBoolean
  val useVerilator = System.getProperty("useVerilator", "true").toBoolean

  val buildRoot = "out"
  val testDir = buildRoot + "/test"

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

  "UartRx" should "handle reads" in {
    test(new UartRx(uartParams)).withAnnotations(backendAnnotations) { dut =>
      implicit val clock = dut.clock

      val clocksPerBit = 217
      val numOutputBits = 8

      // Reset the device
      dut.io.rx.poke(1.U)
      dut.io.clocksPerBitDb.poke(clocksPerBit.U)
      dut.io.numOutputBitsDb.poke(numOutputBits.U)
      dut.io.useParityDb.poke(false.B)

      val chars = Seq('s', 'B', 'C', 'D', 'E', 'F', 'G', 'H')
      for (char <- chars) {
        UartUtils.transactionChar(dut, char, clocksPerBit)
        dut.io.data.expect(char.U)
      }
    }
  }
}
