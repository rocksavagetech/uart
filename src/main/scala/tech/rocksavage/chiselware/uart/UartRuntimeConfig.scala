package tech.rocksavage.chiselware.uart

case class UartRuntimeConfig(
    useAsserts: Boolean = true,
    baudRate: Int = 115_200,
    clockFrequency: Int = 25_000_000,
    numOutputBits: Int = 8,
    useParity: Boolean = false,
    parityOdd: Boolean = false,
    data: Int = 65
) {

    if (useAsserts) {
        require(baudRate > 0, "Baud rate must be greater than 0")
        require(clockFrequency > 0, "Clock frequency must be greater than 0")
        require(
          clockFrequency > baudRate,
          "Clock frequency must be greater than the baud rate"
        )
        require(
          numOutputBits >= 5,
          "Number of output bits must be greater than or equal to 5"
        )
        require(
          9 >= numOutputBits,
          "Number of output bits must be less than or equal to 9"
        )

        // data must be represented in numOutputBits
        require(data >= 0, "Data must be greater than or equal to 0")
        require(
          data < math.pow(2, numOutputBits),
          s"Data must be less than 2^${numOutputBits} (must be representable with the number of output bits)"
        )
    }

    override def toString =
        s"UartRuntimeConfig(useAsserts=$useAsserts, baudRate=$baudRate, clockFrequency=$clockFrequency, numOutputBits=$numOutputBits, useParity=$useParity, parityOdd=$parityOdd, data=$data)"
}
