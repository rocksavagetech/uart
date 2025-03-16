package tech.rocksavage.chiselware.uart

// enum class for data, can be push(data) or pop(data), must contain data
object UartFifoDataDirection extends Enumeration {
    type UartFifoDataDirection = Value
    val Push, Pop = Value
}

class UartData(
    val data: Int,
    val direction: UartFifoDataDirection.UartFifoDataDirection
) {
    require(data >= 0, "Data must be greater than or equal to 0")
    override def toString = s"UartData(data=$data, direction=$direction)"
}

case class UartTestConfig(
    baudRate: Int = 115_200,
    clockFrequency: Int = 25_000_000,
    numOutputBits: Int = 8,
    useParity: Boolean = false,
    parityOdd: Boolean = false,
    fifoSize: Int = 16
)

case class UartFifoTxRuntimeConfig(
    useAsserts: Boolean = true,
    config: UartTestConfig = UartTestConfig(),
    data: Seq[UartData] = Seq(new UartData(65, UartFifoDataDirection.Push))
) {

    if (useAsserts) {
        require(config.baudRate > 0, "Baud rate must be greater than 0")
        require(
          config.clockFrequency > 0,
          "Clock frequency must be greater than 0"
        )
        require(
          config.clockFrequency >= config.baudRate,
          "Clock frequency must be greater than or equal to the baud rate"
        )
        require(
          config.numOutputBits >= 5,
          "Number of output bits must be greater than or equal to 5"
        )
        require(
          9 >= config.numOutputBits,
          "Number of output bits must be less than or equal to 9"
        )

        // data must be represented in numOutputBits
        for (d <- data) {
            require(d.data >= 0, "Data must be greater than or equal to 0")
            require(
              d.data < math.pow(2, config.numOutputBits),
              s"Data must be less than 2^${config.numOutputBits} (must be representable with the number of output bits)"
            )
        }

        var fifoHeight = 0
        for (d <- data) {
            d.direction match {
                case UartFifoDataDirection.Push => fifoHeight += 1
                case UartFifoDataDirection.Pop  => fifoHeight = 0
            }
            require(
              fifoHeight >= 0,
              "Fifo height must be greater than or equal to 0"
            )
            require(
              fifoHeight <= config.fifoSize,
              "Fifo height must be less than or equal to fifo size"
            )
        }
    }

    override def toString =
        s"UartFifoTxRuntimeConfig(useAsserts=$useAsserts, config=$config, data=${data.toString()})"
}

case class UartFifoRxRuntimeConfig(
    useAsserts: Boolean = true,
    config: UartTestConfig = UartTestConfig(),
    data: Seq[UartData] = Seq(new UartData(65, UartFifoDataDirection.Push))
) {

    if (useAsserts) {
        require(config.baudRate > 0, "Baud rate must be greater than 0")
        require(
          config.clockFrequency > 0,
          "Clock frequency must be greater than 0"
        )
        require(
          config.clockFrequency >= config.baudRate,
          "Clock frequency must be greater than or equal to the baud rate"
        )
        require(
          config.numOutputBits >= 5,
          "Number of output bits must be greater than or equal to 5"
        )
        require(
          9 >= config.numOutputBits,
          "Number of output bits must be less than or equal to 9"
        )

        // data must be represented in numOutputBits
        for (d <- data) {
            require(d.data >= 0, "Data must be greater than or equal to 0")
            require(
              d.data < math.pow(2, config.numOutputBits),
              s"Data must be less than 2^${config.numOutputBits} (must be representable with the number of output bits)"
            )
        }

        var fifoHeight = 0
        for (d <- data) {
            d.direction match {
                case UartFifoDataDirection.Push => fifoHeight += 1
                case UartFifoDataDirection.Pop  => fifoHeight -= 1
            }
            require(
              fifoHeight >= 0,
              "Fifo height must be greater than or equal to 0"
            )
            require(
              fifoHeight <= config.fifoSize,
              "Fifo height must be less than or equal to fifo size"
            )
        }
    }

    override def toString =
        s"UartFifoRxRuntimeConfig(useAsserts=$useAsserts, config=$config, data=${data.toString()})"
}
