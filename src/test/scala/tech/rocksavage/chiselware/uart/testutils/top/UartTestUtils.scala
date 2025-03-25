// (c) 2024 Rocksavage Technology, Inc.
// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart.testutils.top

import chisel3._
import chiseltest._
import tech.rocksavage.chiselware.apb.ApbBundle
import tech.rocksavage.chiselware.apb.ApbTestUtils.readAPB
import tech.rocksavage.chiselware.uart.hw.Uart
import tech.rocksavage.chiselware.uart.testconfig.UartFifoDataDirection.UartFifoDataDirection
import tech.rocksavage.chiselware.uart.testconfig._
import tech.rocksavage.chiselware.uart.testmodules.FullDuplexUart

import scala.math.BigInt.int2bigInt

object UartTestUtils {

    def generateNextValidTxRandomConfig(
        validClockFreqs: Seq[Int],
        validBaudRates: Seq[Int],
        validNumOutputBits: Seq[Int],
        fifoSize: Int
    ): UartFifoTxRuntimeConfig = {
        var datas: Seq[UartData] = Seq()
        var fifoHeight           = 0

        val weightPush  = 14
        val weightPop   = 1
        val weightFlush = 1

        for (_ <- 0 until 2 * fifoSize) {
            // case statement to determine if we should push or pop
            val dir: UartFifoDataDirection = {
                if (fifoHeight == 0) {
                    UartFifoDataDirection.Push
                } else if (fifoHeight == fifoSize) {
                    UartFifoDataDirection.Pop
                } else {
                    val pushOrPop = scala.util.Random.nextInt(
                      weightPush + weightPop + weightFlush + 1
                    )
                    pushOrPop match {
                        case x if 0 until weightPush contains x =>
                            UartFifoDataDirection.Push
                        case x
                            if weightPush until weightPush + weightPop contains x =>
                            UartFifoDataDirection.Pop
                        case _ =>
                            UartFifoDataDirection.Flush
                    }
                }
            }

            if (dir == UartFifoDataDirection.Push) {
                fifoHeight += 1
            } else if (dir == UartFifoDataDirection.Pop) {
                fifoHeight = 0
            } else if (dir == UartFifoDataDirection.Flush) {
                fifoHeight = 0
            }
            datas = datas :+ new UartData(
              scala.util.Random
                  .nextInt(2.pow(validNumOutputBits.last).toInt),
              dir
            )
        }

        // if the height is not 0, we need to pop all the data
        if (fifoHeight != 0) {
            datas = datas :+ new UartData(0, UartFifoDataDirection.Pop)
        }

        var iterations = 1000
        while (true) {
//            if (iterations == 0) {
//                throw new RuntimeException("Failed to generate a valid config")
//            }
            iterations -= 1
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
                  parityOdd = scala.util.Random.nextBoolean(),
                  almostFullLevel = scala.util.Random.nextInt(fifoSize),
                  almostEmptyLevel = scala.util.Random.nextInt(fifoSize),
                  lsbFirst = scala.util.Random.nextBoolean()
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

        val weightPush  = 14
        val weightPop   = 1
        val weightFlush = 1

        for (_ <- 0 until 2 * fifoSize) {
            // case statement to determine if we should push or pop
            val dir: UartFifoDataDirection = {
                if (fifoHeight == 0) {
                    UartFifoDataDirection.Push
                } else if (fifoHeight == fifoSize) {
                    UartFifoDataDirection.Pop
                } else {
                    val pushOrPop = scala.util.Random.nextInt(
                      weightPush + weightPop + weightFlush + 1
                    )
                    pushOrPop match {
                        case x if 0 until weightPush contains x =>
                            UartFifoDataDirection.Push
                        case x
                            if weightPush until weightPush + weightPop contains x =>
                            UartFifoDataDirection.Pop
                        case _ =>
                            UartFifoDataDirection.Flush
                    }
                }
            }

            if (dir == UartFifoDataDirection.Push) {
                fifoHeight += 1
            } else if (dir == UartFifoDataDirection.Pop) {
                fifoHeight -= 1
            } else if (dir == UartFifoDataDirection.Flush) {
                fifoHeight = 0
            }
            datas = datas :+ new UartData(
              scala.util.Random
                  .nextInt(2.pow(validNumOutputBits.last).toInt),
              dir
            )
        }

        // if the height is not 0, we need to pop all the data

        while (fifoHeight != 0) {
            datas = datas :+ new UartData(0, UartFifoDataDirection.Pop)
            fifoHeight -= 1
        }

        var iterations = 1000
        while (true) {
//            if (iterations == 0) {
//                throw new RuntimeException("Failed to generate a valid config")
//            }
            iterations -= 1
//            println("Generating new config")
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
                  parityOdd = scala.util.Random.nextBoolean(),
                  almostFullLevel = scala.util.Random.nextInt(fifoSize),
                  almostEmptyLevel = scala.util.Random.nextInt(fifoSize),
                  lsbFirst = scala.util.Random.nextBoolean()
                )
                return UartFifoRxRuntimeConfig(
                  config = config,
                  data = datas
                )
            } catch {
                case e: IllegalArgumentException => {
//                    println(e)
                }
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

    def waitForDataAndVerify(
        apb: ApbBundle,
        uart: Uart,
        expectedData: Int
    )(implicit clock: Clock): Unit = {
        var received    = false
        var timeout     = 0
        val maxTimeout  = 5000
        var dataValid   = false
        var validCycles = 0

        while (!received && timeout < maxTimeout) {
            val rxDataAvailable = readAPB(
              apb,
              uart.registerMap.getAddressOfRegister("rx_dataAvailable").get.U
            )

            if (rxDataAvailable.intValue != 0) {
                validCycles += 1
                if (validCycles >= 2) {
                    dataValid = true
                }
            } else {
                validCycles = 0
            }

            if (dataValid) {
                val receivedData = readAPB(
                  apb,
                  uart.registerMap.getAddressOfRegister("rx_data").get.U
                )
                assert(
                  receivedData == expectedData,
                  s"Data mismatch: expected ${expectedData}, got ${receivedData}"
                )

                val errorStatus = readAPB(
                  apb,
                  uart.registerMap.getAddressOfRegister("error").get.U
                )
                assert(
                  errorStatus == 0,
                  s"Unexpected error status: ${errorStatus}"
                )

                received = true
            }

            clock.step(1)
            timeout += 1
        }

        assert(received, s"Timeout waiting for data after $timeout cycles")
    }

    def verifyIdleState(
        dut: FullDuplexUart
    )(implicit clock: Clock): Unit = {
        assert(dut.io.uart1_tx.peekBoolean(), "UART1 TX should be idle (high)")
        assert(dut.io.uart2_tx.peekBoolean(), "UART2 TX should be idle (high)")

        val uart1Error = readAPB(
          dut.io.uart1Apb,
          dut.getUart1.registerMap.getAddressOfRegister("error").get.U
        )
        val uart2Error = readAPB(
          dut.io.uart2Apb,
          dut.getUart2.registerMap.getAddressOfRegister("error").get.U
        )

        assert(uart1Error == 0, s"UART1 has unexpected errors: $uart1Error")
        assert(uart2Error == 0, s"UART2 has unexpected errors: $uart2Error")

        val uart1DataAvailable = readAPB(
          dut.io.uart1Apb,
          dut.getUart1.registerMap.getAddressOfRegister("rxDataAvailable").get.U
        )
        val uart2DataAvailable = readAPB(
          dut.io.uart2Apb,
          dut.getUart2.registerMap.getAddressOfRegister("rxDataAvailable").get.U
        )

        assert(
          uart1DataAvailable == 0,
          "UART1 should not have data available in idle state"
        )
        assert(
          uart2DataAvailable == 0,
          "UART2 should not have data available in idle state"
        )
    }

}
