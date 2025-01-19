//// (c) 2024 Rocksavage Technology, Inc.
//// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)
//package tech.rocksavage.chiselware.uart
//
//import chisel3._
//import tech.rocksavage.chiselware.addrdecode.{AddrDecode, AddrDecodeError}
//import tech.rocksavage.chiselware.addressable.RegisterMap
//import tech.rocksavage.chiselware.apb.{ApbBundle, ApbParams}
//import tech.rocksavage.chiselware.uart.param.UartParams
//
//class Uart(val params: UartParams) extends Module {
//
//  val dataWidth    = params.dataWidth
//  val addressWidth = params.addressWidth
//
//  // Input/Output bundle for the Timer module
//  val io = IO(new Bundle {
//    val apb         = new ApbBundle(ApbParams(dataWidth, addressWidth))
//    val timerOutput = new TimerOutputBundle(timerParams)
//    val interrupt   = new TimerInterruptBundle
//  })
//
//  // Create a RegisterMap to manage the addressable registers
//  val registerMap = new RegisterMap(dataWidth, addressWidth)
//
//  // Now define your registers without the macro
//  val en: Bool = RegInit(false.B)
//  registerMap.createAddressableRegister(en, "en")
//
//  val prescaler: UInt = RegInit(0.U(timerParams.countWidth.W))
//  registerMap.createAddressableRegister(prescaler, "prescaler")
//
//  val maxCount: UInt = RegInit(0.U(timerParams.countWidth.W))
//  registerMap.createAddressableRegister(maxCount, "maxCount")
//
//  val pwmCeiling: UInt = RegInit(0.U(timerParams.countWidth.W))
//  registerMap.createAddressableRegister(pwmCeiling, "pwmCeiling")
//
//  val setCountValue: UInt = RegInit(0.U(timerParams.countWidth.W))
//  registerMap.createAddressableRegister(setCountValue, "setCountValue")
//
//  val setCount: Bool = RegInit(false.B)
//  registerMap.createAddressableRegister(setCount, "setCount")
//
//  // Generate AddrDecode
//  val addrDecodeParams = registerMap.getAddrDecodeParams
//  val addrDecode       = Module(new AddrDecode(addrDecodeParams))
//  addrDecode.io.addr       := io.apb.PADDR
//  addrDecode.io.addrOffset := 0.U
//  addrDecode.io.en         := true.B
//  addrDecode.io.selInput   := true.B
//
//  io.apb.PREADY := (io.apb.PENABLE && io.apb.PSEL)
//  io.apb.PSLVERR := addrDecode.io.errorCode === AddrDecodeError.AddressOutOfRange
//
//  io.apb.PRDATA := 0.U
//  // Control Register Read/Write
//  when(io.apb.PSEL && io.apb.PENABLE) {
//    when(io.apb.PWRITE) {
//      for (reg <- registerMap.getRegisters) {
//        when(addrDecode.io.sel(reg.id)) {
//          reg.writeCallback(addrDecode.io.addrOffset, io.apb.PWDATA)
//        }
//      }
//    }.otherwise {
//      for (reg <- registerMap.getRegisters) {
//        when(addrDecode.io.sel(reg.id)) {
//          io.apb.PRDATA := reg.readCallback(addrDecode.io.addrOffset)
//        }
//      }
//    }
//  }
//
//  // Instantiate the TimerInner module
//  val timerInner = Module(new TimerInner(timerParams))
//  timerInner.io.timerInputBundle.en            := en // Add this line
//  timerInner.io.timerInputBundle.setCount      := setCount
//  timerInner.io.timerInputBundle.prescaler     := prescaler
//  timerInner.io.timerInputBundle.maxCount      := maxCount
//  timerInner.io.timerInputBundle.pwmCeiling    := pwmCeiling
//  timerInner.io.timerInputBundle.setCountValue := setCountValue
//  // Connect the TimerInner outputs to the top-level outputs
//  io.timerOutput <> timerInner.io.timerOutputBundle
//  // Handle interrupts
//  io.interrupt.interrupt := TimerInterruptEnum.None
//  when(timerInner.io.timerOutputBundle.maxReached) {
//    io.interrupt.interrupt := TimerInterruptEnum.MaxReached
//  }
//}
