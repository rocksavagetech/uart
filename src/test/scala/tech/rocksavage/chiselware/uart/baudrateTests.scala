// baudRateTests.scala
package tech.rocksavage.chiselware.uart

import chisel3._
import chiseltest._
import tech.rocksavage.chiselware.apb.ApbTestUtils._
import tech.rocksavage.chiselware.uart.UartTestUtils.setBaudRate
import tech.rocksavage.chiselware.uart.param.UartParams

object baudRateTests {
    def baudRateAccuracyTest(dut: Uart, params: UartParams): Unit = {
        implicit val clk: Clock = dut.clock
        dut.clock.setTimeout(0)

        val clockFrequency = 25_000_000
        val baudRate       = 115_200

        val clocksPerBit  = clockFrequency / baudRate
        val numOutputBits = 8
        println(s"Testing with clocksPerBit = $clocksPerBit")

        setBaudRate(dut, baudRate, clockFrequency)

        // Configure UART with known good values
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("dataIn").get.U,
          0x55.U
        ) // Alternating pattern
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("useParityDb").get.U,
          false.B
        )
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("parityOddDb").get.U,
          false.B
        )

        println("Initial configuration complete")
        dut.clock.step(5) // Let configuration settle

        // Start transmission
        println("Starting transmission")
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("load").get.U,
          true.B
        )
        dut.clock.step(1)
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("load").get.U,
          false.B
        )

        var lastTx     = dut.io.tx.peekBoolean()
        var edgeCount  = 0
        var cycleCount = 0
        val maxCycles  = clocksPerBit * 12 // One complete character time

        println("Monitoring TX line for transitions")
        while (cycleCount < maxCycles) {
            if (cycleCount % 10 == 0) {
                val txValue = dut.io.tx.peekBoolean()
                println(s"Cycle $cycleCount: TX = $txValue")
            }

            dut.clock.step(1)
            cycleCount += 1

            val currentTx = dut.io.tx.peekBoolean()
            if (currentTx != lastTx) {
                edgeCount += 1
                println(s"Edge detected at cycle $cycleCount")
            }
            lastTx = currentTx
        }

        println(s"Test completed: Found $edgeCount edges in $cycleCount cycles")
        assert(edgeCount > 0, "No edges detected on TX line")
    }

    def baudRateStabilityTest(dut: Uart, params: UartParams): Unit = {
        implicit val clk: Clock = dut.clock
        dut.clock.setTimeout(0)

        val clockFrequency = 25_000_000
        val baudRate       = 115_200

        val clocksPerBit  = clockFrequency / baudRate
        val numOutputBits = 8
        setBaudRate(dut, baudRate, clockFrequency)

        println(s"Starting stability test with clocksPerBit = $clocksPerBit")
        
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("useParityDb").get.U,
          false.B
        )
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("parityOddDb").get.U,
          false.B
        )

        dut.clock.step(5) // Let configuration settle

        // Test with a single character initially
        val testChar = 'A'
        println(s"Testing with character: $testChar")

        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("dataIn").get.U,
          testChar.toInt.U
        )
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("load").get.U,
          true.B
        )
        dut.clock.step(1)
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("load").get.U,
          false.B
        )

        var lastTx     = dut.io.tx.peekBoolean()
        var edgeCount  = 0
        var cycleCount = 0
        val maxCycles  = clocksPerBit * 12

        println("Monitoring TX line for stability")
        while (cycleCount < maxCycles) {
            if (cycleCount % 10 == 0) {
                val txValue = dut.io.tx.peekBoolean()
                println(s"Cycle $cycleCount: TX = $txValue")
            }

            dut.clock.step(1)
            cycleCount += 1

            val currentTx = dut.io.tx.peekBoolean()
            if (currentTx != lastTx) {
                edgeCount += 1
                println(s"Edge detected at cycle $cycleCount")
            }
            lastTx = currentTx
        }

        println(
          s"Stability test completed: Found $edgeCount edges in $cycleCount cycles"
        )
        assert(edgeCount > 0, "No edges detected on TX line")
    }
}
