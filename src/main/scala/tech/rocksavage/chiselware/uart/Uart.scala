// Uart.scala
package tech.rocksavage.chiselware.uart

import chisel3._
import chisel3.util._
import tech.rocksavage.chiselware.addrdecode.{AddrDecode, AddrDecodeError}
import tech.rocksavage.chiselware.addressable.RegisterMap
import tech.rocksavage.chiselware.apb.{ApbBundle, ApbParams}
import tech.rocksavage.chiselware.uart.error.{UartError, UartErrorObject}
import tech.rocksavage.chiselware.uart.param.UartParams

class Uart(val uartParams: UartParams, formal: Boolean) extends Module {
    val dataWidth    = uartParams.dataWidth
    val addressWidth = uartParams.addressWidth

    val io = IO(new Bundle {
        val apb = new ApbBundle(ApbParams(dataWidth, addressWidth))
        val rx  = Input(Bool())
        val tx  = Output(Bool())
    })

    // Create a register map (this example reuses one register map but differentiates TX vs RX registers by name)
    val registerMap = new RegisterMap(dataWidth, addressWidth)

    // -------------------------------------------------------
    // TX registers (for the TX control bundle)
    // -------------------------------------------------------
    val load = RegInit(false.B)
    registerMap.createAddressableRegister(
      load,
      "tx_load",
      readOnly = false,
      verbose = uartParams.verbose
    )

    val dataIn = RegInit(0.U(uartParams.maxOutputBits.W))
    registerMap.createAddressableRegister(
      dataIn,
      "tx_dataIn",
      readOnly = false,
      verbose = uartParams.verbose
    )

    // Transmitter configuration registers
    val tx_baud = RegInit(115200.U(32.W))
    registerMap.createAddressableRegister(
      tx_baud,
      "tx_baudRate",
      readOnly = false,
      verbose = uartParams.verbose
    )

    val tx_clockFreq = RegInit(25_000_000.U(32.W))
    registerMap.createAddressableRegister(
      tx_clockFreq,
      "tx_clockFreq",
      readOnly = false,
      verbose = uartParams.verbose
    )

    val tx_updateBaud = RegInit(false.B)
    registerMap.createAddressableRegister(
      tx_updateBaud,
      "tx_updateBaud",
      readOnly = false,
      verbose = uartParams.verbose
    )

    val tx_numOutputBitsDb = RegInit(
      8.U((log2Ceil(uartParams.maxOutputBits) + 1).W)
    )
    registerMap.createAddressableRegister(
      tx_numOutputBitsDb,
      "tx_numOutputBitsDb",
      readOnly = false,
      verbose = uartParams.verbose
    )

    val tx_useParityDb = RegInit(uartParams.parity.B)
    registerMap.createAddressableRegister(
      tx_useParityDb,
      "tx_useParityDb",
      readOnly = false,
      verbose = uartParams.verbose
    )

    val tx_parityOddDb = RegInit(uartParams.parity.B)
    registerMap.createAddressableRegister(
      tx_parityOddDb,
      "tx_parityOddDb",
      readOnly = false,
      verbose = uartParams.verbose
    )

    // -------------------------------------------------------
    // RX registers (for the RX control bundle and RX data/status)
    // -------------------------------------------------------
    val rxData          = WireInit(0.U(uartParams.maxOutputBits.W))
    val rxDataAvailable = RegInit(false.B)
    registerMap.createAddressableRegister(
      rxData,
      "rx_data",
      readOnly = true,
      verbose = uartParams.verbose
    )
    registerMap.createAddressableRegister(
      rxDataAvailable,
      "rx_dataAvailable",
      readOnly = true,
      verbose = uartParams.verbose
    )

    val error = Wire(new UartError())
    registerMap.createAddressableRegister(
      error.rxError,
      "rx_error",
      readOnly = true,
      verbose = uartParams.verbose
    )

    val topError = RegInit(UartErrorObject.None)
    registerMap.createAddressableRegister(
      error.topError,
      "top_error",
      readOnly = true,
      verbose = uartParams.verbose
    )
    error.topError := topError

    val clearError = RegInit(false.B)
    registerMap.createAddressableRegister(
      clearError,
      "rx_clearError",
      readOnly = false,
      verbose = uartParams.verbose
    )

    // Receiver configuration registers
    val rx_baud = RegInit(115200.U(32.W))
    registerMap.createAddressableRegister(
      rx_baud,
      "rx_baudRate",
      readOnly = false,
      verbose = uartParams.verbose
    )

    val rx_clockFreq = RegInit(25_000_000.U(32.W))
    registerMap.createAddressableRegister(
      rx_clockFreq,
      "rx_clockFreq",
      readOnly = false,
      verbose = uartParams.verbose
    )

    val rx_updateBaud = RegInit(false.B)
    registerMap.createAddressableRegister(
      rx_updateBaud,
      "rx_updateBaud",
      readOnly = false,
      verbose = uartParams.verbose
    )

    val rx_numOutputBitsDb = RegInit(
      8.U((log2Ceil(uartParams.maxOutputBits) + 1).W)
    )
    registerMap.createAddressableRegister(
      rx_numOutputBitsDb,
      "rx_numOutputBitsDb",
      readOnly = false,
      verbose = uartParams.verbose
    )

    val rx_useParityDb = RegInit(uartParams.parity.B)
    registerMap.createAddressableRegister(
      rx_useParityDb,
      "rx_useParityDb",
      readOnly = false,
      verbose = uartParams.verbose
    )

    val rx_parityOddDb = RegInit(uartParams.parity.B)
    registerMap.createAddressableRegister(
      rx_parityOddDb,
      "rx_parityOddDb",
      readOnly = false,
      verbose = uartParams.verbose
    )

    // -------------------------------------------------------
    // Clocks-per-bit registers (read-only outputs)
    // -------------------------------------------------------
    val rxClocksPerBit = WireInit(
      0.U((log2Ceil(uartParams.maxClockFrequency) + 1).W)
    )
    val txClocksPerBit = WireInit(
      0.U((log2Ceil(uartParams.maxClockFrequency) + 1).W)
    )
    registerMap.createAddressableRegister(
      rxClocksPerBit,
      "rx_clocksPerBit",
      readOnly = true,
      verbose = uartParams.verbose
    )
    registerMap.createAddressableRegister(
      txClocksPerBit,
      "tx_clocksPerBit",
      readOnly = true,
      verbose = uartParams.verbose
    )

//    val regDesc = registerMap.prettyPrint
//    println(regDesc)

    // ---------------------------------------------------------------
    // APB address decode
    // ---------------------------------------------------------------
    val addrDecodeParams = registerMap.getAddrDecodeParams
    val addrDecode       = Module(new AddrDecode(addrDecodeParams))
    addrDecode.io.addr     := io.apb.PADDR
    addrDecode.io.en       := io.apb.PSEL
    addrDecode.io.selInput := true.B

    // ---------------------------------------------------------------
    // Instantiate the inner UART module (which contains separate TX and RX logic)
    // ---------------------------------------------------------------
    val uartInner = Module(new UartInner(uartParams, formal))

    // rxDataRegRead is a one-cycle pulse that indicates when the RX data register has been read. It is passed inwards to the rx fifo.
    val rxDataRegRead = WireDefault(false.B)

    // txDataRegWrite is a one-cycle pulse that indicates when the TX data register has been written. It is passed inwards to the tx fifo.
    val txDataRegWrite = WireDefault(false.B)

    // Connect the physical UART pins:
    uartInner.io.rx := io.rx
    io.tx           := uartInner.io.tx

    // Capture RX data when the inner module asserts valid.

    val prevRxValid = RegNext(uartInner.io.rxValid)
    rxData := uartInner.io.dataOut
    when(uartInner.io.rxValid & !prevRxValid) {
        rxDataAvailable := true.B
    }

    // ---------------------------------------------------------------
    // Connect the control bundles using the separate configuration registers:
    //   TX: load, data, tx_baud, tx_clockFreq, tx_updateBaud, tx_numOutputBitsDb, tx_useParityDb, tx_parityOddDb
    //   RX: rx_baud, rx_clockFreq, rx_updateBaud, rx_numOutputBitsDb, rx_useParityDb, rx_parityOddDb, rx_clearError
    // ---------------------------------------------------------------
    // TX control bundle connection:
    uartInner.io.txControlBundle.load            := load
    uartInner.io.txControlBundle.data            := dataIn
    uartInner.io.txControlBundle.baud            := tx_baud
    uartInner.io.txControlBundle.clockFreq       := tx_clockFreq
    uartInner.io.txControlBundle.updateBaud      := tx_updateBaud
    uartInner.io.txControlBundle.numOutputBitsDb := tx_numOutputBitsDb
    uartInner.io.txControlBundle.useParityDb     := tx_useParityDb
    uartInner.io.txControlBundle.parityOddDb     := tx_parityOddDb
    uartInner.io.txControlBundle.txDataRegWrite  := txDataRegWrite

    // RX control bundle connection:
    uartInner.io.rxControlBundle.baud            := rx_baud
    uartInner.io.rxControlBundle.clockFreq       := rx_clockFreq
    uartInner.io.rxControlBundle.updateBaud      := rx_updateBaud
    uartInner.io.rxControlBundle.numOutputBitsDb := rx_numOutputBitsDb
    uartInner.io.rxControlBundle.useParityDb     := rx_useParityDb
    uartInner.io.rxControlBundle.parityOddDb     := rx_parityOddDb
    uartInner.io.rxControlBundle.clearErrorDb    := clearError
    uartInner.io.rxControlBundle.rxDataRegRead   := rxDataRegRead

    // ---------------------------------------------------------------
    // Connect clocks per bit outputs from inner module to our registers
    // ---------------------------------------------------------------
    txClocksPerBit := uartInner.io.txClocksPerBit
    rxClocksPerBit := uartInner.io.rxClocksPerBit

    // Connect error status (from inner module)
    error := uartInner.io.error

    // ---------------------------------------------------------------
    // APB read/write interface handling
    // ---------------------------------------------------------------
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

    // If RX data is read, clear the available flag.
    when(!io.apb.PWRITE) {
        for (reg <- registerMap.getRegisters if reg.name == "rx_data") {
            when(addrDecode.io.sel(reg.id) && rxDataAvailable) {
                rxDataRegRead   := true.B
                rxDataAvailable := false.B
            }
        }
    }
    when(io.apb.PWRITE) {
        for (reg <- registerMap.getRegisters if reg.name == "tx_dataIn") {
            when(addrDecode.io.sel(reg.id)) {
                txDataRegWrite := true.B
            }
        }
    }

    // Error Checks
    // Add error checking for invalid register values
    when(io.apb.PSEL && io.apb.PENABLE) {
        val registerProgrammingError = WireDefault(false.B)
        for (reg <- registerMap.getRegisters) {
            when(addrDecode.io.sel(reg.id)) {
                // Check for invalid values in configuration registers
                when(reg.name.contains("numOutputBitsDb").B) {
                    val maxBits = uartParams.maxOutputBits.U
                    when(io.apb.PWDATA > maxBits) {
                        registerProgrammingError := true.B
                    }
                }.elsewhen(reg.name.contains("_baudRate").B) {
                    val maxBaud = uartParams.maxBaudRate.U
                    when(io.apb.PWDATA > maxBaud) {
                        registerProgrammingError := true.B
                    }
                }.elsewhen(reg.name.contains("clockFreq").B) {
                    val maxFreq = uartParams.maxClockFrequency.U
                    when(io.apb.PWDATA > maxFreq) {
                        registerProgrammingError := true.B
                    }
                }
            }
        }

        // Set top-level error if any invalid values detected
        when(registerProgrammingError) {
            topError := UartErrorObject.InvalidRegisterProgramming
        }
    }

    // Generate one‐cycle pulses for the load and updateBaud signals.
    when(load) {
        load := false.B
    }
    when(tx_updateBaud) {
        tx_updateBaud := false.B
    }
    when(rx_updateBaud) {
        rx_updateBaud := false.B
    }
    when(clearError) {
        clearError := false.B
        topError   := UartErrorObject.None
    }
}
