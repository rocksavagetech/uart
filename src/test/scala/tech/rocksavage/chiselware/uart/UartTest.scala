package tech.rocksavage.chiselware.uart

import chiseltest._
import chiseltest.simulator.{
    VerilatorCFlags,
    WriteFstAnnotation,
    WriteVcdAnnotation
}
import firrtl2.annotations.Annotation
import firrtl2.options.TargetDirAnnotation
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import tech.rocksavage.chiselware.uart.hw.Uart
import tech.rocksavage.chiselware.uart.testmodules.FullDuplexUart
import tech.rocksavage.chiselware.uart.tests._
import tech.rocksavage.chiselware.uart.types.param.UartParams
import tech.rocksavage.test.coverageCollector

class UartTest extends AnyFlatSpec with ChiselScalatestTester with Matchers {
    val numTests     = 2
    val testNameArg  = System.getProperty("testName")
    val enableVcd    = System.getProperty("enableVcd", "true").toBoolean
    val enableFst    = System.getProperty("enableFst", "false").toBoolean
    var useVerilator = System.getProperty("useVerilator", "false").toBoolean
    val testName = (testNameArg == null || testNameArg == "") match {
        case true  => "specialCaseFifoTransmit"
        case false => testNameArg
    }
    val testDir = "out/test"

    println(s"Running test: $testName")
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
    // Command-line toggles
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
          maxClockFrequency = 25_000_000,
          coverage = true
        )

        info(
          s"Data Width: ${uartParams.dataWidth}, Address Width: ${uartParams.addressWidth}"
        )
        info("--------------------------------")
        val covDir     = "./out/cov"
        val coverage   = true
        val configName = uartParams.dataWidth + "_" + "_16" + "_8"

        name match {

            case "txFifoOverflow" =>
                it should "detect TX FIFO overflow correctly" in {
                    val cov = test(new Uart(uartParams, false))
                        .withAnnotations(backendAnnotations) { dut =>
                            fifoIntegrationTests.txFifoOverflowTest(
                              dut,
                              uartParams
                            )
                        }
                    coverageCollector.collectCoverage(
                      cov.getAnnotationSeq,
                      testName,
                      configName,
                      coverage,
                      covDir
                    )
                }

            case "rxFifoOverflow" =>
                it should "detect RX FIFO overflow correctly" in {
                    val cov = test(new Uart(uartParams, false))
                        .withAnnotations(backendAnnotations) { dut =>
                            fifoIntegrationTests.rxFifoOverflowTest(
                              dut,
                              uartParams
                            )
                        }
                    coverageCollector.collectCoverage(
                      cov.getAnnotationSeq,
                      testName,
                      configName,
                      coverage,
                      covDir
                    )
                }

            case "txFifoUnderflow" =>
                it should "detect TX FIFO underflow correctly" in {
                    val cov = test(new Uart(uartParams, false))
                        .withAnnotations(backendAnnotations) { dut =>
                            fifoIntegrationTests.txFifoUnderflowTest(
                              dut,
                              uartParams
                            )
                        }
                    coverageCollector.collectCoverage(
                      cov.getAnnotationSeq,
                      testName,
                      configName,
                      coverage,
                      covDir
                    )
                }

            case "rxFifoUnderflow" =>
                it should "detect RX FIFO underflow correctly" in {
                    val cov = test(new Uart(uartParams, false))
                        .withAnnotations(backendAnnotations) { dut =>
                            fifoIntegrationTests.rxFifoUnderflowTest(
                              dut,
                              uartParams
                            )
                        }
                    coverageCollector.collectCoverage(
                      cov.getAnnotationSeq,
                      testName,
                      configName,
                      coverage,
                      covDir
                    )
                }

            // Add Random Test Cases
            case "randomFifoTransmit" =>
                it should "pass random fifo transmit test" in {
                    val cov = test(new Uart(uartParams, false))
                        .withAnnotations(backendAnnotations) { dut =>
                            randomTests.randomFifoTransmitTest(dut, uartParams)
                        }
                    coverageCollector.collectCoverage(
                      cov.getAnnotationSeq,
                      testName,
                      configName,
                      coverage,
                      covDir
                    )
                }
            // Add Random Test Cases
            case "randomFifoReceive" =>
                it should "pass random receive test" in {
                    val cov = test(new Uart(uartParams, false))
                        .withAnnotations(backendAnnotations) { dut =>
                            randomTests.randomFifoReceiveTest(dut, uartParams)
                        }
                    coverageCollector.collectCoverage(
                      cov.getAnnotationSeq,
                      testName,
                      configName,
                      coverage,
                      covDir
                    )
                }
            case "specialCaseFifoTransmit" =>
                it should "handle special transmit cases" in {
                    val cov = test(new Uart(uartParams, false))
                        .withAnnotations(backendAnnotations) { dut =>
                            specialCaseTests.specialCaseFifoTransmitTests(
                              dut,
                              uartParams
                            )
                        }
                    coverageCollector.collectCoverage(
                      cov.getAnnotationSeq,
                      testName,
                      configName,
                      coverage,
                      covDir
                    )
                }
            case "specialCaseFifoReceive" =>
                it should "handle special receive cases" in {
                    val cov = test(new Uart(uartParams, false))
                        .withAnnotations(backendAnnotations) { dut =>
                            specialCaseTests.specialCaseFifoReceiveTests(
                              dut,
                              uartParams
                            )
                        }
                    coverageCollector.collectCoverage(
                      cov.getAnnotationSeq,
                      testName,
                      configName,
                      coverage,
                      covDir
                    )
                }

            // Error Tests
            case "stopBitError" =>
                it should "detect stop bit errors correctly" in {
                    val cov = test(new Uart(uartParams, false))
                        .withAnnotations(backendAnnotations) { dut =>
                            errorTests.stopBitErrorTest(dut, uartParams)
                        }
                    coverageCollector.collectCoverage(
                      cov.getAnnotationSeq,
                      testName,
                      configName,
                      coverage,
                      covDir
                    )
                }

            case "invalidRegisterProgrammingError" =>
                it should "detect invalid register programming attempts" in {
                    val cov = test(new Uart(uartParams, false))
                        .withAnnotations(backendAnnotations) { dut =>
                            errorTests.invalidRegisterProgrammingTest(
                              dut,
                              uartParams
                            )
                        }
                    coverageCollector.collectCoverage(
                      cov.getAnnotationSeq,
                      testName,
                      configName,
                      coverage,
                      covDir
                    )
                }

            case "parityError" =>
                it should "detect wrong parity errors correctly" in {
                    val cov = test(new Uart(uartParams, false))
                        .withAnnotations(backendAnnotations) { dut =>
                            errorTests.parityErrorTest(dut, uartParams)
                        }
                    coverageCollector.collectCoverage(
                      cov.getAnnotationSeq,
                      testName,
                      configName,
                      coverage,
                      covDir
                    )
                }
            case "parityErrorRecovery" =>
                it should "recover from parity errors correctly" in {
                    val cov = test(new Uart(uartParams, false))
                        .withAnnotations(backendAnnotations) { dut =>
                            errorTests.parityErrorRecoveryTest(dut, uartParams)
                        }
                    coverageCollector.collectCoverage(
                      cov.getAnnotationSeq,
                      testName,
                      configName,
                      coverage,
                      covDir
                    )
                }

            // FullDuplex Tests
            case "bidirectionalComm" =>
                it should "handle bidirectional communication" in {
                    val cov = test(new FullDuplexUart(uartParams))
                        .withAnnotations(backendAnnotations) { dut =>
                            fullDuplexTests.bidirectionalCommunicationTest(
                              dut,
                              uartParams
                            )
                        }
                    coverageCollector.collectCoverage(
                      cov.getAnnotationSeq,
                      testName,
                      configName,
                      coverage,
                      covDir
                    )
                }

            case "simultaneousTransmit" =>
                it should "handle simultaneous transmission" in {
                    val cov = test(new FullDuplexUart(uartParams))
                        .withAnnotations(backendAnnotations) { dut =>
                            fullDuplexTests.simultaneousTransmissionTest(
                              dut,
                              uartParams
                            )
                        }
                    coverageCollector.collectCoverage(
                      cov.getAnnotationSeq,
                      testName,
                      configName,
                      coverage,
                      covDir
                    )
                }

            case "highSpeedTransmit" =>
                it should "handle high-speed transmission" in {
                    val cov = test(new FullDuplexUart(uartParams))
                        .withAnnotations(backendAnnotations) { dut =>
                            fullDuplexTests.highSpeedTransmissionTest(
                              dut,
                              uartParams
                            )
                        }
                    coverageCollector.collectCoverage(
                      cov.getAnnotationSeq,
                      testName,
                      configName,
                      coverage,
                      covDir
                    )
                }

            case "longTransmission" =>
                it should "handle long transmissions" in {
                    val cov =
                        test(new FullDuplexUart(uartParams)).withAnnotations(
                          backendAnnotations
                        ) { dut =>
                            fullDuplexTests.longTransmissionTest(
                              dut,
                              uartParams
                            )
                        }
                    coverageCollector.collectCoverage(
                      cov.getAnnotationSeq,
                      testName,
                      configName,
                      coverage,
                      covDir
                    )
                }
            case "baudRateSwitch" =>
                it should "handle baud rate switching" in {
                    val cov =
                        test(new FullDuplexUart(uartParams)).withAnnotations(
                          backendAnnotations
                        ) { dut =>
                            fullDuplexTests.baudRateSwitchingTest(
                              dut,
                              uartParams
                            )
                        }
                    coverageCollector.collectCoverage(
                      cov.getAnnotationSeq,
                      testName,
                      configName,
                      coverage,
                      covDir
                    )
                }

            case "lineIdle" =>
                it should "detect line idle correctly" in {
                    val cov = test(new FullDuplexUart(uartParams))
                        .withAnnotations(backendAnnotations) { dut =>
                            fullDuplexTests.lineIdleTest(dut, uartParams)
                        }
                    coverageCollector.collectCoverage(
                      cov.getAnnotationSeq,
                      testName,
                      configName,
                      coverage,
                      covDir
                    )
                }

            case "fullDuplex" =>
                it should "handle bidirectional communication" in {
                    val cov = test(new FullDuplexUart(uartParams))
                        .withAnnotations(backendAnnotations) { dut =>
                            fullDuplexTests.bidirectionalCommunicationTest(
                              dut,
                              uartParams
                            )
                        }
                    coverageCollector.collectCoverage(
                      cov.getAnnotationSeq,
                      testName,
                      configName,
                      coverage,
                      covDir
                    )
                }

            // default => run all tests
            case _ =>
                runAllTests(uartParams, configName, covDir, coverage)
        }
        it should "generate cumulative coverage report" in {
            coverageCollector.saveCumulativeCoverage(coverage, covDir)
        }
    }

    def runAllTests(
        params: UartParams,
        configName: String,
        covDir: String,
        coverage: Boolean
    ): Unit = {

        errorTestsFull(params, configName, covDir, coverage)
        fullDuplexTestsFull(params, configName, covDir, coverage)
        randomTestsFull(params, configName, covDir, coverage)
        specialCaseTestsFull(params, configName, covDir, coverage)
        fifoIntegrationTestsFull(params, configName, covDir, coverage)
    }

    def fifoIntegrationTestsFull(
        params: UartParams,
        configName: String,
        covDir: String,
        coverage: Boolean
    ): Unit = {
        it should "detect TX FIFO overflow correctly" in {
            val cov = test(new Uart(params, false))
                .withAnnotations(backendAnnotations) { dut =>
                    fifoIntegrationTests.txFifoOverflowTest(dut, params)
                }
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              "txFifoOverflowTest",
              configName,
              coverage,
              covDir
            )
        }

        it should "detect RX FIFO overflow correctly" in {
            val cov = test(new Uart(params, false))
                .withAnnotations(backendAnnotations) { dut =>
                    fifoIntegrationTests.rxFifoOverflowTest(dut, params)
                }
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              "rxFifoOverflowTest",
              configName,
              coverage,
              covDir
            )
        }

        it should "detect TX FIFO underflow correctly" in {
            val cov = test(new Uart(params, false))
                .withAnnotations(backendAnnotations) { dut =>
                    fifoIntegrationTests.txFifoUnderflowTest(dut, params)
                }
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              "txFifoUnderflowTest",
              configName,
              coverage,
              covDir
            )
        }

        it should "detect RX FIFO underflow correctly" in {
            val cov = test(new Uart(params, false))
                .withAnnotations(backendAnnotations) { dut =>
                    fifoIntegrationTests.rxFifoUnderflowTest(dut, params)
                }
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              "rxFifoUnderflowTest",
              configName,
              coverage,
              covDir
            )
        }

    }

    def randomTestsFull(
        params: UartParams,
        configName: String,
        covDir: String,
        coverage: Boolean
    ): Unit = {

        it should "pass random fifo transmit test" in {
            val cov = test(new Uart(params, false))
                .withAnnotations(backendAnnotations) { dut =>
                    randomTests.randomFifoTransmitTest(dut, params)
                }
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              "randomFifoTransmitTest",
              configName,
              coverage,
              covDir
            )
        }

        it should "pass random fifo receive test" in {
            val cov = test(new Uart(params, false))
                .withAnnotations(backendAnnotations) { dut =>
                    randomTests.randomFifoReceiveTest(dut, params)
                }
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              "randomFifoReceiveTest",
              configName,
              coverage,
              covDir
            )
        }
    }

    def specialCaseTestsFull(
        params: UartParams,
        configName: String,
        covDir: String,
        coverage: Boolean
    ): Unit = {

        it should "handle special fifo transmit cases" in {
            val cov = test(new Uart(params, false))
                .withAnnotations(backendAnnotations) { dut =>
                    specialCaseTests.specialCaseFifoTransmitTests(dut, params)
                }
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              "specialCaseFifoTransmitTests",
              configName,
              coverage,
              covDir
            )
        }

        it should "handle special fifo receive cases" in {
            val cov = test(new Uart(params, false))
                .withAnnotations(backendAnnotations) { dut =>
                    specialCaseTests.specialCaseFifoReceiveTests(dut, params)
                }
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              "specialCaseFifoReceiveTests",
              configName,
              coverage,
              covDir
            )
        }
    }

    def fullDuplexTestsFull(
        params: UartParams,
        configName: String,
        covDir: String,
        coverage: Boolean
    ): Unit = {
        it should "handle bidirectional communication" in {
            val cov = test(new FullDuplexUart(params))
                .withAnnotations(backendAnnotations) { dut =>
                    fullDuplexTests.bidirectionalCommunicationTest(dut, params)
                }
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              "bidirectionalCommunicationTest",
              configName,
              coverage,
              covDir
            )
        }

        it should "handle simultaneous transmission" in {
            val cov = test(new FullDuplexUart(params))
                .withAnnotations(backendAnnotations) { dut =>
                    fullDuplexTests.simultaneousTransmissionTest(dut, params)
                }
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              "simultaneousTransmissionTest",
              configName,
              coverage,
              covDir
            )
        }

        it should "handle high-speed transmission" in {
            val cov = test(new FullDuplexUart(params))
                .withAnnotations(backendAnnotations) { dut =>
                    fullDuplexTests.highSpeedTransmissionTest(dut, params)
                }
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              "highSpeedTransmissionTest",
              configName,
              coverage,
              covDir
            )
        }

        it should "handle long transmissions" in {
            val cov = test(new FullDuplexUart(params))
                .withAnnotations(backendAnnotations) { dut =>
                    fullDuplexTests.longTransmissionTest(dut, params)
                }
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              "longTransmissionTest",
              configName,
              coverage,
              covDir
            )
        }

        it should "handle baud rate switching" in {
            val cov = test(new FullDuplexUart(params))
                .withAnnotations(backendAnnotations) { dut =>
                    fullDuplexTests.baudRateSwitchingTest(dut, params)
                }
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              "baudRateSwitchingTest",
              configName,
              coverage,
              covDir
            )
        }

        it should "detect line idle correctly" in {
            val cov = test(new FullDuplexUart(params))
                .withAnnotations(backendAnnotations) { dut =>
                    fullDuplexTests.lineIdleTest(dut, params)
                }
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              "lineIdleTest",
              configName,
              coverage,
              covDir
            )
        }
    }

    def errorTestsFull(
        params: UartParams,
        configName: String,
        covDir: String,
        coverage: Boolean
    ): Unit = {
        it should "detect stop bit errors correctly" in {
            val cov = test(new Uart(params, false))
                .withAnnotations(backendAnnotations) { dut =>
                    errorTests.stopBitErrorTest(dut, params)
                }
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              "stopBitErrorTest",
              configName,
              coverage,
              covDir
            )
        }

        it should "detect invalid register programming attempts" in {
            val cov = test(new Uart(params, false))
                .withAnnotations(backendAnnotations) { dut =>
                    errorTests.invalidRegisterProgrammingTest(
                      dut,
                      params
                    )
                }
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              "invalidRegisterProgrammingTest",
              configName,
              coverage,
              covDir
            )
        }

        it should "detect wrong parity errors correctly" in {
            val cov = test(new Uart(params, false))
                .withAnnotations(backendAnnotations) { dut =>
                    errorTests.parityErrorTest(dut, params)
                }
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              "parityErrorTest",
              configName,
              coverage,
              covDir
            )
        }
        it should "recover from parity errors correctly" in {
            val cov = test(new Uart(params, false))
                .withAnnotations(backendAnnotations) { dut =>
                    errorTests.parityErrorRecoveryTest(dut, params)
                }
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              "parityErrorRecoveryTest",
              configName,
              coverage,
              covDir
            )
        }

    }
}
