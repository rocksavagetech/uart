// (c) 2024 Rocksavage Technology, Inc.
// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart.testutils.fifo

import tech.rocksavage.chiselware.uart.UartFifoDataDirection.UartFifoDataDirection
import tech.rocksavage.chiselware.uart._

import scala.math.BigInt.int2bigInt

object UartFifoConfigTestUtils {

    def generateNextValidTxRandomConfig(
        validClockFreqs: Seq[Int],
        validBaudRates: Seq[Int],
        validNumOutputBits: Seq[Int],
        fifoSize: Int
    ): UartFifoTxRuntimeConfig = {
        var datas: Seq[UartData] = Seq()
        var fifoHeight           = 0
        for (_ <- 0 until 2 * fifoSize) {
            // case statement to determine if we should push or pop
            val pushOrPop: UartFifoDataDirection = {
                if (fifoHeight == 0) {
                    UartFifoDataDirection.Push
                } else if (fifoHeight == fifoSize) {
                    UartFifoDataDirection.Pop
                } else {
                    val randomInt = scala.util.Random.nextInt(16)
                    val boolValue = randomInt <= 15 // 1/16 chance of popping
                    boolToPushPop(boolValue)
                }
            }

            if (pushOrPop == UartFifoDataDirection.Push) {
                fifoHeight += 1
            } else {
                fifoHeight = 0
            }
            datas = datas :+ new UartData(
              scala.util.Random
                  .nextInt(2.pow(validNumOutputBits.last).toInt),
              pushOrPop
            )
        }

        // if the height is not 0, we need to pop all the data
        if (fifoHeight != 0) {
            datas = datas :+ new UartData(0, UartFifoDataDirection.Pop)
        }

        while (true) {
            try {
                val config = UartTestConfig(
                  baudRate = validBaudRates(
                    scala.util.Random.nextInt(validBaudRates.length)
                  ),
                  clockFrequency = validClockFreqs(
                    scala.util.Random.nextInt(validClockFreqs.length)
                  ),
                  numOutputBits = validNumOutputBits(
                    scala.util.Random.nextInt(validNumOutputBits.length)
                  ),
                  useParity = scala.util.Random.nextBoolean(),
                  parityOdd = scala.util.Random.nextBoolean()
                )
                return UartFifoTxRuntimeConfig(
                  config = config,
                  data = datas
                )
            } catch {
                case _: IllegalArgumentException =>
            }
        }

        throw new RuntimeException("Failed to generate a valid config")
    }

    def generateNextValidRxRandomConfig(
        validClockFreqs: Seq[Int],
        validBaudRates: Seq[Int],
        validNumOutputBits: Seq[Int],
        fifoSize: Int
    ): UartFifoRxRuntimeConfig = {
        var datas: Seq[UartData] = Seq()
        var fifoHeight           = 0
        for (_ <- 0 until 2 * fifoSize) {
            // case statement to determine if we should push or pop
            val pushOrPop: UartFifoDataDirection = {
                if (fifoHeight == 0) {
                    UartFifoDataDirection.Push
                } else if (fifoHeight == fifoSize) {
                    UartFifoDataDirection.Pop
                } else {
                    val boolValue = scala.util.Random.nextBoolean()
                    boolToPushPop(boolValue)
                }
            }

            if (pushOrPop == UartFifoDataDirection.Push) {
                fifoHeight += 1
            } else {
                fifoHeight -= 1
            }
            datas = datas :+ new UartData(
              scala.util.Random
                  .nextInt(2.pow(validNumOutputBits.last).toInt),
              pushOrPop
            )
        }

        // if the height is not 0, we need to pop all the data

        while (fifoHeight != 0) {
            datas = datas :+ new UartData(0, UartFifoDataDirection.Pop)
            fifoHeight -= 1
        }

        while (true) {
            try {
                val config = UartTestConfig(
                  baudRate = validBaudRates(
                    scala.util.Random.nextInt(validBaudRates.length)
                  ),
                  clockFrequency = validClockFreqs(
                    scala.util.Random.nextInt(validClockFreqs.length)
                  ),
                  numOutputBits = validNumOutputBits(
                    scala.util.Random.nextInt(validNumOutputBits.length)
                  ),
                  useParity = scala.util.Random.nextBoolean(),
                  parityOdd = scala.util.Random.nextBoolean()
                )
                return UartFifoRxRuntimeConfig(
                  config = config,
                  data = datas
                )
            } catch {
                case _: IllegalArgumentException =>
            }
        }

        throw new RuntimeException("Failed to generate a valid config")
    }

    def boolToPushPop(bool: Boolean): UartFifoDataDirection = {
        if (bool) {
            UartFifoDataDirection.Push
        } else {
            UartFifoDataDirection.Pop
        }
    }
}
