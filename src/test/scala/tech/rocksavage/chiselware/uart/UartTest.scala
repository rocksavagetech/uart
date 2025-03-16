package tech.rocksavage.chiselware.uart

import chiseltest._
import chiseltest.simulator.{
    VerilatorCFlags,
    WriteFstAnnotation,
    WriteVcdAnnotation
}
import firrtl2.annotations.Annotation
import firrtl2.options.TargetDirAnnotation
import org.scalatest.ParallelTestExecution
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import tech.rocksavage.chiselware.uart.param.UartParams
import tech.rocksavage.chiselware.uart.tests._

class UartTest
    extends AnyFlatSpec
    with ChiselScalatestTester
    with Matchers
    with ParallelTestExecution {
    val numTests    = 2
    val testNameArg = System.getProperty("testName")
    // Command-line toggles
    var useVerilator = System.getProperty("useVerilator", "false").toBoolean
    val enableVcd    = System.getProperty("enableVcd", "true").toBoolean
    val enableFst    = System.getProperty("enableFst", "false").toBoolean
    val testName = (testNameArg == null || testNameArg == "") match {
        case true  => "regression"
        case false => testNameArg
    }

    println(s"Running test: $testName")
    val testDir = "out/test"
    useVerilator = false
    val backendAnnotations = {
        var annos: Seq[Annotation] = Seq()
        if (enableVcd) annos = annos :+ WriteVcdAnnotation
        if (enableFst) annos = annos :+ WriteFstAnnotation
        if (useVerilator) {
            annos = annos :+ VerilatorBackendAnnotation
            annos = annos :+ VerilatorCFlags(
              Seq("--std=c++17", "-O3", "-march=native")
            )
        }
        annos = annos :+ TargetDirAnnotation(testDir)
        annos
    }
    runTest(testName)

    def runTest(name: String): Unit = {
        behavior of name

        // Example UART parameters
        val uartParams = UartParams(
          dataWidth = 32,
          addressWidth = 32,
          wordWidth = 8,
          maxOutputBits = 8,
          syncDepth = 2,
          maxBaudRate = 25_000_000,
          maxClockFrequency = 25_000_000
        )

        info(
          s"Data Width: ${uartParams.dataWidth}, Address Width: ${uartParams.addressWidth}"
        )
        info("--------------------------------")

        name match {

            case "txFifoOverflow" =>
                it should "detect TX FIFO overflow correctly" in {
                    test(new Uart(uartParams, false))
                        .withAnnotations(backendAnnotations) { dut =>
                            fifoIntegrationTests.txFifoOverflowTest(
                              dut,
                              uartParams
                            )
                        }
                }

            case "rxFifoOverflow" =>
                it should "detect RX FIFO overflow correctly" in {
                    test(new Uart(uartParams, false))
                        .withAnnotations(backendAnnotations) { dut =>
                            fifoIntegrationTests.rxFifoOverflowTest(
                              dut,
                              uartParams
                            )
                        }
                }

            case "txFifoUnderflow" =>
                it should "detect TX FIFO underflow correctly" in {
                    test(new Uart(uartParams, false))
                        .withAnnotations(backendAnnotations) { dut =>
                            fifoIntegrationTests.txFifoUnderflowTest(
                              dut,
                              uartParams
                            )
                        }
                }

            case "rxFifoUnderflow" =>
                it should "detect RX FIFO underflow correctly" in {
                    test(new Uart(uartParams, false))
                        .withAnnotations(backendAnnotations) { dut =>
                            fifoIntegrationTests.rxFifoUnderflowTest(
                              dut,
                              uartParams
                            )
                        }
                }

//            case "fifoErrorClearing" =>
//                it should "clear FIFO errors correctly" in {
//                    test(new Uart(uartParams, false))
//                        .withAnnotations(backendAnnotations) { dut =>
//                            fifoIntegrationTests.errorClearingTest(
//                              dut,
//                              uartParams
//                            )
//                        }
//                }

            // Add Random Test Cases
            case "randomTransmit" =>
                it should "pass random transmit test" in {
                    test(new Uart(uartParams, false))
                        .withAnnotations(backendAnnotations) { dut =>
                            randomTests.randomTransmitTest(dut, uartParams)
                        }
                }
            // Add Random Test Cases
            case "randomFifoTransmit" =>
                it should "pass random fifo transmit test" in {
                    test(new Uart(uartParams, false))
                        .withAnnotations(backendAnnotations) { dut =>
                            randomTests.randomFifoTransmitTest(dut, uartParams)
                        }
                }
            // Add Random Test Cases
            case "randomReceive" =>
                it should "pass random receive test" in {
                    test(new Uart(uartParams, false))
                        .withAnnotations(backendAnnotations) { dut =>
                            randomTests.randomReceiveTest(dut, uartParams)
                        }
                }
            // Add Random Test Cases
            case "randomFifoReceive" =>
                it should "pass random receive test" in {
                    test(new Uart(uartParams, false))
                        .withAnnotations(backendAnnotations) { dut =>
                            randomTests.randomFifoReceiveTest(dut, uartParams)
                        }
                }
            case "specialCaseTransmit" =>
                it should "handle special transmit cases" in {
                    test(new Uart(uartParams, false))
                        .withAnnotations(backendAnnotations) { dut =>
                            specialCaseTests.specialCaseTransmitTests(
                              dut,
                              uartParams
                            )
                        }
                }
            case "specialCaseFifoTransmit" =>
                it should "handle special transmit cases" in {
                    test(new Uart(uartParams, false))
                        .withAnnotations(backendAnnotations) { dut =>
                            specialCaseTests.specialCaseFifoTransmitTests(
                              dut,
                              uartParams
                            )
                        }
                }
            case "specialCaseReceive" =>
                it should "handle special receive cases" in {
                    test(new Uart(uartParams, false))
                        .withAnnotations(backendAnnotations) { dut =>
                            specialCaseTests.specialCaseReceiveTests(
                              dut,
                              uartParams
                            )
                        }
                }
            case "specialCaseFifoReceive" =>
                it should "handle special receive cases" in {
                    test(new Uart(uartParams, false))
                        .withAnnotations(backendAnnotations) { dut =>
                            specialCaseTests.specialCaseFifoReceiveTests(
                              dut,
                              uartParams
                            )
                        }
                }
            case "changingBaudRate" =>
                it should "handle changing baud rate" in {
                    test(new Uart(uartParams, false))
                        .withAnnotations(backendAnnotations) { dut =>
                            baudrateTests.changeingBaudTest(dut, uartParams)
                        }
                }
            case "randomParity" =>
                it should "handle random parity configurations" in {
                    test(new FullDuplexUart(uartParams))
                        .withAnnotations(backendAnnotations) { dut =>
                            randomTests.randomParityTest(dut, uartParams)
                        }
                }

            // Error Tests
            case "stopBitError" =>
                it should "detect stop bit errors correctly" in {
                    test(new Uart(uartParams, false))
                        .withAnnotations(backendAnnotations) { dut =>
                            errorTests.stopBitErrorTest(dut, uartParams)
                        }
                }

            case "invalidRegisterProgrammingError" =>
                it should "detect invalid register programming attempts" in {
                    test(new Uart(uartParams, false))
                        .withAnnotations(backendAnnotations) { dut =>
                            errorTests.invalidRegisterProgrammingTest(
                              dut,
                              uartParams
                            )
                        }
                }

            case "parityError" =>
                it should "detect wrong parity errors correctly" in {
                    test(new Uart(uartParams, false))
                        .withAnnotations(backendAnnotations) { dut =>
                            errorTests.parityErrorTest(dut, uartParams)
                        }
                }
            case "parityErrorRecovery" =>
                it should "recover from parity errors correctly" in {
                    test(new Uart(uartParams, false))
                        .withAnnotations(backendAnnotations) { dut =>
                            errorTests.parityErrorRecoveryTest(dut, uartParams)
                        }
                }

            // Basic Transmission Tests
            case "basicTransmit" =>
                it should "transmit data correctly" in {
                    test(new UartTx(uartParams))
                        .withAnnotations(backendAnnotations) { dut =>
                            transmissionTests.basicTxTest(dut, uartParams)
                        }
                }
            case "basicBufferedTransmit" =>
                it should "transmit buffered data correctly" in {
                    test(new UartTx(uartParams))
                        .withAnnotations(backendAnnotations) { dut =>
                            transmissionTests.bufferedTxTest(dut, uartParams)
                        }
                }

            case "basicReceive" =>
                it should "receive data correctly" in {
                    test(new UartRx(uartParams))
                        .withAnnotations(backendAnnotations) { dut =>
                            transmissionTests.basicRxTest(dut, uartParams)
                        }
                }
            case "basicBufferedReceive" =>
                it should "receive buffered data correctly" in {
                    test(new UartRx(uartParams))
                        .withAnnotations(backendAnnotations) { dut =>
                            transmissionTests.bufferedRxTest(dut, uartParams)
                        }
                }

            // FullDuplex Tests
            case "bidirectionalComm" =>
                it should "handle bidirectional communication" in {
                    test(new FullDuplexUart(uartParams))
                        .withAnnotations(backendAnnotations) { dut =>
                            fullDuplexTests.bidirectionalCommunicationTest(
                              dut,
                              uartParams
                            )
                        }
                }

            case "simultaneousTransmit" =>
                it should "handle simultaneous transmission" in {
                    test(new FullDuplexUart(uartParams))
                        .withAnnotations(backendAnnotations) { dut =>
                            fullDuplexTests.simultaneousTransmissionTest(
                              dut,
                              uartParams
                            )
                        }
                }

            case "highSpeedTransmit" =>
                it should "handle high-speed transmission" in {
                    test(new FullDuplexUart(uartParams))
                        .withAnnotations(backendAnnotations) { dut =>
                            fullDuplexTests.highSpeedTransmissionTest(
                              dut,
                              uartParams
                            )
                        }
                }

            case "longTransmission" =>
                it should "handle long transmissions" in {
                    test(new FullDuplexUart(uartParams)).withAnnotations(
                      backendAnnotations
                    ) { dut =>
                        fullDuplexTests.longTransmissionTest(dut, uartParams)
                    }
                }
            case "baudRateSwitch" =>
                it should "handle baud rate switching" in {
                    test(new FullDuplexUart(uartParams)).withAnnotations(
                      backendAnnotations
                    ) { dut =>
                        fullDuplexTests.baudRateSwitchingTest(dut, uartParams)
                    }
                }

            case "lineIdle" =>
                it should "detect line idle correctly" in {
                    test(new FullDuplexUart(uartParams))
                        .withAnnotations(backendAnnotations) { dut =>
                            fullDuplexTests.lineIdleTest(dut, uartParams)
                        }
                }

            case "fullDuplex" =>
                it should "handle bidirectional communication" in {
                    test(new FullDuplexUart(uartParams))
                        .withAnnotations(backendAnnotations) { dut =>
                            fullDuplexTests.bidirectionalCommunicationTest(
                              dut,
                              uartParams
                            )
                        }
                }

            // default => run all tests
            case _ =>
                runAllTests(uartParams)
        }
    }

    def runAllTests(params: UartParams): Unit = {
        errorTestsFull(params)
        baudrateTestsFull(params)
        transmissionTestsFull(params)
        fullDuplexTestsFull(params)
        randomTestsFull(params)
        specialCaseTestsFull(params)
        fifoIntegrationTestsFull(params)
    }

    def fifoIntegrationTestsFull(params: UartParams): Unit = {
        it should "detect TX FIFO overflow correctly" in {
            test(new Uart(params, false))
                .withAnnotations(backendAnnotations) { dut =>
                    fifoIntegrationTests.txFifoOverflowTest(dut, params)
                }
        }

        it should "detect RX FIFO overflow correctly" in {
            test(new Uart(params, false))
                .withAnnotations(backendAnnotations) { dut =>
                    fifoIntegrationTests.rxFifoOverflowTest(dut, params)
                }
        }

        it should "detect TX FIFO underflow correctly" in {
            test(new Uart(params, false))
                .withAnnotations(backendAnnotations) { dut =>
                    fifoIntegrationTests.txFifoUnderflowTest(dut, params)
                }
        }

        it should "detect RX FIFO underflow correctly" in {
            test(new Uart(params, false))
                .withAnnotations(backendAnnotations) { dut =>
                    fifoIntegrationTests.rxFifoUnderflowTest(dut, params)
                }
        }

//        it should "clear FIFO errors correctly" in {
//            test(new Uart(params, false))
//                .withAnnotations(backendAnnotations) { dut =>
//                    fifoIntegrationTests.errorClearingTest(dut, params)
//                }
//        }
    }

    def randomTestsFull(params: UartParams): Unit = {
        it should "pass random transmit test" in {
            test(new Uart(params, false))
                .withAnnotations(backendAnnotations) { dut =>
                    randomTests.randomTransmitTest(dut, params)
                }
        }

        it should "pass random receive test" in {
            test(new Uart(params, false))
                .withAnnotations(backendAnnotations) { dut =>
                    randomTests.randomReceiveTest(dut, params)
                }
        }

        it should "pass random fifo transmit test" in {
            test(new Uart(params, false))
                .withAnnotations(backendAnnotations) { dut =>
                    randomTests.randomFifoTransmitTest(dut, params)
                }
        }

        it should "pass random fifo receive test" in {
            test(new Uart(params, false))
                .withAnnotations(backendAnnotations) { dut =>
                    randomTests.randomFifoReceiveTest(dut, params)
                }
        }

        it should "handle random parity configurations" in {
            test(new FullDuplexUart(params))
                .withAnnotations(backendAnnotations) { dut =>
                    randomTests.randomParityTest(dut, params)
                }
        }
    }

    def specialCaseTestsFull(params: UartParams): Unit = {
        it should "handle special transmit cases" in {
            test(new Uart(params, false))
                .withAnnotations(backendAnnotations) { dut =>
                    specialCaseTests.specialCaseTransmitTests(dut, params)
                }
        }

        it should "handle special fifo transmit cases" in {
            test(new Uart(params, false))
                .withAnnotations(backendAnnotations) { dut =>
                    specialCaseTests.specialCaseFifoTransmitTests(dut, params)
                }
        }

        it should "handle special receive cases" in {
            test(new Uart(params, false))
                .withAnnotations(backendAnnotations) { dut =>
                    specialCaseTests.specialCaseReceiveTests(dut, params)
                }
        }

        it should "handle special fifo receive cases" in {
            test(new Uart(params, false))
                .withAnnotations(backendAnnotations) { dut =>
                    specialCaseTests.specialCaseFifoReceiveTests(dut, params)
                }
        }
    }

    def baudrateTestsFull(params: UartParams): Unit = {
        it should "handle changing baud rate" in {
            test(new Uart(params, false))
                .withAnnotations(backendAnnotations) { dut =>
                    baudrateTests.changeingBaudTest(dut, params)
                }
        }
    }

    def transmissionTestsFull(params: UartParams): Unit = {
        it should "transmit data correctly" in {
            test(new UartTx(params)).withAnnotations(backendAnnotations) {
                dut =>
                    transmissionTests.basicTxTest(dut, params)
            }
        }

        it should "receive data correctly" in {
            test(new UartRx(params)).withAnnotations(backendAnnotations) {
                dut =>
                    transmissionTests.basicRxTest(dut, params)
            }
        }
    }

    def fullDuplexTestsFull(params: UartParams): Unit = {
        it should "handle bidirectional communication" in {
            test(new FullDuplexUart(params))
                .withAnnotations(backendAnnotations) { dut =>
                    fullDuplexTests.bidirectionalCommunicationTest(dut, params)
                }
        }

        it should "handle simultaneous transmission" in {
            test(new FullDuplexUart(params))
                .withAnnotations(backendAnnotations) { dut =>
                    fullDuplexTests.simultaneousTransmissionTest(dut, params)
                }
        }

//        it should "handle mixed baud rates" in {
//            test(new FullDuplexUart(params))
//                .withAnnotations(backendAnnotations) { dut =>
//                    fullDuplexTests.mixedBaudRateTest(dut, params)
//                }
//        }

        it should "handle high-speed transmission" in {
            test(new FullDuplexUart(params))
                .withAnnotations(backendAnnotations) { dut =>
                    fullDuplexTests.highSpeedTransmissionTest(dut, params)
                }
        }

        it should "handle long transmissions" in {
            test(new FullDuplexUart(params))
                .withAnnotations(backendAnnotations) { dut =>
                    fullDuplexTests.longTransmissionTest(dut, params)
                }
        }

//        it should "recover from errors" in {
//            test(new FullDuplexUart(params))
//                .withAnnotations(backendAnnotations) { dut =>
//                    fullDuplexTests.errorRecoveryTest(dut, params)
//                }
//        }
//
//        it should "be immune to noise" in {
//            test(new FullDuplexUart(params))
//                .withAnnotations(backendAnnotations) { dut =>
//                    fullDuplexTests.noiseImmunityTest(dut, params)
//                }
//        }

        it should "handle baud rate switching" in {
            test(new FullDuplexUart(params))
                .withAnnotations(backendAnnotations) { dut =>
                    fullDuplexTests.baudRateSwitchingTest(dut, params)
                }
        }

        it should "detect line idle correctly" in {
            test(new FullDuplexUart(params))
                .withAnnotations(backendAnnotations) { dut =>
                    fullDuplexTests.lineIdleTest(dut, params)
                }
        }
    }

    def errorTestsFull(params: UartParams): Unit = {
        it should "detect stop bit errors correctly" in {
            test(new Uart(params, false))
                .withAnnotations(backendAnnotations) { dut =>
                    errorTests.stopBitErrorTest(dut, params)
                }
        }

        it should "detect invalid register programming attempts" in {
            test(new Uart(params, false))
                .withAnnotations(backendAnnotations) { dut =>
                    errorTests.invalidRegisterProgrammingTest(
                      dut,
                      params
                    )
                }
        }

        it should "detect wrong parity errors correctly" in {
            test(new Uart(params, false))
                .withAnnotations(backendAnnotations) { dut =>
                    errorTests.parityErrorTest(dut, params)
                }
        }
        it should "recover from parity errors correctly" in {
            test(new Uart(params, false))
                .withAnnotations(backendAnnotations) { dut =>
                    errorTests.parityErrorRecoveryTest(dut, params)
                }
        }

    }

    // Coverage collection helper
    def coverageCollection(
        cov: Seq[Annotation],
        params: UartParams,
        testName: String
    ): Unit = {}
}
