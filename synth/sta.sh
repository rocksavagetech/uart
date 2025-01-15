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

# Loop through all the test cases
declare -a arr=(\
  "8_8_8" \
  "16_8_8" \
  "32_8_8" \
  )


for testCase in "${arr[@]}"
do
  cd $BUILD_ROOT/synth/$testCase
  echo "*** Running STA on " $testCase
  python3 ${PROJECT_ROOT}/synth/sdc.py --top ${TOP} --out ${BUILD_ROOT}/sta/${TOP}.sdc --clock clock=5.0 --net $BUILD_ROOT/synth/$testCase/${TOP}_net.v
  sta -no_init -no_splash -exit $PROJECT_ROOT/synth/sta.tcl | tee ./timing.rpt
  timing=`grep slack ./timing.rpt`
  mkdir -p $BUILD_ROOT/sta/$testCase
  echo -e "$testCase = \t $timing" >> $BUILD_ROOT/sta/$testCase/timing.rpt
  cd $PROJECT_ROOT/synth
done
