package tech.rocksavage.chiselware.timer

import tech.rocksavage.chiselware.timer.param.TimerParams
import tech.rocksavage.traits.ModuleConfig

class TimerConfig extends ModuleConfig {
  override def getDefaultConfigs: Map[String, Any] = Map(
    "8_8_8" -> TimerParams(dataWidth = 8, addressWidth = 8, countWidth = 8),
    "16_16_16" -> TimerParams(
      dataWidth = 16,
      addressWidth = 16,
      countWidth = 16
    ),
    "32_32_32" -> TimerParams(
      dataWidth = 32,
      addressWidth = 32,
      countWidth = 32
    ),
    "64_64_64" -> TimerParams(
      dataWidth = 64,
      addressWidth = 64,
      countWidth = 64
    )
  )
}
