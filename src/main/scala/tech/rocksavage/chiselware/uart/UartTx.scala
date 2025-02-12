// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)
package tech.rocksavage.chiselware.uart

import chisel3._
import chisel3.util._
import tech.rocksavage.chiselware.uart.bundle.UartTxBundle
import tech.rocksavage.chiselware.uart.param.UartParams

/** A UART transmitter module that handles the transmission of UART data.
  * @param params
  *   Configuration parameters for the UART.
  * @param formal
  *   A boolean value to enable formal verification.
  */
class UartTx(params: UartParams, formal: Boolean = true) extends Module {
  val io = IO(new UartTxBundle(params))

  // ###################
  // Internal Registers
  // ###################
  // Current state of the UART FSM
  val stateReg = RegInit(UartState.Idle)
  // Bit counter for transmitted bits
  val bitCounterReg = RegInit(0.U((log2Ceil(params.dataWidth) + 1).W))
  // Clock counter for timing
  val clockCounterReg = RegInit(0.U((log2Ceil(params.maxClocksPerBit) + 1).W))
  // Next values for the FSM registers
  val stateNext = WireInit(UartState.Idle)
  val bitCounterNext = WireInit(0.U((log2Ceil(params.dataWidth) + 1).W))
  val clockCounterNext = WireInit(
    0.U((log2Ceil(params.maxClocksPerBit) + 1).W)
  )

  stateReg := stateNext
  bitCounterReg := bitCounterNext
  clockCounterReg := clockCounterNext

  // ###################
  // Input Control State Registers
  // ###################
  // These registers hold the configuration settings that might be loaded
  // when a new transmission is initiated.
  val clocksPerBitReg = RegInit(0.U((log2Ceil(params.maxClocksPerBit) + 1).W))
  val numOutputBitsReg = RegInit(0.U((log2Ceil(params.maxOutputBits) + 1).W))
  val useParityReg = RegInit(false.B)
  val clocksPerBitDbReg = RegInit(
    0.U((log2Ceil(params.maxClocksPerBit) + 1).W)
  )
  val numOutputBitsDbReg = RegInit(
    0.U((log2Ceil(params.maxOutputBits) + 1).W)
  )
  val useParityDbReg = RegInit(false.B)

  val clocksPerBitNext = WireInit(
    0.U((log2Ceil(params.maxClocksPerBit) + 1).W)
  )
  val numOutputBitsNext = WireInit(
    0.U((log2Ceil(params.maxOutputBits) + 1).W)
  )
  val useParityNext = WireInit(false.B)

  clocksPerBitReg := clocksPerBitNext
  numOutputBitsReg := numOutputBitsNext
  useParityReg := useParityNext
  clocksPerBitDbReg := io.clocksPerBitDb
  numOutputBitsDbReg := io.numOutputBitsDb
  useParityDbReg := io.useParityDb

  // ###################
  // Shift Register for Storing Data to Transmit
  // ###################
  // In the idle state, when a new transmission is requested (via io.load),
  // the transmit data is loaded into this register.
  val dataShiftReg = RegInit(0.U(params.maxOutputBits.W))
  val dataShiftNext = WireInit(0.U(params.maxOutputBits.W))
  dataShiftReg := dataShiftNext

  // ###################
  // Output Register
  // ###################
  // The TX output should be high (logic 1) in idle.
//  val txReg = RegInit(true.B)
  val txNext = WireInit(true.B)
  txNext := calculateTxOut(stateReg, dataShiftReg)
//  txReg := txNext
//  io.tx := txReg
  io.tx := txNext

  // ###################
  // Finite State Machine (FSM)
  // ###################
  // When drive a transmission, the following sequence occurs:
  //  - Idle: Waiting for a new transmission. When io.load is high, the module loads
  //          data from io.data into the shift register and captures configuration parameters.
  //  - Start: Generates the start bit (logic 0) for one bit period.
  //  - Data:  Shifts out each data bit (LSB first) according to the clocksPerBit timing.
  //  - Stop:  Generates the stop bit (logic 1) for one bit period, then returns to Idle.
  stateNext := calculateStateNext(
    stateReg,
    io.load,
    clockCounterReg,
    clocksPerBitReg,
    bitCounterReg,
    numOutputBitsReg
  )
  bitCounterNext := calculateBitCounterNext(
    stateReg,
    clockCounterReg,
    clocksPerBitReg,
    bitCounterReg,
    numOutputBitsReg
  )
  clockCounterNext := calculateClockCounterNext(
    stateReg,
    clockCounterReg,
    clocksPerBitReg
  )
  clocksPerBitNext := calculateClocksPerBitNext(
    stateReg,
    clocksPerBitReg,
    clocksPerBitDbReg,
    io.load
  )
  numOutputBitsNext := calculateNumOutputBitsNext(
    stateReg,
    numOutputBitsReg,
    numOutputBitsDbReg,
    io.load
  )
  useParityNext := calculateUseParityNext(
    stateReg,
    useParityReg,
    useParityDbReg,
    io.load
  )
  dataShiftNext := calculateDataShiftNext(
    stateReg,
    clockCounterReg,
    clocksPerBitReg,
    dataShiftReg,
    io.data,
    io.load
  )

  // ###################
  // Functions for FSM and Data Handling
  // ###################
  def calculateStateNext(
      stateReg: UartState.Type,
      load: Bool,
      clockCounterReg: UInt,
      clocksPerBitReg: UInt,
      bitCounterReg: UInt,
      numOutputBitsReg: UInt
  ): UartState.Type = {
    val stateNext = WireInit(stateReg)
    switch(stateReg) {
      is(UartState.Idle) {
        when(load) {
          stateNext := UartState.Start
        }
      }
      is(UartState.Start) {
        when(clockCounterReg === clocksPerBitReg) {
          stateNext := UartState.Data
        }
      }
      is(UartState.Data) {
        when(clockCounterReg === clocksPerBitReg - 1.U) {
          when(bitCounterReg === numOutputBitsReg - 1.U) {
            stateNext := UartState.Stop
          }.otherwise {
            stateNext := UartState.Data
          }
        }
      }
      is(UartState.Stop) {
        when(clockCounterReg === clocksPerBitReg) {
          stateNext := UartState.Idle
        }
      }
    }
    stateNext
  }

  def calculateBitCounterNext(
      stateReg: UartState.Type,
      clockCounterReg: UInt,
      clocksPerBitReg: UInt,
      bitCounterReg: UInt,
      numOutputBitsReg: UInt
  ): UInt = {
    val bitCounterNext = WireInit(bitCounterReg)
    switch(stateReg) {
      is(UartState.Idle) {
        bitCounterNext := 0.U
      }
      is(UartState.Data) {
        when(clockCounterReg === clocksPerBitReg) {
          when(bitCounterReg =/= numOutputBitsReg) {
            bitCounterNext := bitCounterReg + 1.U
          }.otherwise {
            bitCounterNext := 0.U
          }
        }
      }
      is(UartState.Stop) {
        bitCounterNext := 0.U
      }
    }
    bitCounterNext
  }

  def calculateClockCounterNext(
      stateReg: UartState.Type,
      clockCounterReg: UInt,
      clocksPerBitReg: UInt
  ): UInt = {
    val clockCounterNext = WireInit(clockCounterReg)
    switch(stateReg) {
      is(UartState.Idle) {
        clockCounterNext := 0.U
      }
      is(UartState.Start, UartState.Data, UartState.Stop) {
        when(clockCounterReg === clocksPerBitReg) {
          clockCounterNext := 0.U
        }.otherwise {
          clockCounterNext := clockCounterReg + 1.U
        }
      }
    }
    clockCounterNext
  }

  // When the module is idle and a new transmission is loaded, latch in the debug
  // parameters so that the transmission can use the new configurations.
  def calculateClocksPerBitNext(
      stateReg: UartState.Type,
      clocksPerBitReg: UInt,
      clocksPerBitDbReg: UInt,
      load: Bool
  ): UInt = {
    val clocksPerBitNext = WireInit(clocksPerBitReg)
    when(stateReg === UartState.Idle && load) {
      clocksPerBitNext := clocksPerBitDbReg
    }
    clocksPerBitNext
  }

  def calculateNumOutputBitsNext(
      stateReg: UartState.Type,
      numOutputBitsReg: UInt,
      numOutputBitsDbReg: UInt,
      load: Bool
  ): UInt = {
    val numOutputBitsNext = WireInit(numOutputBitsReg)
    when(stateReg === UartState.Idle && load) {
      numOutputBitsNext := numOutputBitsDbReg
    }
    numOutputBitsNext
  }

  def calculateUseParityNext(
      stateReg: UartState.Type,
      useParityReg: Bool,
      useParityDbReg: Bool,
      load: Bool
  ): Bool = {
    val useParityNext = WireInit(useParityReg)
    when(stateReg === UartState.Idle && load) {
      useParityNext := useParityDbReg
    }
    useParityNext
  }

  // For the data shift register:
  //   - In Idle, when a new transmission is requested (load is high), the incoming
  //     parallel data (io.data) is loaded into the shift register.
  //   - In the Data state, once a bit period is complete, shift the register right (LSB first).
  def calculateDataShiftNext(
      stateReg: UartState.Type,
      clockCounterReg: UInt,
      clocksPerBitReg: UInt,
      dataShiftReg: UInt,
      loadData: UInt,
      load: Bool
  ): UInt = {
    val dataShiftNext = WireInit(dataShiftReg)
    switch(stateReg) {
      is(UartState.Idle) {
        when(load) {
          // Load new data when starting a transmission.
          dataShiftNext := loadData
        }
      }
      is(UartState.Data) {
        when(clockCounterReg === clocksPerBitReg - 1.U) {
          dataShiftNext := Cat(0.U(1.W), dataShiftReg >> 1)

        }
      }
      is(UartState.Stop) {
        // In the stop phase, the data shift register isn’t updated.
        dataShiftNext := dataShiftReg
      }
    }
    dataShiftNext
  }

  // The TX output is driven based on the current FSM state:
  //   - In Idle and Stop, the output is logic high.
  //   - In Start, the output is logic low (start bit).
  //   - In Data, the output is the LSB of the shift register.
  def calculateTxOut(
      stateReg: UartState.Type,
      dataShiftReg: UInt
  ): Bool = {
    val txOut = WireInit(true.B)
    switch(stateReg) {
      is(UartState.Idle) {
        txOut := true.B
      }
      is(UartState.Start) {
        txOut := false.B
      }
      is(UartState.Data) {
        txOut := dataShiftReg(0)
      }
      is(UartState.Stop) {
        txOut := true.B
      }
    }
    txOut
  }
}
