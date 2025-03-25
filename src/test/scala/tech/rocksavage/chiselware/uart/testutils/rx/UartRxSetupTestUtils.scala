// (c) 2024 Rocksavage Technology, Inc.
// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart.testutils.rx

import chisel3._
import chiseltest._
import tech.rocksavage.chiselware.addressable.RegisterMap
import tech.rocksavage.chiselware.apb.ApbBundle
import tech.rocksavage.chiselware.apb.ApbTestUtils.{readAPB, writeAPB}
import tech.rocksavage.chiselware.uart.testconfig.UartTestConfig

object UartRxSetupTestUtils {
    def receiveSetup(
        registerMap: RegisterMap,
        apb: ApbBundle,
        config: UartTestConfig
    )(implicit
        clock: Clock
    ): Unit = {
        clock.setTimeout(1000)

        val clockFrequency = config.clockFrequency
        val baudRate       = config.baudRate
        val numOutputBits  = config.numOutputBits

        // Provide the baud rate
        rxSetBaudRate(registerMap, apb, baudRate, clockFrequency)

        // Configure UART
        writeAPB(
          apb,
          registerMap.getAddressOfRegister("rx_numOutputBitsDb").get.U,
          numOutputBits.U
        )
        writeAPB(
          apb,
          registerMap.getAddressOfRegister("rx_useParityDb").get.U,
          config.useParity.B
        )
        writeAPB(
          apb,
          registerMap.getAddressOfRegister("rx_parityOddDb").get.U,
          config.parityOdd.B
        )
        writeAPB(
          apb,
          registerMap.getAddressOfRegister("rx_almostFullLevel").get.U,
          config.almostFullLevel.U
        )
        writeAPB(
          apb,
          registerMap.getAddressOfRegister("rx_almostEmptyLevel").get.U,
          config.almostEmptyLevel.U
        )
        writeAPB(
          apb,
          registerMap.getAddressOfRegister("rx_lsbFirst").get.U,
          config.lsbFirst.B
        )
        writeAPB(
          apb,
          registerMap.getAddressOfRegister("rx_flush").get.U,
          false.B
        )

        val foundNumOutputBits = readAPB(
          apb,
          registerMap.getAddressOfRegister("rx_numOutputBitsDb").get.U
        )
        val foundUseParity = readAPB(
          apb,
          registerMap.getAddressOfRegister("rx_useParityDb").get.U
        )
        val foundParityOdd = readAPB(
          apb,
          registerMap.getAddressOfRegister("rx_parityOddDb").get.U
        )

        val foundAlmostFullLevel = readAPB(
          apb,
          registerMap.getAddressOfRegister("rx_almostFullLevel").get.U
        )
        val foundAlmostEmptyLevel = readAPB(
          apb,
          registerMap.getAddressOfRegister("rx_almostEmptyLevel").get.U
        )

        val foundLsbFirst = readAPB(
          apb,
          registerMap.getAddressOfRegister("rx_lsbFirst").get.U
        )
        val foundFlush = readAPB(
          apb,
          registerMap.getAddressOfRegister("rx_flush").get.U
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
        assert(
          (foundFlush == 0),
          "flush register not set correctly"
        )
    }

    def rxSetBaudRate(
        registerMap: RegisterMap,
        apb: ApbBundle,
        baudRate: Int,
        clockFrequency: Int
    )(implicit
        clock: Clock
    ): Unit = {

        val rxBaudRateAddr =
            registerMap.getAddressOfRegister("rx_baudRate").get
        val rxClockFreqAddr =
            registerMap.getAddressOfRegister("rx_clockFreq").get
        val rxUpdateBaudAddr =
            registerMap.getAddressOfRegister("rx_updateBaud").get
        val rxClocksPerBitAddr =
            registerMap.getAddressOfRegister("rx_clocksPerBit").get

        writeAPB(apb, rxBaudRateAddr.U, baudRate.U)
        writeAPB(apb, rxClockFreqAddr.U, clockFrequency.U)
        writeAPB(apb, rxUpdateBaudAddr.U, 1.U)

        val clocksPerBitExpected = clockFrequency / (baudRate / 2)

        var breakLoop = false
        val numLoops  = 30
        var loopCount = 0
        while (!breakLoop) {
            val clocksPerBitActual = readAPB(
              apb,
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
