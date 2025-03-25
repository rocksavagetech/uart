package tech.rocksavage.chiselware.uart.testconfig

object UartFifoDataDirection extends Enumeration {
    type UartFifoDataDirection = Value
    val Push, Pop, Flush = Value
}
