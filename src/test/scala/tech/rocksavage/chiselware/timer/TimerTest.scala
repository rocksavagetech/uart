// (c) 2024 Rocksavage Technology, Inc.
// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.timer

import chisel3._
import chiseltest._
import chiseltest.simulator.VerilatorCFlags
import firrtl2.TargetDirAnnotation
import firrtl2.annotations.Annotation
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import tech.rocksavage.chiselware.apb.ApbBundle
import tech.rocksavage.chiselware.apb.ApbTestUtils._
import tech.rocksavage.chiselware.addressable.RegisterMap
import tech.rocksavage.chiselware.timer.Timer
import tech.rocksavage.chiselware.timer.bundle.TimerInterruptEnum
import tech.rocksavage.chiselware.timer.param.TimerParams

class TimerTest extends AnyFlatSpec with ChiselScalatestTester {
  val verbose = false
  val numTests = 2
  val testName = System.getProperty("testName")
  println(s"Argument passed: $testName")

  // System properties for flags
  val enableVcd = System.getProperty("enableVcd", "false").toBoolean
  val enableFst = System.getProperty("enableFst", "true").toBoolean
  val useVerilator = System.getProperty("useVerilator", "false").toBoolean

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

  val timerParams = TimerParams(
    dataWidth = 32,
    addressWidth = 32,
    countWidth = 64
  )

  "Timer" should "correctly handle register writes and reads" in {
    test(new Timer(timerParams)).withAnnotations(backendAnnotations) { dut =>
      implicit val clock = dut.clock

      // Get the register map from the Timer module
      val registerMap = dut.registerMap

      // Get the addresses of the registers
      val enAddr = registerMap.getAddressOfRegister("en").get
      val prescalerAddr = registerMap.getAddressOfRegister("prescaler").get
      val maxCountAddr = registerMap.getAddressOfRegister("maxCount").get
      val pwmCeilingAddr = registerMap.getAddressOfRegister("pwmCeiling").get
      val setCountValueAddr = registerMap.getAddressOfRegister("setCountValue").get
      val setCountAddr = registerMap.getAddressOfRegister("setCount").get

      // Write to the prescaler register
      writeAPB(dut.io.apb, prescalerAddr.U, 10.U)
      readAPB(dut.io.apb, prescalerAddr.U) shouldEqual 10

      // Write to the maxCount register
      writeAPB(dut.io.apb, maxCountAddr.U, 1024.U)
      readAPB(dut.io.apb, maxCountAddr.U) shouldEqual 1024

      // Write to the pwmCeiling register
      writeAPB(dut.io.apb, pwmCeilingAddr.U, 50.U)
      readAPB(dut.io.apb, pwmCeilingAddr.U) shouldEqual 50

      // Write to the setCountValue register
      writeAPB(dut.io.apb, setCountValueAddr.U, 20.U)
      readAPB(dut.io.apb, setCountValueAddr.U) shouldEqual 20

      // Write to the setCount register
      writeAPB(dut.io.apb, setCountAddr.U, 1.U)
      readAPB(dut.io.apb, setCountAddr.U) shouldEqual 1

      // Write to the enable register
      writeAPB(dut.io.apb, enAddr.U, 1.U)
      readAPB(dut.io.apb, enAddr.U) shouldEqual 1

      // Step the clock until the count reaches maxCount
      while (dut.io.timerOutput.count.peekInt() < 1000) {
        dut.clock.step(1)
      }

      // Check that maxReached is true
      dut.io.timerOutput.pwm.expect(true.B) // Since count >= pwmCeiling, PWM should be high
    }
  }
}