// Uart.scala
package tech.rocksavage.chiselware.uart

import chisel3._
import chisel3.util._
import tech.rocksavage.chiselware.addrdecode.{AddrDecode, AddrDecodeError}
import tech.rocksavage.chiselware.addressable.RegisterMap
import tech.rocksavage.chiselware.apb.{ApbBundle, ApbParams}
import tech.rocksavage.chiselware.uart.error.UartError
import tech.rocksavage.chiselware.uart.param.UartParams

class Uart(val uartParams: UartParams, formal: Boolean) extends Module {
    val dataWidth    = uartParams.dataWidth
    val addressWidth = uartParams.addressWidth

    val io = IO(new Bundle {
        val apb = new ApbBundle(ApbParams(dataWidth, addressWidth))
        val rx  = Input(Bool())
        val tx  = Output(Bool())
    })

    // ---------------------------------------------
    // Create and initialize the register map first
    // ---------------------------------------------
    val registerMap = new RegisterMap(dataWidth, addressWidth)

    // ------------------
    // Control registers
    // ------------------
    val load = RegInit(false.B)
    registerMap.createAddressableRegister(
      load,
      "load",
      readOnly = false,
      verbose = uartParams.verbose
    )

    val dataIn = RegInit(0.U(uartParams.maxOutputBits.W))
    registerMap.createAddressableRegister(
      dataIn,
      "dataIn",
      readOnly = false,
      verbose = uartParams.verbose
    )

    // ------------------
    // RX Data & Status
    // ------------------
    val rxDataReg       = RegInit(0.U(uartParams.maxOutputBits.W))
    val rxDataAvailable = RegInit(false.B)

    registerMap.createAddressableRegister(
      rxDataReg,
      "rxData",
      readOnly = true,
      verbose = uartParams.verbose
    )
    registerMap.createAddressableRegister(
      rxDataAvailable,
      "rxDataAvailable",
      readOnly = true,
      verbose = uartParams.verbose
    )

    // -------------
    // Error Status
    // -------------

    val error = Wire(new UartError())
    registerMap.createAddressableRegister(
      error,
      "error",
      readOnly = true,
      verbose = uartParams.verbose
    )

    val clearError = RegInit(false.B)
    registerMap.createAddressableRegister(
      clearError,
      "clearError",
      readOnly = false,
      verbose = uartParams.verbose
    )

    // --------------------
    // Configuration Regs
    // --------------------
    val clocksPerBitDb = RegInit(
      0.U((log2Ceil(uartParams.maxClocksPerBit) + 1).W)
    )
    val numOutputBitsDb = RegInit(
      0.U((log2Ceil(uartParams.maxOutputBits) + 1).W)
    )
    val useParityDb = RegInit(uartParams.parity.B)
    val parityOddDb = RegInit(uartParams.parity.B)

    registerMap.createAddressableRegister(
      clocksPerBitDb,
      "clocksPerBitDb",
      readOnly = false,
      verbose = uartParams.verbose
    )
    registerMap.createAddressableRegister(
      numOutputBitsDb,
      "numOutputBitsDb",
      readOnly = false,
      verbose = uartParams.verbose
    )
    registerMap.createAddressableRegister(
      useParityDb,
      "useParityDb",
      readOnly = false,
      verbose = uartParams.verbose
    )
    registerMap.createAddressableRegister(
      parityOddDb,
      "parityOddDb",
      readOnly = false,
      verbose = uartParams.verbose
    )

    // ---------------------------------------------
    // Initialize Address Decode Logic
    // ---------------------------------------------
    val addrDecodeParams = registerMap.getAddrDecodeParams
    val addrDecode       = Module(new AddrDecode(addrDecodeParams))

    addrDecode.io.addr     := io.apb.PADDR
    addrDecode.io.en       := io.apb.PSEL
    addrDecode.io.selInput := true.B

    // --------------------------------
    // Instantiate the Inner UART logic
    // --------------------------------
    val uartInner = Module(
      new UartInner(uartParams, formal)
    ) // <--- Line in question

    // Handle RX data valid signal
    val rxDataValid = RegNext(uartInner.io.valid)
    when(uartInner.io.valid && !rxDataValid) {
        rxDataReg       := uartInner.io.dataOut
        rxDataAvailable := true.B
        printf(
          p"[Uart.scala DEBUG] New data received: rxDataReg=${uartInner.io.dataOut}\n"
        )
    }

    // APB read handling
    when(!io.apb.PWRITE) {
        // Handle data read
        for (reg <- registerMap.getRegisters if reg.name == "rxData") {
            when(addrDecode.io.sel(reg.id) && rxDataAvailable) {
                rxDataAvailable := false.B
                printf(
                  p"[Uart.scala DEBUG] Data read complete, clearing rxDataAvailable\n"
                )
            }
        }
    }

    // ---------------
    // APB Interface
    // ---------------
    io.apb.PREADY := io.apb.PENABLE && io.apb.PSEL
    io.apb.PSLVERR := addrDecode.io.errorCode === AddrDecodeError.AddressOutOfRange
    io.apb.PRDATA := 0.U

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

    // -------------------------
    // Connect external signals
    // -------------------------
    uartInner.io.rx := io.rx
    io.tx           := uartInner.io.tx

    // Connect TX control signals
    uartInner.io.txControlBundle.load            := load
    uartInner.io.txControlBundle.data            := dataIn
    uartInner.io.txControlBundle.clocksPerBitDb  := clocksPerBitDb
    uartInner.io.txControlBundle.numOutputBitsDb := numOutputBitsDb
    uartInner.io.txControlBundle.useParityDb     := useParityDb
    uartInner.io.txControlBundle.parityOddDb     := parityOddDb

    // Connect RX control signals
    uartInner.io.rxControlBundle.clocksPerBitDb  := clocksPerBitDb
    uartInner.io.rxControlBundle.numOutputBitsDb := numOutputBitsDb
    uartInner.io.rxControlBundle.useParityDb     := useParityDb
    uartInner.io.rxControlBundle.parityOddDb     := parityOddDb
    uartInner.io.rxControlBundle.clearErrorDb    := clearError

    // Connect error signals
    error := uartInner.io.error

    // Add pulse for load signal
    when(load) {
        load := false.B // Auto-clear after one cycle
    }
}
