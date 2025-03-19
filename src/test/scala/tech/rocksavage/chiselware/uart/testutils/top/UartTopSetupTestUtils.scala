// (c) 2024 Rocksavage Technology, Inc.
// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart.testutils.top

import chisel3.Clock
import tech.rocksavage.chiselware.apb.ApbBundle
import tech.rocksavage.chiselware.uart.hw.Uart
import tech.rocksavage.chiselware.uart.testutils.rx.UartRxSetupTestUtils.{
    receiveSetup,
    rxSetBaudRate
}
import tech.rocksavage.chiselware.uart.testutils.tx.UartTxSetupTestUtils.txSetBaudRate

object UartTopSetupTestUtils {

    def setBaudRate(dut: Uart, baudRate: Int, clockFrequency: Int): Unit = {
        implicit val clock = dut.clock

        rxSetBaudRate(dut, baudRate, clockFrequency)
        txSetBaudRate(dut, baudRate, clockFrequency)
    }

    def setupUart(
        apb: ApbBundle,
        uart: Uart,
        clockFreq: Int,
        baudRate: Int,
        useParity: Boolean = false,
        parityOdd: Boolean = false
    )(implicit clock: Clock): Unit = {
        receiveSetup(apb, uart, clockFreq, baudRate, useParity, parityOdd)
        setupTxUart(apb, uart, clockFreq, baudRate, useParity, parityOdd)
    }

}
