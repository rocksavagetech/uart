#!/bin/sh

# Example Usage: synth.sh GPIO

# The Following Vars are set by the development flake:
# - BUILD_ROOT
# - PROJECT_ROOT
# - TOP

# Exit on error
set -e

# if ENV vars are not set, error out
if [ -z ${BUILD_ROOT} ]; then
    echo "BUILD_ROOT is not set. Exiting..."
    exit 1
fi
if [ -z ${PROJECT_ROOT} ]; then
    echo "PROJECT_ROOT is not set. Exiting..."
    exit 1
fi
if [ -z ${TOP} ]; then
    echo "TOP is not set. Exiting..."
    exit 1
fi

# Set up build directories
if [ ! -e ${BUILD_ROOT}/synth ]; then 
    mkdir -p ${BUILD_ROOT}/synth
fi

nand2Area=0.798 # Nangate 45nm
designName="Gpio"

# Loop through all the test cases
declare -a arr=(\
  "8_8_8" \
  "16_8_8" \
  "32_8_8" \
  )


for testCase in "${arr[@]}"
do
  cd $BUILD_ROOT/verilog/$testCase
  mkdir -p ${BUILD_ROOT}/synth/$testCase/

  # removing old tcl file
  if [ -e ${BUILD_ROOT}/synth/$testCase/synth.tcl ]; then
      rm -f ${BUILD_ROOT}/synth/$testCase/synth.tcl
  fi

  echo "set top ${TOP}" >> ${BUILD_ROOT}/synth/$testCase/synth.tcl
  echo "set techLib ${PROJECT_ROOT}/synth/stdcells.lib" >> ${BUILD_ROOT}/synth/$testCase/synth.tcl
  echo "yosys -import" >> ${BUILD_ROOT}/synth/$testCase/synth.tcl
  echo "set f [open ${BUILD_ROOT}/verilog/$testCase/filelist.f]" >> ${BUILD_ROOT}/synth/$testCase/synth.tcl
  echo "while {[gets \$f line] > -1} {" >> ${BUILD_ROOT}/synth/$testCase/synth.tcl
  echo "  read_verilog -sv ${BUILD_ROOT}/verilog/$testCase/\$line" >> ${BUILD_ROOT}/synth/$testCase/synth.tcl
  echo "}" >> ${BUILD_ROOT}/synth/$testCase/synth.tcl
  echo "close \$f" >> ${BUILD_ROOT}/synth/$testCase/synth.tcl
  echo "hierarchy -check -top \$top" >> ${BUILD_ROOT}/synth/$testCase/synth.tcl
  echo "synth -top \$top" >> ${BUILD_ROOT}/synth/$testCase/synth.tcl
  echo "flatten" >> ${BUILD_ROOT}/synth/$testCase/synth.tcl
  echo "dfflibmap -liberty \$techLib" >> ${BUILD_ROOT}/synth/$testCase/synth.tcl
  echo "abc -liberty \$techLib" >> ${BUILD_ROOT}/synth/$testCase/synth.tcl
  echo "opt_clean -purge" >> ${BUILD_ROOT}/synth/$testCase/synth.tcl
  echo "write_verilog -noattr ./$testCase/\$top\_net.v" >> ${BUILD_ROOT}/synth/$testCase/synth.tcl
  echo "stat -liberty \$techLib" >> ${BUILD_ROOT}/synth/$testCase/synth.tcl

  echo "*** Synthesizing test case:  " $testCase
  echo ""
  cd ${BUILD_ROOT}/synth/
  mkdir -p ${BUILD_ROOT}/synth/$testCase
  yosys -Qv 1 -l ${BUILD_ROOT}/synth/$testCase/synth.rpt ${BUILD_ROOT}/synth/$testCase/synth.tcl
  echo ""

  # Extract area
  file=${BUILD_ROOT}/synth/$testCase/synth.rpt
  areaLine=$(grep "Chip area" $file)
  floatArea=$(echo $areaLine| cut -d':' -f 2)
  intArea=$(echo ${floatArea%.*})
  gates=$(echo "$intArea/$nand2Area" | bc)
  if [ -e ${BUILD_ROOT}/synth/area.rpt ]; then
      rm -f ${BUILD_ROOT}/synth/area.rpt
  fi
  echo -e "$testCase = \t $gates gates" >> ./area.rpt
  echo -e "$testCase = \t $gates gates"

  cd $PROJECT_ROOT/synth
done
