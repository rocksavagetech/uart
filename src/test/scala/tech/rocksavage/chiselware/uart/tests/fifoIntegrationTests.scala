package tech.rocksavage.chiselware.uart

import chisel3._
import chiseltest._
import tech.rocksavage.chiselware.apb.ApbTestUtils._
import tech.rocksavage.chiselware.uart.param.UartParams
import tech.rocksavage.chiselware.apb.ApbBundle
import tech.rocksavage.chiselware.uart.error.UartRxError
import tech.rocksavage.chiselware.uart.error.UartTxError
import tech.rocksavage.chiselware.uart.testutils.UartRxTestUtils
import scala.util.control.Breaks._

object fifoIntegrationTests {
   /**
   * Test TX FIFO overflow condition by filling the FIFO beyond capacity
   */
  def txFifoOverflowTest(dut: Uart, params: UartParams): Unit = {
    implicit val clock = dut.clock
    clock.setTimeout(10000)
    
    println("Setting up UART for TX FIFO overflow test")
    setupUart(dut.io.apb, dut, 25000000, 115200)
    
    // Verify we start with no errors
    val initialError = readAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("error").get.U)
    println(s"Initial error status: $initialError")
    assert(initialError == 0, "Expected no errors at start of test")
    
    // Fill FIFO beyond capacity - the buffer size is params.bufferSize
    println(s"Attempting to fill TX FIFO beyond capacity (${params.bufferSize} entries)")
    for (i <- 0 until params.bufferSize + 2) {
      println(s"Writing TX data ${i+1}/${params.bufferSize + 2}")
      writeAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("tx_dataIn").get.U, ('A' + i).U)
      writeAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("tx_load").get.U, true.B)
      clock.step(1)
      
      // On overflow (after buffer size), we should see an error
      if (i >= params.bufferSize) {
        val errorStatus = readAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("error").get.U)
        val expectedBit = UartTxError.fifoOverflow.litValue
        val hasOverflowError = (errorStatus & expectedBit) != 0
        
        println(s"Error status after write ${i+1}: $errorStatus")
        println(s"TX FIFO overflow bit set: $hasOverflowError")
        
        assert(hasOverflowError, s"TX FIFO overflow error should be set but got error status $errorStatus")
      }
    }
    
    // Clear errors before proceeding
    writeAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("clearError").get.U, true.B)
    clock.step(2)
    
    val finalError = readAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("error").get.U)
    println(s"Final error status after clear: $finalError")
    assert(finalError == 0, "Error should be cleared after clearError")
  }
  
  /**
   * Test RX FIFO overflow condition by filling the FIFO beyond capacity
   */
  def rxFifoOverflowTest(dut: Uart, params: UartParams): Unit = {
    import scala.util.control.Breaks._
    
    implicit val clock = dut.clock
    clock.setTimeout(10000)
    
    val clockFreq = 25000000
    val baudRate = 115200
    val clocksPerBit = clockFreq / baudRate
    
    println("Setting up UART for RX FIFO overflow test")
    setupUart(dut.io.apb, dut, clockFreq, baudRate)
    
    // Verify we start with no errors
    val initialError = readAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("error").get.U)
    println(s"Initial error status: $initialError")
    assert(initialError == 0, "Expected no errors at start of test")
    
    // Fill RX FIFO by sending characters without reading them
    println(s"Filling RX FIFO beyond capacity (${params.bufferSize} entries)")
    
    // Send one more character than the buffer size
    for (i <- 0 until params.bufferSize + 1) {
      val char = ('A' + i).toChar
      println(s"Sending character $char (${i+1}/${params.bufferSize + 1})")
      
      // Simulate receiving a character by directly manipulating the RX line
      simulateCharacterReceive(dut, char, clocksPerBit)
      
      // Wait for reception to complete
      clock.step(clocksPerBit * 12)
      
      // Check for data available
      val dataAvailable = readAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("rx_dataAvailable").get.U)
      println(s"RX data available: $dataAvailable")
      
      // If we've exceeded the buffer size, check for overflow error
      if (i >= params.bufferSize) {
        val errorStatus = readAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("error").get.U)
        val expectedBit = UartRxError.fifoOverflow.litValue
        val hasOverflowError = (errorStatus & expectedBit) != 0
        
        println(s"Error status after reception ${i+1}: $errorStatus")
        println(s"RX FIFO overflow bit set: $hasOverflowError")
        
        assert(hasOverflowError, s"RX FIFO overflow error should be set but got error status $errorStatus")
      }
    }
    
    // Clear errors
    writeAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("clearError").get.U, true.B)
    clock.step(2)
    
    // Now read data from the FIFO
    println("Reading data from RX FIFO")
    breakable {
      for (i <- 0 until params.bufferSize) {
        val dataAvailable = readAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("rx_dataAvailable").get.U)
        if (dataAvailable != 0) {
          val rxData = readAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("rx_data").get.U)
          println(s"Read data from RX FIFO: ${rxData.toChar}")
        } else {
          println(s"No more data available after reading $i items")
          break
        }
      }
    }
  }
  
  /**
   * Helper function to simulate receiving a character on a UART instance
   */
  private def simulateCharacterReceive(dut: Uart, char: Char, clocksPerBit: Int)(implicit clock: Clock): Unit = {
    // Start bit (low)
    dut.io.rx.poke(false.B)
    clock.step(clocksPerBit)
    
    // Data bits (LSB first)
    val bits = char.toInt.toBinaryString.reverse.padTo(8, '0')
    for (bit <- bits) {
      dut.io.rx.poke((bit == '1').B)
      clock.step(clocksPerBit)
    }
    
    // Stop bit (high)
    dut.io.rx.poke(true.B)
    clock.step(clocksPerBit)
  }
  
  /**
   * Test TX FIFO underflow condition by trying to transmit from empty FIFO
   */
  def txFifoUnderflowTest(dut: Uart, params: UartParams): Unit = {
    implicit val clock = dut.clock
    clock.setTimeout(10000)
    
    println("Setting up UART for TX FIFO underflow test")
    setupUart(dut.io.apb, dut, 25000000, 115200)
    
    // Verify we start with no errors
    val initialError = readAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("error").get.U)
    println(s"Initial error status: $initialError")
    assert(initialError == 0, "Expected no errors at start of test")
    
    // Try to trigger a load operation when FIFO is empty
    println("Attempting to load data from empty TX FIFO")
    writeAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("tx_load").get.U, true.B)
    clock.step(1)
    
    // Check for underflow error
    val errorStatus = readAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("error").get.U)
    val expectedBit = UartTxError.fifoUnderflow.litValue
    val hasUnderflowError = (errorStatus & expectedBit) != 0
    
    println(s"Error status after attempting to load from empty FIFO: $errorStatus")
    println(s"TX FIFO underflow bit set: $hasUnderflowError")
    
    assert(hasUnderflowError, s"TX FIFO underflow error should be set but got error status $errorStatus")
    
    // Clear errors
    writeAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("clearError").get.U, true.B)
    clock.step(2)
    
    val finalError = readAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("error").get.U)
    println(s"Final error status after clear: $finalError")
    assert(finalError == 0, "Error should be cleared after clearError")
  }
  
  /**
   * Test RX FIFO underflow condition by trying to read from empty FIFO
   */
  def rxFifoUnderflowTest(dut: Uart, params: UartParams): Unit = {
    implicit val clock = dut.clock
    clock.setTimeout(10000)
    
    println("Setting up UART for RX FIFO underflow test")
    setupUart(dut.io.apb, dut, 25000000, 115200)
    
    // Verify we start with no errors
    val initialError = readAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("error").get.U)
    println(s"Initial error status: $initialError")
    assert(initialError == 0, "Expected no errors at start of test")
    
    // Make sure RX FIFO is empty
    val dataAvailable = readAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("rx_dataAvailable").get.U)
    assert(dataAvailable == 0, "RX FIFO should be empty at start of test")
    
    // Try to read from empty FIFO
    println("Attempting to read from empty RX FIFO")
    val rxData = readAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("rx_data").get.U)
    println(s"Read data (should be 0): $rxData")
    
    // Check for underflow error
    val errorStatus = readAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("error").get.U)
    val expectedBit = UartRxError.fifoUnderflow.litValue
    val hasUnderflowError = (errorStatus & expectedBit) != 0
    
    println(s"Error status after reading from empty FIFO: $errorStatus")
    println(s"RX FIFO underflow bit set: $hasUnderflowError")
    
    assert(hasUnderflowError, s"RX FIFO underflow error should be set but got error status $errorStatus")
    
    // Clear errors
    writeAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("clearError").get.U, true.B)
    clock.step(2)
    
    val finalError = readAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("error").get.U)
    println(s"Final error status after clear: $finalError")
    assert(finalError == 0, "Error should be cleared after clearError")
  }
  
  /**
   * Test error clearing functionality for multiple FIFO errors
   */
  def errorClearingTest(dut: Uart, params: UartParams): Unit = {
    implicit val clock = dut.clock
    clock.setTimeout(10000)
    
    println("Setting up UART for error clearing test")
    setupUart(dut.io.apb, dut, 25000000, 115200)
    
    // Step 1: Generate TX FIFO overflow
    println("Generating TX FIFO overflow")
    for (i <- 0 until params.bufferSize + 1) {
      writeAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("tx_dataIn").get.U, ('A' + i).U)
      writeAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("tx_load").get.U, true.B)
      clock.step(1)
    }
    
    // Verify TX overflow error is set
    val txErrorStatus = readAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("error").get.U)
    val txOverflowSet = (txErrorStatus & UartTxError.fifoOverflow.litValue) != 0
    println(s"TX FIFO overflow error set: $txOverflowSet (error=$txErrorStatus)")
    assert(txOverflowSet, "TX FIFO overflow error should be set")
    
    // Step 2: Clear only this error
    writeAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("clearError").get.U, true.B)
    clock.step(2)
    
    // Verify error is cleared
    val clearedStatus = readAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("error").get.U)
    println(s"Error status after clear: $clearedStatus")
    assert(clearedStatus == 0, "All errors should be cleared")
    
    // Step 3: Generate both TX and RX errors simultaneously
    println("Generating both TX and RX FIFO errors")
    
    // Generate TX overflow again
    for (i <- 0 until params.bufferSize + 1) {
      writeAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("tx_dataIn").get.U, ('A' + i).U)
      writeAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("tx_load").get.U, true.B)
      clock.step(1)
    }
    
    // Generate RX underflow (read from empty FIFO)
    readAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("rx_data").get.U)
    
    // Verify both errors are set
    val combinedErrorStatus = readAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("error").get.U)
    val txErrorBit = UartTxError.fifoOverflow.litValue
    val rxErrorBit = UartRxError.fifoUnderflow.litValue
    
    val hasTxError = (combinedErrorStatus & txErrorBit) != 0
    val hasRxError = (combinedErrorStatus & rxErrorBit) != 0
    
    println(s"Combined error status: $combinedErrorStatus")
    println(s"TX overflow set: $hasTxError, RX underflow set: $hasRxError")
    
    assert(hasTxError && hasRxError, 
      s"Both TX and RX errors should be set but got error status $combinedErrorStatus")
    
    // Step 4: Clear all errors at once
    writeAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("clearError").get.U, true.B)
    clock.step(2)
    
    // Verify all errors are cleared
    val finalErrorStatus = readAPB(dut.io.apb, dut.registerMap.getAddressOfRegister("error").get.U)
    println(s"Final error status after clearing multiple errors: $finalErrorStatus")
    assert(finalErrorStatus == 0, "All errors should be cleared with a single clearError operation")
  }
  
  /**
   * Setup function to configure UART for testing
   */
  private def setupUart(apb: ApbBundle, uart: Uart, clockFreq: Int, baudRate: Int)(implicit clock: Clock): Unit = {
    println(s"Setting up UART with clockFreq=$clockFreq, baudRate=$baudRate")
    
    // Configure TX
    writeAPB(apb, uart.registerMap.getAddressOfRegister("tx_clockFreq").get.U, clockFreq.U)
    writeAPB(apb, uart.registerMap.getAddressOfRegister("tx_baudRate").get.U, baudRate.U)
    writeAPB(apb, uart.registerMap.getAddressOfRegister("tx_updateBaud").get.U, true.B)
    writeAPB(apb, uart.registerMap.getAddressOfRegister("tx_numOutputBitsDb").get.U, 8.U)
    writeAPB(apb, uart.registerMap.getAddressOfRegister("tx_useParityDb").get.U, false.B)
    
    // Configure RX
    writeAPB(apb, uart.registerMap.getAddressOfRegister("rx_clockFreq").get.U, clockFreq.U)
    writeAPB(apb, uart.registerMap.getAddressOfRegister("rx_baudRate").get.U, baudRate.U)
    writeAPB(apb, uart.registerMap.getAddressOfRegister("rx_updateBaud").get.U, true.B)
    writeAPB(apb, uart.registerMap.getAddressOfRegister("rx_numOutputBitsDb").get.U, 8.U)
    writeAPB(apb, uart.registerMap.getAddressOfRegister("rx_useParityDb").get.U, false.B)
    
    // Wait for baud rate generators to settle
    clock.step(40)
    
    // Clear any errors
    writeAPB(apb, uart.registerMap.getAddressOfRegister("clearError").get.U, true.B)
    clock.step(2)
    
    println("UART setup complete")
  }
}