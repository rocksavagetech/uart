package tech.rocksavage.chiselware.uart.testconfig

class UartData(
    val data: Int,
    val direction: UartFifoDataDirection.UartFifoDataDirection
) {
    require(data >= 0, "Data must be greater than or equal to 0")
    override def toString = s"UartData(data=$data, direction=$direction)"
}
