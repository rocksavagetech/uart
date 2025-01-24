# Uart Module

## Setup

### Git 

```bash
git clone [url].git
git submodule update --init --recursive
touch .git-blame-ignore-revs
git config blame.ignoreRevsFile .git-blame-ignore-revs
``` 

## Overview

The `Timer` module is a configurable hardware timer designed for use in embedded systems and other applications requiring precise timing control. Implemented in Chisel, a hardware design language, the module offers a flexible and scalable solution for managing timers with features such as PWM (Pulse Width Modulation), interrupt generation, and configurable count widths. The module is designed to integrate seamlessly with APB (Advanced Peripheral Bus) interfaces, making it suitable for memory-mapped I/O operations.

## Features

- **Configurable Timer Parameters**: Define data width, address width, and count width to suit your application.
- **PWM Generation**: Generate PWM signals with configurable duty cycles.
- **Interrupt Support**: Generate interrupts when the timer reaches its maximum count.
- **APB Integration**: Easily integrate with APB interfaces for memory-mapped I/O.
- **Formal Verification Support**: Enable formal verification to ensure the correctness of the timer logic.

## Run Directions

To run this project, use the following commands:

### Serialize to Verilog
```bash
sbt "runMain tech.rocksavage.Main verilog --mode print --module tech.rocksavage.chiselware.timer.Timer  --config-class tech.rocksavage.chiselware.timer.TimerConfig"
```

### Synthesis
```bash
sbt "runMain tech.rocksavage.Main synth --module tech.rocksavage.chiselware.timer.Timer --techlib synth/stdcells.lib  --config-class tech.rocksavage.chiselware.timer.TimerConfig"
```

The results are written to the `./out/synth/$config` directories for each configuration.

### Sta
```bash
sbt "runMain tech.rocksavage.Main sta --module tech.rocksavage.chiselware.timer.Timer --techlib synth/stdcells.lib  --config-class tech.rocksavage.chiselware.timer.TimerConfig --clock-period 5.0"
```

The results are written to the `./out/sta/$config` directories for each configuration.

## Usage

### Defining Timer Parameters

To configure the timer, create an instance of `TimerParams` with the desired data width, address width, and count width. These parameters define the size of the registers and the range of the timer.

```scala
val timerParams = TimerParams(
  dataWidth = 32,
  addressWidth = 32,
  countWidth = 32
)
```

### Instantiating the Timer Module

Instantiate the `Timer` module with the defined parameters. The module will automatically configure the timer registers and handle the timing logic.

```scala
val timer = Module(new Timer(timerParams))
```

### Connecting Inputs and Outputs

Connect the APB interface, timer outputs, and interrupt signals to the `Timer` module. The module will manage the timer logic and generate the appropriate outputs.

```scala
timer.io.apb <> io.apb
timer.io.timerOutput <> io.timerOutput
timer.io.interrupt <> io.interrupt
```

### Configuring Timer Registers

The `Timer` module provides several configurable registers, including:

- **Enable (`en`)**: Enable or disable the timer.
- **Prescaler (`prescaler`)**: Divide the clock frequency to control the timer speed.
- **Max Count (`maxCount`)**: Define the maximum count value before the timer resets.
- **PWM Ceiling (`pwmCeiling`)**: Control the duty cycle of the PWM signal.
- **Set Count Value (`setCountValue`)**: Set the counter to a specific value.
- **Set Count (`setCount`)**: Signal to set the counter to `setCountValue`.

These registers can be accessed via the APB interface.

### Handling Interrupts

The `Timer` module generates an interrupt when the timer reaches its maximum count. The interrupt signal can be used to trigger other actions in your design.

```scala
when(timer.io.interrupt.interrupt === TimerInterruptEnum.MaxReached) {
  // Handle timer interrupt
}
```

### Formal Verification

Enable formal verification by setting the `formal` parameter to `true` when instantiating the `TimerInner` module. This will add assertions to verify the correctness of the timer logic.

```scala
val timerInner = Module(new TimerInner(timerParams, formal = true))
```

## Example

The following example demonstrates how to use the `Timer` module in a design with an APB interface.

```scala
class TimerTop(val timerParams: TimerParams) extends Module {
  val io = IO(new Bundle {
    val apb = new ApbBundle(ApbParams(timerParams.dataWidth, timerParams.addressWidth))
    val timerOutput = new TimerOutputBundle(timerParams)
    val interrupt = new TimerInterruptBundle
  })

  // Instantiate the Timer module
  val timer = Module(new Timer(timerParams))
  timer.io.apb <> io.apb
  timer.io.timerOutput <> io.timerOutput
  timer.io.interrupt <> io.interrupt

  // Handle APB writes to timer registers
  when(io.apb.PSEL && io.apb.PENABLE && io.apb.PWRITE) {
    // Write to the appropriate timer register based on the address
  }

  // Handle APB reads from timer registers
  when(io.apb.PSEL && io.apb.PENABLE && !io.apb.PWRITE) {
    // Read from the appropriate timer register based on the address
  }

  // Handle timer interrupts
  when(timer.io.interrupt.interrupt === TimerInterruptEnum.MaxReached) {
    // Handle timer interrupt
  }
}
```

## Conclusion

The `Timer` module is a versatile and configurable solution for managing timers in Chisel-based hardware designs. With features such as PWM generation, interrupt support, and seamless APB integration, the module is well-suited for a wide range of applications requiring precise timing control. The inclusion of formal verification support ensures the correctness and reliability of the timer logic, making it a robust choice for complex systems.
