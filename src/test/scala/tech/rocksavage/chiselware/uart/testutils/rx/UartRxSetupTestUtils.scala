// (c) 2024 Rocksavage Technology, Inc.
// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart.testutils.rx

import chisel3._
import chiseltest._
import tech.rocksavage.chiselware.apb.ApbTestUtils.{readAPB, writeAPB}
import tech.rocksavage.chiselware.uart.hw.Uart
import tech.rocksavage.chiselware.uart.testconfig.UartTestConfig

object UartRxSetupTestUtils {
    def receiveSetup(uart: Uart, config: UartTestConfig)(implicit
        clock: Clock
    ): Unit = {
        clock.setTimeout(1000)

        val clockFrequency = config.clockFrequency
        val baudRate       = config.baudRate
        val numOutputBits  = config.numOutputBits

        // Provide the baud rate
        rxSetBaudRate(uart, baudRate, clockFrequency)

        // Configure UART
        writeAPB(
          uart.io.apb,
          uart.registerMap.getAddressOfRegister("rx_numOutputBitsDb").get.U,
          numOutputBits.U
        )
        writeAPB(
          uart.io.apb,
          uart.registerMap.getAddressOfRegister("rx_useParityDb").get.U,
          config.useParity.B
        )
        writeAPB(
          uart.io.apb,
          uart.registerMap.getAddressOfRegister("rx_parityOddDb").get.U,
          config.parityOdd.B
        )
        writeAPB(
          uart.io.apb,
          uart.registerMap.getAddressOfRegister("rx_almostFullLevel").get.U,
          config.almostFullLevel.U
        )
        writeAPB(
          uart.io.apb,
          uart.registerMap.getAddressOfRegister("rx_almostEmptyLevel").get.U,
          config.almostEmptyLevel.U
        )
        writeAPB(
          uart.io.apb,
          uart.registerMap.getAddressOfRegister("rx_lsbFirst").get.U,
          config.lsbFirst.B
        )

        val foundNumOutputBits = readAPB(
          uart.io.apb,
          uart.registerMap.getAddressOfRegister("rx_numOutputBitsDb").get.U
        )
        val foundUseParity = readAPB(
          uart.io.apb,
          uart.registerMap.getAddressOfRegister("rx_useParityDb").get.U
        )
        val foundParityOdd = readAPB(
          uart.io.apb,
          uart.registerMap.getAddressOfRegister("rx_parityOddDb").get.U
        )

        val foundAlmostFullLevel = readAPB(
          uart.io.apb,
          uart.registerMap.getAddressOfRegister("rx_almostFullLevel").get.U
        )
        val foundAlmostEmptyLevel = readAPB(
          uart.io.apb,
          uart.registerMap.getAddressOfRegister("rx_almostEmptyLevel").get.U
        )

        val foundLsbFirst = readAPB(
          uart.io.apb,
          uart.registerMap.getAddressOfRegister("rx_lsbFirst").get.U
        )

        assert(
          foundNumOutputBits == numOutputBits,
          "numOutputBitsDb register not set correctly"
        )
        assert(
          (foundUseParity == 1) == config.useParity,
          "useParityDb register not set correctly"
        )
        assert(
          (foundParityOdd == 1) == config.parityOdd,
          "parityOddDb register not set correctly"
        )
        assert(
          foundAlmostFullLevel == config.almostFullLevel,
          "almostFullLevel register not set correctly"
        )
        assert(
          foundAlmostEmptyLevel == config.almostEmptyLevel,
          "almostEmptyLevel register not set correctly"
        )
        assert(
          (foundLsbFirst == 1) == config.lsbFirst,
          "lsbFirst register not set correctly"
        )
    }
    
    def rxSetBaudRate(dut: Uart, baudRate: Int, clockFrequency: Int): Unit = {
        implicit val clock = dut.clock

        val rxBaudRateAddr =
            dut.registerMap.getAddressOfRegister("rx_baudRate").get
        val rxClockFreqAddr =
            dut.registerMap.getAddressOfRegister("rx_clockFreq").get
        val rxUpdateBaudAddr =
            dut.registerMap.getAddressOfRegister("rx_updateBaud").get
        val rxClocksPerBitAddr =
            dut.registerMap.getAddressOfRegister("rx_clocksPerBit").get

        writeAPB(dut.io.apb, rxBaudRateAddr.U, baudRate.U)
        writeAPB(dut.io.apb, rxClockFreqAddr.U, clockFrequency.U)
        writeAPB(dut.io.apb, rxUpdateBaudAddr.U, 1.U)

        val clocksPerBitExpected = clockFrequency / (baudRate / 2)

        var breakLoop = false
        val numLoops  = 30
        var loopCount = 0
        while (!breakLoop) {
            val clocksPerBitActual = readAPB(
              dut.io.apb,
              rxClocksPerBitAddr.U
            )
            if (clocksPerBitActual == clocksPerBitExpected) {
                breakLoop = true
            }

            if (loopCount >= numLoops) {
                throw new RuntimeException(
                  s"Failed to set baud rate after $numLoops attempts"
                )
            }

            loopCount += 1
            clock.step(1)
        }
    }

}
