// (c) 2024 Rocksavage Technology, Inc.
// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.uart

import chisel3._
import chisel3.util._
import tech.rocksavage.chiselware.addrdecode.{AddrDecode, AddrDecodeError}
import tech.rocksavage.chiselware.addressable.RegisterMap
import tech.rocksavage.chiselware.apb.{ApbBundle, ApbParams}
import tech.rocksavage.chiselware.uart.param.UartParams

/** A UartWrapper module that provides an APB interface to program the UART
  * control signals.
  *
  * External signals:
  *   - rx: incoming serial data (from an external PHY or wire)
  *   - tx: outgoing serial data (to an external PHY or wire)
  *
  * Control signals (programmed via the APB interface):
  *   - load: tells the transmitter to start a new transmission
  *   - dataIn: parallel data to be transmitted
  *   - clocksPerBitDb: number of clock cycles per bit period
  *   - numOutputBitsDb: number of data bits to transmit
  *   - useParityDb: flag to indicate whether to use parity during transmission
  */
class Uart(val uartParams: UartParams, formal: Boolean) extends Module {
    // Specify data and address widths from the uartParams
    val dataWidth    = uartParams.dataWidth
    val addressWidth = uartParams.addressWidth

    // Define the top-level IO bundle. This wrapper brings in the APB interface,
    // an external serial rx input, and a serial tx output.
    val io = IO(new Bundle {
        // APB interface for control register access
        val apb = new ApbBundle(ApbParams(dataWidth, addressWidth))
        // External UART signals
        val rx = Input(Bool())
        val tx = Output(Bool())
    })

    // Create a register map to hold the UART control registers.
    // These registers program the internal UART control signals.
    val registerMap = new RegisterMap(dataWidth, addressWidth)

    // Control register for initiating a new transmission.
    val load = RegInit(false.B)
    registerMap.createAddressableRegister(
      load,
      "load",
      verbose = uartParams.verbose
    )

    // Parallel data input for the UART transmitter.
    val dataIn = RegInit(0.U(uartParams.maxOutputBits.W))
    registerMap.createAddressableRegister(
      dataIn,
      "dataIn",
      verbose = uartParams.verbose
    )

    // Register to hold the clocks per bit configuration.
    val clocksPerBitDb = RegInit(
      0.U((log2Ceil(uartParams.maxClocksPerBit) + 1).W)
    )
    registerMap.createAddressableRegister(
      clocksPerBitDb,
      "clocksPerBitDb",
      verbose = uartParams.verbose
    )

    // Register to hold the number of output bits to be transmitted.
    val numOutputBitsDb = RegInit(
      0.U((log2Ceil(uartParams.maxOutputBits) + 1).W)
    )
    registerMap.createAddressableRegister(
      numOutputBitsDb,
      "numOutputBitsDb",
      verbose = uartParams.verbose
    )

    // Register to hold the parity enable flag.
    val useParityDb = RegInit(false.B)
    registerMap.createAddressableRegister(
      useParityDb,
      "useParityDb",
      verbose = uartParams.verbose
    )

    // Register to hold the even parity flag.
    val parityEvenDb = RegInit(false.B)
    registerMap.createAddressableRegister(
      parityEvenDb,
      "parityEvenDb",
      verbose = uartParams.verbose
    )

    // --------------------------------------------------------------------------
    // APB Interface: Use AddrDecode and RegisterMap to implement
    // read/write access to control registers.
    // --------------------------------------------------------------------------

    // Get the address decoding parameters from the register map.
    val addrDecodeParams = registerMap.getAddrDecodeParams
    val addrDecode       = Module(new AddrDecode(addrDecodeParams))
    addrDecode.io.addr     := io.apb.PADDR
    addrDecode.io.en       := true.B
    addrDecode.io.selInput := true.B

    // Drive basic APB handshake signals.
    io.apb.PREADY := io.apb.PENABLE && io.apb.PSEL
    io.apb.PSLVERR := addrDecode.io.errorCode === AddrDecodeError.AddressOutOfRange
    io.apb.PRDATA := 0.U // Default data output

    // Register read/write operations via APB.
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

    // --------------------------------------------------------------------------
    // Instantiate the merged UART module.
    // The merged UART (instantiated from a previously defined Uart module)
    // encapsulates both the RX and TX functionality.
    // --------------------------------------------------------------------------
    val uartInner = Module(new UartInner(uartParams, formal))

    // External connections:
    //  - 'rx' is the external serial input.
    //  - 'tx' is the external serial output.
    uartInner.io.rx := io.rx
    io.tx           := uartInner.io.tx

    // Control signals: drive the internal UART control inputs from the APB mapped registers.
    // TX control signals
    uartInner.io.txControlBundle.load            := load
    uartInner.io.txControlBundle.data            := dataIn
    uartInner.io.txControlBundle.clocksPerBitDb  := clocksPerBitDb
    uartInner.io.txControlBundle.numOutputBitsDb := numOutputBitsDb
    uartInner.io.txControlBundle.useParityDb     := useParityDb

    // ---
    // RX control signals
    uartInner.io.rxControlBundle.clocksPerBitDb  := clocksPerBitDb
    uartInner.io.rxControlBundle.numOutputBitsDb := numOutputBitsDb
    uartInner.io.rxControlBundle.useParityDb     := useParityDb

}
