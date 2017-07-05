#!/bin/sh
#
# Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
# 
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
# 
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
# 
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
# 
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

###########################################################################################
# This is a helper script to evaluate nashorn with optimistic types
# it produces a flight recording for every run, and uses the best 
# known flags for performance for the current configration
###########################################################################################

# Flags to enable assertions, we need the system assertions too, since
# this script runs Nashorn in the BCP to override any nashorn.jar that might
# reside in your $JAVA_HOME/jre/lib/ext/nashorn.jar
#
ENABLE_ASSERTIONS_FLAGS="-ea -esa"

# Flags to instrument lambdaform computation, caching, interpretation and compilation
# Default compile threshold for lambdaforms is 30
#
#LAMBDAFORM_FLAGS="\
#    -Djava.lang.invoke.MethodHandle.COMPILE_THRESHOLD=3 \
#    -Djava.lang.invoke.MethodHandle.DUMP_CLASS_FILES=true \
#    -Djava.lang.invoke.MethodHandle.TRACE_METHOD_LINKAGE=true \
#    -Djava.lang.invoke.MethodHandle.TRACE_INTERPRETER=true"

# Flags to run trusted tests from the Nashorn test suite
#
#TRUSTED_TEST_FLAGS="\
#-Djava.security.manager \
#-Djava.security.policy=../build/nashorn.policy -Dnashorn.debug"

# Testing out new code optimizations using the generic hotspot "new code" parameter
#
#USE_NEW_CODE_FLAGS=-XX:+UnlockDiagnosticVMOptions -XX:+UseNewCode

#
#-Dnashorn.typeInfo.disabled=false \
# and for Nashorn options: 
# --class-cache-size=0 --persistent-code-cache=false 

# Unique timestamped file name for JFR recordings. For JFR, we also have to
# crank up the stack cutoff depth to 1024, because of ridiculously long lambda form
# stack traces.
#
# It is also recommended that you go into $JAVA_HOME/jre/lib/jfr/default.jfc and
# set the "method-sampling-interval" Normal and Maximum sample time as low as you
# can go (10 ms on most platforms). The default is normally higher. The increased
# sampling overhead is usually negligible for Nashorn runs, but the data is better

if [ -z $JFR_FILENAME ]; then
    JFR_FILENAME="./nashorn_$(date|sed "s/ /_/g"|sed "s/:/_/g").jfr"
fi

# Flight recorder
#
# see above - already in place, copy the flags down here to disable
ENABLE_FLIGHT_RECORDER_FLAGS="\
    -XX:+UnlockCommercialFeatures \
    -XX:+FlightRecorder \
    -XX:FlightRecorderOptions=defaultrecording=true,disk=true,dumponexit=true,dumponexitpath=$JFR_FILENAME,stackdepth=1024"

# Type specialization and math intrinsic replacement should be enabled by default in 8u20 and nine,
# keeping this flag around for experimental reasons. Replace + with - to switch it off
#
#ENABLE_TYPE_SPECIALIZATION_FLAGS=-XX:+UseTypeSpeculation

# Same with math intrinsics. They should be enabled by default in 8u20 and 9, so
# this disables them if needed
#
#DISABLE_MATH_INTRINSICS_FLAGS=-XX:-UseMathExactIntrinsics

# Add timing to time the compilation phases.
#ENABLE_TIME_FLAGS=--log=time

# Add ShowHiddenFrames to get lambda form internals on the stack traces
#ENABLE_SHOW_HIDDEN_FRAMES_FLAGS=-XX:+ShowHiddenFrames

# Add print optoassembly to get an asm dump. This requires 1) a debug build, not product,
# That tired compilation is switched off, for C2 only output and that the number of
# compiler threads is set to 1 for determinsm.
#
#PRINT_ASM_FLAGS=-XX:+PrintOptoAssembly -XX:-TieredCompilation -XX:CICompilerCount=1 \

# Tier compile threasholds. Default value is 10. (1-100 is useful for experiments)
#TIER_COMPILATION_THRESHOLD_FLAGS=-XX:IncreaseFirstTierCompileThresholdAt=10

# Directory where to look for nashorn.jar in a dist folder. The default is "..", assuming
# that we run the script from the make dir
DIR=..
NASHORN_JAR=$DIR/dist/nashorn.jar


# The built Nashorn jar is placed first in the bootclasspath to override the JDK
# nashorn.jar in $JAVA_HOME/jre/lib/ext. Thus, we also need -esa, as assertions in
# nashorn count as system assertions in this configuration

# Type profiling default level is 111, 222 adds some compile time, but is faster

$JAVA_HOME/bin/java \
$ENABLE_ASSERTIONS_FLAGS \
$LAMBDAFORM_FLAGS \
$TRUSTED_FLAGS \
$USE_NEW_CODE_FLAGS \
$ENABLE_SHOW_HIDDEN_FRAMES_FLAGS \
$ENABLE_FLIGHT_RECORDER_FLAGS \
$ENABLE_TYPE_SPECIALIZATION_FLAGS \
$TIERED_COMPILATION_THRESOLD_FLAGS \
$DISABLE_MATH_INTRINSICS_FLAGS \
$PRINT_ASM_FLAGS \
-Xbootclasspath/p:$NASHORN_JAR \
-Xms2G -Xmx2G \
-XX:TypeProfileLevel=222 \
-cp $CLASSPATH:../build/test/classes/ \
jdk.nashorn.tools.Shell $ENABLE_TIME_FLAGS ${@}


