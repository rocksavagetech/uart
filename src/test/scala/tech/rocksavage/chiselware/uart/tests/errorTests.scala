package tech.rocksavage.chiselware.uart.tests

import chisel3._
import chiseltest._
import tech.rocksavage.chiselware.apb.ApbTestUtils._
import tech.rocksavage.chiselware.uart.Uart
import tech.rocksavage.chiselware.uart.param.UartParams
import tech.rocksavage.chiselware.uart.testutils.UartTestUtils.setBaudRate

object errorTests {

    /** [Test 1] Invalid Register Programming Error Test
      *
      * This test makes invalid APB writes to configuration registers. For
      * example, it writes a number larger than allowed to the “numOutputBitsDb”
      * register, a too–high baud–rate to the “baudRate” register, and a
      * too–high clock frequency to the “clockFreq” register. The error register
      * is expected to be set.
      */
    def invalidRegisterProgrammingTest(dut: Uart, params: UartParams): Unit = {
        implicit val clk: Clock = dut.clock
        dut.clock.setTimeout(5000)
        // Keep the receiver line idle (logic high)
        dut.io.rx.poke(1.U)

        // -- Example invalid write for numOutputBitsDb --
        val invalidNumBits = (params.maxOutputBits + 1).U
        val regNumBitsAddr =
            dut.registerMap.getAddressOfRegister("tx_numOutputBitsDb").get.U
        println(
          s"Writing invalid numOutputBits: $invalidNumBits to address $regNumBitsAddr"
        )
        writeAPB(dut.io.apb, regNumBitsAddr, invalidNumBits)
        clk.step(1)

        // -- Example invalid write for baudRate --
        val invalidBaudRate = (params.maxBaudRate + 1000).U
        val regBaudAddr =
            dut.registerMap.getAddressOfRegister("tx_baudRate").get.U
        println(
          s"Writing invalid baud rate: $invalidBaudRate to address $regBaudAddr"
        )
        writeAPB(dut.io.apb, regBaudAddr, invalidBaudRate)
        clk.step(1)

        // -- Example invalid write for clockFreq --
        val invalidClockFreq = (params.maxClockFrequency + 1000000).U
        val regClockAddr =
            dut.registerMap.getAddressOfRegister("tx_clockFreq").get.U
        println(
          s"Writing invalid clock frequency: $invalidClockFreq to address $regClockAddr"
        )
        writeAPB(dut.io.apb, regClockAddr, invalidClockFreq)
        clk.step(1)

        // Now read the error register. We expect a non–zero value indicating an error.
        val errorVal = readAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("error").get.U
        )
        println(f"Invalid write test: Error register = $errorVal")
        assert(
          errorVal != 0,
          s"Invalid register writes did not trigger error; error register = 0x$errorVal%02x"
        )
        println("Invalid register programming test PASSED")
    }

    /** [Test 2] UART Receiver Parity Error Test
      *
      * This test sets the RX configuration to use even parity (8 data bits) and
      * then sends the byte 0x55 with a forced wrong parity bit. (For 0x55 the
      * number of one bits is even, so the correct parity should be 0. Here the
      * parity bit is forced to 1.) The test polls the error register until it
      * sees the parity error flag (assumed to be bitmask 0x04).
      */
    def parityErrorTest(dut: Uart, params: UartParams): Unit = {
        implicit val clk: Clock = dut.clock
        dut.clock.setTimeout(5000)
        // Ensure RX line is idle.
        dut.io.rx.poke(true.B)

        val clockFrequency = 25000000
        val baudRate       = 115200
        val clocksPerBit   = clockFrequency / (baudRate / 2)
        val numOutputBits  = 8

        setBaudRate(dut, baudRate, clockFrequency)
        println(
          s"Starting uartRx Parity Error Test with clocksPerBit = $clocksPerBit"
        )

        // Configure UART RX: 8 data bits and parity enabled (even parity)
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("rx_numOutputBitsDb").get.U,
          numOutputBits.U
        )
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("rx_useParityDb").get.U,
          1.U
        )
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("rx_parityOddDb").get.U,
          0.U
        ) // 0 => even parity
        clk.step(clocksPerBit * 2)

        // Clear any existing errors.
        readAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("error").get.U
        )
        clk.step(2)

        // Send the byte 0x55 (binary 01010101 LSB–first) with an incorrect parity bit.
        println("Sending 0x55 with forced WRONG parity bit.")
        // Start bit (low):
        dut.io.rx.poke(false.B)
        clk.step(clocksPerBit)
        // Data bits:
        for (i <- 0 until 8) {
            val bit = ((0x55 >> i) & 1) == 1
            dut.io.rx.poke(bit.B)
            clk.step(clocksPerBit)
        }
        // Forced wrong parity: correct would be 0 for even parity. We send a 1.
        dut.io.rx.poke(true.B)
        clk.step(clocksPerBit)
        // Stop bit:
        dut.io.rx.poke(true.B)
        clk.step(clocksPerBit)
        // Extra idle time:
        dut.io.rx.poke(true.B)
        clk.step(clocksPerBit * 2)

        println("Waiting for parity error detection.")
        var cycleCount          = 0
        var parityErrorDetected = false
        val maxCycles           = clocksPerBit * 15
        while (!parityErrorDetected && cycleCount < maxCycles) {
            val errVal = readAPB(
              dut.io.apb,
              dut.registerMap.getAddressOfRegister("error").get.U
            )
            println(f"Cycle $cycleCount: Error register = 0x$errVal%02x")
            // Check for the parity error flag (here we assume bitmask 0x04 indicates a parity error).
            if (errVal == 0x30) {
                parityErrorDetected = true
                println("Parity error detected!")
            }
            clk.step(1)
            cycleCount += 1
        }
        assert(
          parityErrorDetected,
          s"Never detected parity error after $cycleCount cycles"
        )
        println("uartRx Parity Error Test PASSED")
    }

    /** [Test 3] UART Receiver Stop Bit Error Test
      *
      * This test sends a frame that contains the byte 0xAA (10101010
      * LSB–first). The valid frame should have a high stop–bit; however, this
      * test forces the stop–bit low. It then polls the error register and
      * expects the stop–bit error flag (assumed to be bitmask 0x02) to be set.
      */
    def stopBitErrorTest(dut: Uart, params: UartParams): Unit = {
        implicit val clk: Clock = dut.clock
        dut.clock.setTimeout(5000)
        dut.io.rx.poke(true.B)

        val clockFrequency = 25000000
        val baudRate       = 115200
        val clocksPerBit   = clockFrequency / (baudRate / 2)
        val numOutputBits  = 8

        setBaudRate(dut, baudRate, clockFrequency)
        println(
          s"Starting uartRx Stop Bit Error Test with clocksPerBit = $clocksPerBit"
        )

        // Configure RX for 8 data bits with no parity.
        clk.step(clocksPerBit * 2)
        // Clear existing errors.
        readAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("error").get.U
        )
        clk.step(2)

        // Transmit a frame:
        // Start bit: pull low.
        dut.io.rx.poke(false.B)
        clk.step(clocksPerBit)
        // Send the data bits for 0xAA (LSB–first).
        for (i <- 0 until 8) {
            val bit = ((0xaa >> i) & 1) == 1
            dut.io.rx.poke(bit.B)
            clk.step(clocksPerBit)
        }
        // Invalid stop bit: should be high, but force low.
        dut.io.rx.poke(false.B)
        clk.step(clocksPerBit)
        // Return to idle:
        dut.io.rx.poke(true.B)

        println("Waiting for stop bit error detection.")
        var errorDetected = false
        var cycleCount    = 0
        val maxWaitCycles = clocksPerBit * 4
        while (!errorDetected && cycleCount < maxWaitCycles) {
            val errorVal = readAPB(
              dut.io.apb,
              dut.registerMap.getAddressOfRegister("error").get.U
            )
            println(f"Cycle $cycleCount: Error register = 0x$errorVal%02x")
            // Check for the stop bit error flag (assume bitmask 0x02 indicates a stop bit error).
            if (errorVal == 0x20) {
                errorDetected = true
                println("Stop bit error detected!")
            }
            clk.step(1)
            cycleCount += 1
        }
        assert(
          errorDetected,
          s"Stop bit error not detected after $cycleCount cycles"
        )
        println("uartRx Stop Bit Error Test PASSED")
    }

    /** [Test 4] UART Receiver Parity Error Recovery Test
      *
      * In this test the receiver is first forced to report a parity error by
      * sending a byte (0x55) with an incorrect parity bit. Once the error is
      * detected and read, the error is cleared by writing to the "clearError"
      * register. Then a normal valid frame is sent, and the received data is
      * verified.
      */
    def parityErrorRecoveryTest(dut: Uart, params: UartParams): Unit = {
        implicit val clk: Clock = dut.clock
        dut.clock.setTimeout(5000)
        dut.io.rx.poke(true.B)

        val clockFrequency = 25000000
        val baudRate       = 115200
        val clocksPerBit   = clockFrequency / (baudRate / 2)
        val numOutputBits  = 8

        setBaudRate(dut, baudRate, clockFrequency)
        println(
          s"Starting RX Parity Error Recovery Test with clocksPerBit = $clocksPerBit"
        )

        // Configure RX: 8 data bits with parity enabled (even parity: rx_parityOddDb = 0).
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("rx_numOutputBitsDb").get.U,
          numOutputBits.U
        )
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("rx_useParityDb").get.U,
          1.U
        )
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("rx_parityOddDb").get.U,
          0.U
        )
        clk.step(clocksPerBit * 2)
        // Clear any previous errors.
        readAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("error").get.U
        )
        clk.step(2)

        // --- First, induce a parity error ---
        println("Sending 0x55 with incorrect parity to force a parity error.")
        // Start bit.
        dut.io.rx.poke(false.B)
        clk.step(clocksPerBit)
        // Data bits for 0x55:
        for (i <- 0 until numOutputBits) {
            val bit = ((0x55 >> i) & 1) == 1
            dut.io.rx.poke(bit.B)
            clk.step(clocksPerBit)
        }
        // Send an incorrect parity bit (for even parity the proper bit is 0; we send 1).
        dut.io.rx.poke(true.B)
        clk.step(clocksPerBit)
        // Stop bit.
        dut.io.rx.poke(true.B)
        clk.step(clocksPerBit)
        // Extra idle.
        dut.io.rx.poke(true.B)
        clk.step(clocksPerBit * 2)

        // Poll for the parity error (bitmask 0x04).
        var cycleCount          = 0
        var parityErrorDetected = false
        val maxCycles           = clocksPerBit * 15
        while (!parityErrorDetected && cycleCount < maxCycles) {
            val errVal = readAPB(
              dut.io.apb,
              dut.registerMap.getAddressOfRegister("error").get.U
            )
            if (errVal == 0x30) {
                parityErrorDetected = true
                println("Parity error detected!")
            }
            clk.step(1)
            cycleCount += 1
        }
        assert(
          parityErrorDetected,
          s"Parity error not detected after $cycleCount cycles"
        )
        val errorBeforeClear = readAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("error").get.U
        )
        println(f"Error register before clear = 0x$errorBeforeClear%02x")

        // --- Clear the error ---
        val clearErrorAddr =
            dut.registerMap.getAddressOfRegister("clearError").get.U
        println(
          s"Clearing error by writing to clearError (address = $clearErrorAddr)"
        )
        writeAPB(dut.io.apb, clearErrorAddr, 1.U)
        clk.step(2)
        val errorAfterClear = readAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("error").get.U
        )
        println(f"Error register after clear = 0x$errorAfterClear%02x")
        assert(
          errorAfterClear == 0,
          s"Error register did not clear properly (error = 0x$errorAfterClear%02x)"
        )

        // --- Next, perform a normal valid reception ---
        // For 0x55 with even parity the correct parity bit is 0.
        println("Sending 0x55 with correct parity for a normal transaction.")
        dut.io.rx.poke(false.B) // Start bit
        clk.step(clocksPerBit)
        val bits = Seq(0, 1, 0, 1, 0, 1, 0, 1)
        for (i <- 0 until numOutputBits) {
            val bit = bits(i)
            dut.io.rx.poke(bit.B)
            clk.step(clocksPerBit)
        }
        // Send the correct parity bit (0 for even parity).
        dut.io.rx.poke(false.B)
        clk.step(clocksPerBit)
        // Stop bit.
        dut.io.rx.poke(true.B)
        clk.step(clocksPerBit)
        // Extra idle.
        dut.io.rx.poke(true.B)
        clk.step(clocksPerBit * 2)

        // Poll until valid data is available.
        var received = false
        cycleCount = 0
        val maxValidate = clocksPerBit * 20
        while (!received && cycleCount < maxValidate) {
            val avail = readAPB(
              dut.io.apb,
              dut.registerMap.getAddressOfRegister("rx_dataAvailable").get.U
            )
            if (avail != 0) { received = true }
            clk.step(1)
            cycleCount += 1
        }
        assert(received, s"Did not receive valid data after $cycleCount cycles")
        clk.step(1)
        val receivedData = readAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("rx_data").get.U
        )
        println(f"Normal transaction received data = 0x$receivedData%02x")
        assert(
          receivedData == 0x55,
          s"Data mismatch; expected 0x55, got 0x$receivedData%02x"
        )
        println("UART Rx Parity Error Recovery Test PASSED")
    }
}
