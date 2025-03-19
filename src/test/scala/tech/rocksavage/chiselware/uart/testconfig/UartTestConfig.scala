package tech.rocksavage.chiselware.uart.testconfig

case class UartTestConfig(
    baudRate: Int = 115_200,
    clockFrequency: Int = 25_000_000,
    numOutputBits: Int = 8,
    useParity: Boolean = false,
    parityOdd: Boolean = false,
    fifoSize: Int = 16,
    almostFullLevel: Int = 14,
    almostEmptyLevel: Int = 2,
    lsbFirst: Boolean = false
)
