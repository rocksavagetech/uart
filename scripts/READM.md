# Verilog Generation Tools

This set of tools helps you generate and manage Verilog files from your Chisel project.

## Setup

Run the setup script to create necessary directories and tools:

```bash
# Make the setup script executable
chmod +x scripts/setup_verilog_tools.sh

# Run the setup script
./scripts/setup_verilog_tools.sh
```

## Usage

### Generating Verilog

The improved Makefile target splits the generated Verilog into individual module files:

```bash
make verilog
```

This will:
1. Generate the combined Verilog using SBT
2. Extract the Verilog content (skipping SBT output)
3. Split the file into individual module files in the `generated/` directory
4. Create a manifest of all modules

### Browsing Generated Modules

To browse and view all generated modules:

```bash
python bin/view_modules.py
```

This launches a simple terminal UI for navigating the modules:
- Use arrow keys to navigate
- Press Enter to view a module
- Press Escape to return to the module list
- Press 'q' to quit

### Viewing a Specific Module

To quickly view a specific module:

```bash
bin/view_module.sh <module_name>
```

For example:
```bash
bin/view_module.sh Uart      # View the top-level Uart module
bin/view_module.sh UartFsm   # View the UartFsm module
```

## Generated Files

After running `make verilog`, you'll have the following files:

- `generated/<ModuleName>.v` - Individual Verilog module files
- `generated/combined_uart.v` - The original combined file
- `generated/all_modules.v` - A file with `include` statements for all modules
- `generated/modules_manifest.txt` - A list of all generated modules
- `generated/module_hierarchy.txt` - The module hierarchy showing dependencies

## For Synthesis

When using the generated files for synthesis:

1. **Option 1:** Use individual module files for targeted synthesis
2. **Option 2:** Use `all_modules.v` to include everything at once
3. **Option 3:** Use `combined_uart.v` if your tool works better with a single file

The module hierarchy information can help you understand the dependencies between modules.

## Troubleshooting

If you encounter any issues:

1. Make sure all scripts are executable (`chmod +x scripts/*.py bin/*.py bin/*.sh`)
2. Verify that Python 3 is installed on your system
3. Check that the generated directory exists and has write permissions