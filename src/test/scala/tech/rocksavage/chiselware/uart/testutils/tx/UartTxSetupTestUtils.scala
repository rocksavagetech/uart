// (c) 2024 Rocksavage Technology, Inc.
// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart.testutils.tx

import chisel3._
import chiseltest._
import tech.rocksavage.chiselware.apb.ApbTestUtils.{readAPB, writeAPB}
import tech.rocksavage.chiselware.uart.hw.Uart
import tech.rocksavage.chiselware.uart.testconfig.UartTestConfig

object UartTxSetupTestUtils {
    def transmitSetup(dut: Uart, config: UartTestConfig)(implicit
        clock: Clock
    ): Unit = {
        clock.setTimeout(1000)

        val clockFrequency = config.clockFrequency
        val baudRate       = config.baudRate

        val numOutputBits = config.numOutputBits

        // Provide the baud rate
        txSetBaudRate(dut, baudRate, clockFrequency)

        // Configure UART
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("tx_numOutputBitsDb").get.U,
          numOutputBits.U
        )
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("tx_useParityDb").get.U,
          config.useParity.B
        )
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("tx_parityOddDb").get.U,
          config.parityOdd.B
        )
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("tx_almostFullLevel").get.U,
          config.almostFullLevel.U
        )
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("tx_almostEmptyLevel").get.U,
          config.almostEmptyLevel.U
        )
        writeAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("tx_lsbFirst").get.U,
          config.lsbFirst.B
        )

        val foundNumOutputBits = readAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("tx_numOutputBitsDb").get.U
        )
        val foundUseParity = readAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("tx_useParityDb").get.U
        )
        val foundParityOdd = readAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("tx_parityOddDb").get.U
        )
        val foundAlmostFullLevel = readAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("tx_almostFullLevel").get.U
        )
        val foundAlmostEmptyLevel = readAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("tx_almostEmptyLevel").get.U
        )
        val foundLsbFirst = readAPB(
          dut.io.apb,
          dut.registerMap.getAddressOfRegister("tx_lsbFirst").get.U
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
    def txSetBaudRate(dut: Uart, baudRate: Int, clockFrequency: Int): Unit = {
        implicit val clock = dut.clock

        val txBaudRateAddr =
            dut.registerMap.getAddressOfRegister("tx_baudRate").get
        val txClockFreqAddr =
            dut.registerMap.getAddressOfRegister("tx_clockFreq").get
        val txUpdateBaudAddr =
            dut.registerMap.getAddressOfRegister("tx_updateBaud").get
        val txClocksPerBitAddr =
            dut.registerMap.getAddressOfRegister("tx_clocksPerBit").get

        writeAPB(dut.io.apb, txBaudRateAddr.U, baudRate.U)
        writeAPB(dut.io.apb, txClockFreqAddr.U, clockFrequency.U)
        writeAPB(dut.io.apb, txUpdateBaudAddr.U, 1.U)

        val clocksPerBitExpected = clockFrequency / (baudRate / 2)

        clock.setTimeout(1000)
        var breakLoop = false
        while (!breakLoop) {
            val clocksPerBitActual = readAPB(
              dut.io.apb,
              txClocksPerBitAddr.U
            )
            if (clocksPerBitActual == clocksPerBitExpected) {
                breakLoop = true
            }
            clock.step(1)
        }
    }

}
