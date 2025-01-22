MAKEFLAGS += --silent

# Define SBT variable
SBT = sbt

# Default target
default: verilog

docs:
	@echo Generating docs
	mkdir -p $(shell pwd)/out/doc
	cd doc/user-guide && pdflatex -output-directory=$(shell pwd)/out/doc timer.tex | tee -a $(shell pwd)/out/doc/doc.rpt

update:
	@echo Updating...
	rm -rf ~/.sbt
	rm -rf ~/.ivy2
	sbt clean
	sbt dependencyUpdates

# Start with a fresh directory
clean:
	@echo Cleaning...
	rm -rf generated target *anno.json ./*.rpt doc/*.rpt syn/*.rpt syn.log out test_run_dir target
	rm -rf project/project project/target
	# filter all files with bad extensions
	find . -type f -name "*.aux" -delete
	find . -type f -name "*.toc" -delete
	find . -type f -name "*.out" -delete
	find . -type f -name "*.log" -delete
	find . -type f -name "*.fdb_latexmk" -delete
	find . -type f -name "*.fls" -delete
	find . -type f -name "*.synctex.gz" -delete
	find . -type f -name "*.pdf" -delete

# Generate verilog from the Chisel code
verilog:
	@echo Generating Verilog...
	@$(SBT) "runMain tech.rocksavage.Main verilog --mode print --module tech.rocksavage.chiselware.uart.Uart --config-class tech.rocksavage.chiselware.uart.UartConfig"

# Run the tests
test:
	@echo Running tests...
	@$(SBT) test

# Synthesize the design
synth:
	@echo Synthesizing...
	@$(SBT) "runMain tech.rocksavage.Main synth --module tech.rocksavage.chiselware.uart.Uart --config-class tech.rocksavage.chiselware.uart.UartConfig --techlib synth/stdcells.lib"

sta:
	@echo Running Timing Analysis...
	@$(SBT) "runMain tech.rocksavage.Main sta --module tech.rocksavage.chiselware.uart.Uart --config-class tech.rocksavage.chiselware.uart.UartConfig --techlib synth/stdcells.lib --clock-period 5.0"