package tech.rocksavage.chiselware.uart

import tech.rocksavage.chiselware.uart.types.param.UartParams
import tech.rocksavage.traits.ModuleConfig

class UartConfig extends ModuleConfig {
  override def getDefaultConfigs: Map[String, Any] = Map(
    "9bits_2sync_900000baud" -> Seq(
      UartParams(
        addressWidth = 32,
        dataWidth = 32,
        wordWidth = 8,
        maxOutputBits = 9,
        syncDepth = 2,
        maxBaudRate = 921_600,
        maxClockFrequency = 1_000_000
      ),
      false
    ),
    "8bits_2sync_900000baud" -> Seq(
      UartParams(
        addressWidth = 32,
        dataWidth = 32,
        wordWidth = 8,
        maxOutputBits = 8,
        syncDepth = 2,
        maxBaudRate = 921_600,
        maxClockFrequency = 1_000_000
      ),
      false
    ),
    "9bits_32sync_900000baud" -> Seq(
      UartParams(
        addressWidth = 32,
        dataWidth = 32,
        wordWidth = 8,
        maxOutputBits = 9,
        syncDepth = 32,
        maxBaudRate = 921_600,
        maxClockFrequency = 1_000_000
      ),
      false
    ),
    "9bits_2sync_115000baud" -> Seq(
      UartParams(
        addressWidth = 32,
        dataWidth = 32,
        wordWidth = 8,
        maxOutputBits = 9,
        syncDepth = 2,
        maxBaudRate = 115200,
        maxClockFrequency = 1_000_000
      ),
      false
    ),
    "8bits_2sync_115000baud" -> Seq(
      UartParams(
        addressWidth = 32,
        dataWidth = 32,
        wordWidth = 8,
        maxOutputBits = 8,
        syncDepth = 2,
        maxBaudRate = 115200,
        maxClockFrequency = 50_000_000
      ),
      false
    )
  )
}
