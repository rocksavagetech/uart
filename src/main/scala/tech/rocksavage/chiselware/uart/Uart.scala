// (c) 2024 Rocksavage Technology, Inc.
// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)
package tech.rocksavage.chiselware.uart

import chisel3._
import chisel3.util.log2Ceil
import tech.rocksavage.chiselware.addrdecode.{AddrDecode, AddrDecodeError}
import tech.rocksavage.chiselware.addressable.RegisterMap
import tech.rocksavage.chiselware.apb.{ApbBundle, ApbParams}
import tech.rocksavage.chiselware.uart.error.{UartRxError, UartTxError}
import tech.rocksavage.chiselware.uart.param.UartParams

class Uart(val params: UartParams) extends Module {
    val dataWidth    = params.dataWidth
    val addressWidth = params.addressWidth

    // Input/Output bundle for the UART module
    val io = IO(new Bundle {
        val apb = new ApbBundle(ApbParams(dataWidth, addressWidth))
        // Uart RX Signals
        val rx      = Input(Bool())
        val rxData  = Output(UInt(params.dataWidth.W))
        val rxValid = Output(Bool())
        val rxReady = Input(Bool())
        val rxError = Output(UartRxError())
        // Uart TX Signals
        val tx      = Output(Bool())
        val txData  = Input(UInt(params.dataWidth.W))
        val txValid = Output(Bool()) // Changed from Input to Output
        val txReady = Output(Bool())
        val txError = Output(UartTxError())
    })

    // Create a RegisterMap to manage the addressable registers
    val registerMap = new RegisterMap(dataWidth, addressWidth)

    // Define independent registers for RX configuration
    val rxMaxClocksPerBit: UInt = RegInit(
      0.U(log2Ceil(params.maxClocksPerBit).W)
    )
    registerMap.createAddressableRegister(
      rxMaxClocksPerBit,
      "rxMaxClocksPerBit"
    )

    val rxDataBits: UInt = RegInit(0.U(log2Ceil(params.maxOutputBits).W))
    registerMap.createAddressableRegister(rxDataBits, "rxDataBits")

    val rxParityEnable: Bool = RegInit(false.B)
    registerMap.createAddressableRegister(rxParityEnable, "rxParityEnable")

    val rxStopBits: UInt = RegInit(0.U(2.W))
    registerMap.createAddressableRegister(rxStopBits, "rxStopBits")

    // Define independent registers for TX configuration
    val txMaxClocksPerBit: UInt = RegInit(
      0.U(log2Ceil(params.maxClocksPerBit).W)
    )
    registerMap.createAddressableRegister(
      txMaxClocksPerBit,
      "txMaxClocksPerBit"
    )

    val txDataBits: UInt = RegInit(0.U(log2Ceil(params.maxOutputBits).W))
    registerMap.createAddressableRegister(txDataBits, "txDataBits")

    val txParityEnable: Bool = RegInit(false.B)
    registerMap.createAddressableRegister(txParityEnable, "txParityEnable")

    val txStopBits: UInt = RegInit(0.U(2.W))
    registerMap.createAddressableRegister(txStopBits, "txStopBits")

    // Generate AddrDecode
    val addrDecodeParams = registerMap.getAddrDecodeParams
    val addrDecode       = Module(new AddrDecode(addrDecodeParams))
    addrDecode.io.addr     := io.apb.PADDR
    addrDecode.io.en       := true.B
    addrDecode.io.selInput := true.B

    io.apb.PREADY := (io.apb.PENABLE && io.apb.PSEL)
    io.apb.PSLVERR := addrDecode.io.errorCode === AddrDecodeError.AddressOutOfRange
    io.apb.PRDATA := 0.U

    // Control Register Read/Write
    when(io.apb.PSEL && io.apb.PENABLE) {
        when(io.apb.PWRITE) {
            for (reg <- registerMap.getRegisters) {
                when(addrDecode.io.sel(reg.id)) {
                    reg.writeCallback(addrDecode.io.addrOut, io.apb.PWDATA)
                }
            }
        }.otherwise {
            for (reg <- registerMap.getRegisters) {
                when(addrDecode.io.sel(reg.id)) {
                    io.apb.PRDATA := reg.readCallback(addrDecode.io.addrOut)
                }
            }
        }
    }

    // Instantiate the UartInner module
    val uartInner = Module(new UartInner(params))

    // Connect the RX configuration registers to UartInner
    uartInner.io.rx.clocksPerBitDb  := rxMaxClocksPerBit
    uartInner.io.rx.numOutputBitsDb := rxDataBits
    uartInner.io.rx.useParityDb     := rxParityEnable

    // Connect the TX configuration registers to UartInner
    uartInner.io.tx.clocksPerBitDb  := txMaxClocksPerBit
    uartInner.io.tx.numOutputBitsDb := txDataBits
    uartInner.io.tx.useParityDb     := txParityEnable

    // Connect the UART Rx/Tx signals to the top-level IO
    uartInner.io.rx.rx    := io.rx
    io.rxData             := uartInner.io.rx.data
    io.rxValid            := uartInner.io.rx.valid
    uartInner.io.rx.ready := io.rxReady
    io.rxError            := uartInner.io.rx.error

    io.tx                := uartInner.io.tx.tx
    uartInner.io.tx.data := io.txData
    io.txValid           := uartInner.io.tx.valid
    io.txReady           := uartInner.io.tx.ready
    io.txError           := uartInner.io.tx.error
}
