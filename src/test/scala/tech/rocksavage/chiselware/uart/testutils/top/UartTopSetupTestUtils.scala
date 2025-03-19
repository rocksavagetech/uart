// (c) 2024 Rocksavage Technology, Inc.
// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart.testutils.top

import chisel3.Clock
import tech.rocksavage.chiselware.addressable.RegisterMap
import tech.rocksavage.chiselware.apb.ApbBundle
import tech.rocksavage.chiselware.uart.testconfig.UartTestConfig
import tech.rocksavage.chiselware.uart.testutils.rx.UartRxSetupTestUtils.{
    receiveSetup,
    rxSetBaudRate
}
import tech.rocksavage.chiselware.uart.testutils.tx.UartTxSetupTestUtils.{
    transmitSetup,
    txSetBaudRate
}

object UartTopSetupTestUtils {

    def setBaudRate(
        registerMap: RegisterMap,
        apb: ApbBundle,
        baudRate: Int,
        clockFrequency: Int
    )(implicit clock: Clock): Unit = {
        rxSetBaudRate(registerMap, apb, baudRate, clockFrequency)
        txSetBaudRate(registerMap, apb, baudRate, clockFrequency)
    }

    def setupUart(
        registerMap: RegisterMap,
        apb: ApbBundle,
        config: UartTestConfig
    )(implicit clock: Clock): Unit = {
        receiveSetup(registerMap, apb, config)
        transmitSetup(registerMap, apb, config)
    }

}
