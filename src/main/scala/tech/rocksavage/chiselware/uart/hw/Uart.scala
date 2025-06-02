// Uart.scala
package tech.rocksavage.chiselware.uart.hw

import chisel3._
import chisel3.util.log2Ceil
import tech.rocksavage.chiselware.addrdecode.{AddrDecode, AddrDecodeError}
import tech.rocksavage.chiselware.addressable.RegisterMap
import tech.rocksavage.chiselware.apb.{ApbBundle, ApbParams}
import tech.rocksavage.chiselware.uart.types.bundle.{FifoStatusBundle, UartInterruptBundle}
import tech.rocksavage.chiselware.uart.types.error.{UartErrorBundle, UartRxError, UartTopError, UartTxError}
import tech.rocksavage.chiselware.uart.types.param.UartParams
import tech.rocksavage.test.TestUtils.coverAll

class Uart(val uartParams: UartParams, formal: Boolean) extends Module {
  val dataWidth = uartParams.dataWidth
  val addressWidth = uartParams.addressWidth
  val wordWidth = uartParams.wordWidth

  val io = IO(new Bundle {
    val apb = new ApbBundle(ApbParams(dataWidth, addressWidth))
    val rx = Input(Bool())
    val tx = Output(Bool())
    val interrupts = Output(new UartInterruptBundle(uartParams))
  })

  // Create a register map (this example reuses one register map but differentiates TX vs RX registers by name)
  val registerMap = new RegisterMap(dataWidth, addressWidth, Some(wordWidth))

  val fifoStatusRx = Wire(new FifoStatusBundle(uartParams))
  val fifoStatusTx = Wire(new FifoStatusBundle(uartParams))

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
  val txBaud = RegInit(115200.U(32.W))
  registerMap.createAddressableRegister(
    txBaud,
    "tx_baudRate",
    readOnly = false,
    verbose = uartParams.verbose
  )

  val txClockFreq = RegInit(25_000_000.U(32.W))
  registerMap.createAddressableRegister(
    txClockFreq,
    "tx_clockFreq",
    readOnly = false,
    verbose = uartParams.verbose
  )

  val txUpdateBaud = RegInit(false.B)
  registerMap.createAddressableRegister(
    txUpdateBaud,
    "tx_updateBaud",
    readOnly = false,
    verbose = uartParams.verbose
  )

  val txNumOutputBitsDb = RegInit(
    8.U((log2Ceil(uartParams.maxOutputBits) + 1).W)
  )
  registerMap.createAddressableRegister(
    txNumOutputBitsDb,
    "tx_numOutputBitsDb",
    readOnly = false,
    verbose = uartParams.verbose
  )

  val txUseParityDb = RegInit(false.B)
  registerMap.createAddressableRegister(
    txUseParityDb,
    "tx_useParityDb",
    readOnly = false,
    verbose = uartParams.verbose
  )

  val txParityOddDb = RegInit(false.B)
  registerMap.createAddressableRegister(
    txParityOddDb,
    "tx_parityOddDb",
    readOnly = false,
    verbose = uartParams.verbose
  )
  val txAlmostEmptyLevel = RegInit(
    1.U((log2Ceil(uartParams.bufferSize) + 1).W)
  )
  registerMap.createAddressableRegister(
    txAlmostEmptyLevel,
    "tx_almostEmptyLevel",
    readOnly = false,
    verbose = uartParams.verbose
  )
  val txAlmostFullLevel = RegInit(
    (uartParams.bufferSize - 1).U((log2Ceil(uartParams.bufferSize) + 1).W)
  )
  registerMap.createAddressableRegister(
    txAlmostFullLevel,
    "tx_almostFullLevel",
    readOnly = false,
    verbose = uartParams.verbose
  )
  val txFifoFull = WireInit(false.B)
  registerMap.createAddressableRegister(
    txFifoFull,
    "tx_fifoFull",
    readOnly = true,
    verbose = uartParams.verbose
  )
  val txFifoEmpty = WireInit(true.B)
  registerMap.createAddressableRegister(
    txFifoEmpty,
    "tx_fifoEmpty",
    readOnly = true,
    verbose = uartParams.verbose
  )
  val txFifoAlmostEmpty = WireInit(false.B)
  registerMap.createAddressableRegister(
    txFifoAlmostEmpty,
    "tx_fifoAlmostEmpty",
    readOnly = true,
    verbose = uartParams.verbose
  )
  val txFifoAlmostFull = WireInit(false.B)
  registerMap.createAddressableRegister(
    txFifoAlmostFull,
    "tx_fifoAlmostFull",
    readOnly = true,
    verbose = uartParams.verbose
  )

  // -------------------------------------------------------
  // RX registers (for the RX control bundle and RX data/status)
  // -------------------------------------------------------
  val rxData = WireInit(0.U(uartParams.maxOutputBits.W))
  val rxDataPeek = WireInit(0.U(uartParams.maxOutputBits.W))
  val rxDataAvailable = !fifoStatusRx.empty
  registerMap.createAddressableRegister(
    rxData,
    "rx_data",
    readOnly = true,
    verbose = uartParams.verbose
  )
  registerMap.createAddressableRegister(
    rxDataPeek,
    "rx_dataPeek",
    readOnly = true,
    verbose = uartParams.verbose
  )
  registerMap.createAddressableRegister(
    rxDataAvailable,
    "rx_dataAvailable",
    readOnly = true,
    verbose = uartParams.verbose
  )

  // Create a wire for the error output
  val error = RegInit(0.U.asTypeOf(new UartErrorBundle()))
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

  // Receiver configuration registers
  val rxBaud = RegInit(115200.U(32.W))
  registerMap.createAddressableRegister(
    rxBaud,
    "rx_baudRate",
    readOnly = false,
    verbose = uartParams.verbose
  )

  val rxClockFreq = RegInit(25_000_000.U(32.W))
  registerMap.createAddressableRegister(
    rxClockFreq,
    "rx_clockFreq",
    readOnly = false,
    verbose = uartParams.verbose
  )

  val rxUpdateBaud = RegInit(false.B)
  registerMap.createAddressableRegister(
    rxUpdateBaud,
    "rx_updateBaud",
    readOnly = false,
    verbose = uartParams.verbose
  )

  val rxNumOutputBitsDb = RegInit(
    8.U((log2Ceil(uartParams.maxOutputBits) + 1).W)
  )
  registerMap.createAddressableRegister(
    rxNumOutputBitsDb,
    "rx_numOutputBitsDb",
    readOnly = false,
    verbose = uartParams.verbose
  )

  val rxUseParityDb = RegInit(false.B)
  registerMap.createAddressableRegister(
    rxUseParityDb,
    "rx_useParityDb",
    readOnly = false,
    verbose = uartParams.verbose
  )

  val rxParityOddDb = RegInit(false.B)
  registerMap.createAddressableRegister(
    rxParityOddDb,
    "rx_parityOddDb",
    readOnly = false,
    verbose = uartParams.verbose
  )
  val rxAlmostEmptyLevel = RegInit(
    1.U((log2Ceil(uartParams.bufferSize) + 1).W)
  )
  registerMap.createAddressableRegister(
    rxAlmostEmptyLevel,
    "rx_almostEmptyLevel",
    readOnly = false,
    verbose = uartParams.verbose
  )
  val rxAlmostFullLevel = RegInit(
    (uartParams.bufferSize - 1).U((log2Ceil(uartParams.bufferSize) + 1).W)
  )
  registerMap.createAddressableRegister(
    rxAlmostFullLevel,
    "rx_almostFullLevel",
    readOnly = false,
    verbose = uartParams.verbose
  )
  val rxFifoFull = WireInit(false.B)
  registerMap.createAddressableRegister(
    rxFifoFull,
    "rx_fifoFull",
    readOnly = true,
    verbose = uartParams.verbose
  )
  val rxFifoEmpty = WireInit(true.B)
  registerMap.createAddressableRegister(
    rxFifoEmpty,
    "rx_fifoEmpty",
    readOnly = true,
    verbose = uartParams.verbose
  )
  val rxFifoAlmostEmpty = WireInit(false.B)
  registerMap.createAddressableRegister(
    rxFifoAlmostEmpty,
    "rx_fifoAlmostEmpty",
    readOnly = true,
    verbose = uartParams.verbose
  )
  val rxFifoAlmostFull = WireInit(false.B)
  registerMap.createAddressableRegister(
    rxFifoAlmostFull,
    "rx_fifoAlmostFull",
    readOnly = true,
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

  val rxLsbFirst = RegInit(true.B)
  val txLsbFirst = RegInit(true.B)
  registerMap.createAddressableRegister(
    rxLsbFirst,
    "rx_lsbFirst",
    readOnly = false,
    verbose = uartParams.verbose
  )
  registerMap.createAddressableRegister(
    txLsbFirst,
    "tx_lsbFirst",
    readOnly = false,
    verbose = uartParams.verbose
  )
  val rxFlush = RegInit(false.B)
  val txFlush = RegInit(false.B)
  registerMap.createAddressableRegister(
    rxFlush,
    "rx_flush",
    readOnly = false,
    verbose = uartParams.verbose
  )
  registerMap.createAddressableRegister(
    txFlush,
    "tx_flush",
    readOnly = false,
    verbose = uartParams.verbose
  )

  //    registerMap.prettyPrint()
  //    registerMap.printHeaderFile()

  // ---------------------------------------------------------------
  // APB address decode
  // ---------------------------------------------------------------
  val addrDecodeParams = registerMap.getAddrDecodeParams
  val addrDecode = Module(new AddrDecode(addrDecodeParams))
  addrDecode.io.addrRaw := io.apb.PADDR
  addrDecode.io.en := io.apb.PSEL && io.apb.PENABLE
  addrDecode.io.selInput := true.B

  // ---------------------------------------------------------------
  // Instantiate the inner UART module (which contains separate TX and RX logic)
  // ---------------------------------------------------------------
  val uartInner = Module(new UartInner(uartParams, formal))

  // rxDataRegRead is a one-cycle pulse that indicates when the RX data register has been read.
  // It is passed inwards to the rx fifo.
  val rxDataRegRead = WireDefault(false.B)

  // txDataRegWrite is a one-cycle pulse that indicates when the TX data register has been written.
  // It is passed inwards to the tx fifo.
  val txDataRegWrite = WireDefault(false.B)

  // Connect the physical UART pins:
  uartInner.io.rx := io.rx
  io.tx := uartInner.io.tx

  // Capture RX data when the inner module asserts valid.
  rxData := uartInner.io.dataOut
  rxDataPeek := uartInner.io.dataOut

  // ---------------------------------------------------------------
  // Connect the control bundles using the separate configuration registers:
  //   TX: load, data, tx_baud, tx_clockFreq, tx_updateBaud, tx_numOutputBitsDb, tx_useParityDb, tx_parityOddDb
  //   RX: rx_baud, rx_clockFreq, rx_updateBaud, rx_numOutputBitsDb, rx_useParityDb, rx_parityOddDb, clearError
  // ---------------------------------------------------------------
  // TX control bundle connection:
  uartInner.io.txControlBundle.load := load
  uartInner.io.txControlBundle.data := dataIn
  uartInner.io.txControlBundle.baud := txBaud
  uartInner.io.txControlBundle.clockFreq := txClockFreq
  uartInner.io.txControlBundle.updateBaud := txUpdateBaud
  uartInner.io.txControlBundle.numOutputBitsDb := txNumOutputBitsDb
  uartInner.io.txControlBundle.useParityDb := txUseParityDb
  uartInner.io.txControlBundle.parityOddDb := txParityOddDb
  uartInner.io.txControlBundle.txDataRegWrite := txDataRegWrite
  uartInner.io.txControlBundle.clearErrorDb := clearError
  uartInner.io.txControlBundle.almostEmptyLevel := txAlmostEmptyLevel
  uartInner.io.txControlBundle.almostFullLevel := txAlmostFullLevel
  uartInner.io.txControlBundle.lsbFirst := txLsbFirst
  uartInner.io.txControlBundle.flush := txFlush

  // RX control bundle connection:
  uartInner.io.rxControlBundle.baud := rxBaud
  uartInner.io.rxControlBundle.clockFreq := rxClockFreq
  uartInner.io.rxControlBundle.updateBaud := rxUpdateBaud
  uartInner.io.rxControlBundle.numOutputBitsDb := rxNumOutputBitsDb
  uartInner.io.rxControlBundle.useParityDb := rxUseParityDb
  uartInner.io.rxControlBundle.parityOddDb := rxParityOddDb
  uartInner.io.rxControlBundle.clearErrorDb := clearError
  uartInner.io.rxControlBundle.rxDataRegRead := rxDataRegRead
  uartInner.io.rxControlBundle.almostEmptyLevel := rxAlmostEmptyLevel
  uartInner.io.rxControlBundle.almostFullLevel := rxAlmostFullLevel
  uartInner.io.rxControlBundle.lsbFirst := rxLsbFirst
  uartInner.io.rxControlBundle.flush := rxFlush

  fifoStatusRx := uartInner.io.rxFifoStatus
  fifoStatusTx := uartInner.io.txFifoStatus

  txFifoEmpty := uartInner.io.txFifoStatus.empty
  txFifoFull := uartInner.io.txFifoStatus.full
  txFifoAlmostEmpty := uartInner.io.txFifoStatus.almostEmpty
  txFifoAlmostFull := uartInner.io.txFifoStatus.almostFull

  rxFifoEmpty := uartInner.io.rxFifoStatus.empty
  rxFifoFull := uartInner.io.rxFifoStatus.full
  rxFifoAlmostEmpty := uartInner.io.rxFifoStatus.almostEmpty
  rxFifoAlmostFull := uartInner.io.rxFifoStatus.almostFull

  error.rxError := uartInner.io.error.rxError
  error.txError := uartInner.io.error.txError
  //    error.topError        := uartInner.io.error.topError
  error.addrDecodeError := addrDecode.io.errorCode

  // ---------------------------------------------------------------
  // Connect clocks per bit outputs from inner module to our registers
  // ---------------------------------------------------------------
  txClocksPerBit := uartInner.io.txClocksPerBit
  rxClocksPerBit := uartInner.io.rxClocksPerBit

  // ---------------------------------------------------------------
  // Connect the interrupt bundle
  // ---------------------------------------------------------------
  io.interrupts.dataReceived := !uartInner.io.rxFifoStatus.empty && RegNext(
    uartInner.io.rxFifoStatus.empty
  )

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
      when(
        addrDecode.io.sel(reg.id)
      ) { // fix to send an error when trying to read from an empty fifo
        rxDataRegRead := true.B
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
    for (reg <- registerMap.getRegisters) {
      when(addrDecode.io.sel(reg.id)) {
        // Check for invalid values in configuration registers
        when(reg.name.contains("numOutputBitsDb").B) {
          val maxBits = uartParams.maxOutputBits.U
          when(io.apb.PWDATA > maxBits) {
            error.topError := UartTopError.InvalidRegisterProgramming
          }
        }.elsewhen(reg.name.contains("_baudRate").B) {
          val maxBaud = uartParams.maxBaudRate.U
          when(io.apb.PWDATA > maxBaud) {
            error.topError := UartTopError.InvalidRegisterProgramming
          }
        }.elsewhen(reg.name.contains("clockFreq").B) {
          val maxFreq = uartParams.maxClockFrequency.U
          when(io.apb.PWDATA > maxFreq) {
            error.topError := UartTopError.InvalidRegisterProgramming
          }
        }
      }
    }
  }

  // Generate one‐cycle pulses for the load and updateBaud signals.

  when(
    error.rxError =/= UartRxError.None || error.txError =/= UartTxError.None
      || error.topError =/= UartTopError.None || error.addrDecodeError =/= AddrDecodeError.None
  ) {
    // Print debug message
    if (uartParams.verbose) {
      printf(
        "[Uart.scala DEBUG] Error detected Bits: %b\n",
        error.asUInt
      )
      when(error.rxError =/= UartRxError.None) {
        printf(
          "[Uart.scala DEBUG] RX Error detected: %b\n",
          error.rxError.asUInt
        )
      }
      when(error.txError =/= UartTxError.None) {
        printf(
          "[Uart.scala DEBUG] TX Error detected: %b\n",
          error.txError.asUInt
        )
      }
      when(error.topError =/= UartTopError.None) {
        printf(
          "[Uart.scala DEBUG] Top Error detected: %b\n",
          error.topError.asUInt
        )
      }
      when(error.addrDecodeError =/= AddrDecodeError.None) {
        printf(
          "[Uart.scala DEBUG] AddrDecode Error detected: %b\n",
          error.addrDecodeError.asUInt
        )
      }
    }

  }

  when(load) {
    load := false.B
  }
  when(txUpdateBaud) {
    txUpdateBaud := false.B
  }
  when(rxUpdateBaud) {
    rxUpdateBaud := false.B
  }
  when(clearError) {
    // Clear errors by connecting directly to None values
    clearError := false.B
    error.rxError := UartRxError.None
    error.txError := UartTxError.None
    error.topError := UartTopError.None

    // Print debug message
    if (uartParams.verbose) {
      printf("[Uart.scala DEBUG] Clearing error registers\n")
    }
  }

  when(rxFlush) {
    rxFlush := false.B
  }
  when(txFlush) {
    txFlush := false.B
  }

  // Collect code coverage points
  if (uartParams.coverage) {
    // Cover the entire IO bundle recursively.
    coverAll(io, "_io")
  }
}
