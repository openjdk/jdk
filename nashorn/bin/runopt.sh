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

# Flags to instrument lambdaform computation, caching, interpretation and compilation
# Default compile threshold for lambdaforms is 30
#FLAGS="-Djava.lang.invoke.MethodHandle.COMPILE_THRESHOLD=3 -Djava.lang.invoke.MethodHandle.DUMP_CLASS_FILES=true -Djava.lang.invoke.MethodHandle.TRACE_METHOD_LINKAGE=true -Djava.lang.invoke.MethodHandle.TRACE_INTERPRETER=true"


# Flags to run trusted tests from the Nashorn test suite
#FLAGS="-Djava.security.manager -Djava.security.policy=../build/nashorn.policy -Dnashorn.debug"


# Unique timestamped file name for JFR recordings. For JFR, we also have to
# crank up the stack cutoff depth to 1024, because of ridiculously long lambda form
# stack traces.
#
# It is also recommended that you go into $JAVA_HOME/jre/lib/jfr/default.jfc and
# set the "method-sampling-interval" Normal and Maximum sample time as low as you
# can go (10 ms on most platforms). The default is normally higher. The increased
# sampling overhead is usually negligible for Nashorn runs, but the data is better

JFR_FILENAME="./nashorn_$(date|sed "s/ /_/g"|sed "s/:/_/g").jfr"


# Directory where to look for nashorn.jar in a dist folder. The default is "..", assuming
# that we run the script from the make dir
DIR=..
NASHORN_JAR=$DIR/dist/nashorn.jar


# The built Nashorn jar is placed first in the bootclasspath to override the JDK
# nashorn.jar in $JAVA_HOME/jre/lib/ext. Thus, we also need -esa, as assertions in
# nashorn count as system assertions in this configuration

# Type profiling default level is 111, 222 adds some compile time, but is faster

$JAVA_HOME/bin/java \
$FLAGS \
-ea \
-esa \
-Xbootclasspath/p:$NASHORN_JAR \
-Xms2G -Xmx2G \
-XX:TypeProfileLevel=222 \
-cp $CLASSPATH:../build/test/classes/ \
jdk.nashorn.tools.Shell ${@}

# Below are flags that may come in handy, but aren't used for default runs

# Testing out new code optimizations using the generic hotspot "new code" parameter
#-XX:+UnlockDiagnosticVMOptions \
#-XX:+UseNewCode \

# Flight recorder
#-XX:+UnlockCommercialFeatures \
#-XX:+FlightRecorder \
#-XX:FlightRecorderOptions=defaultrecording=true,disk=true,dumponexit=true,dumponexitpath=$JFR_FILENAME,stackdepth=1024 \


# Type specialization and math intrinsic replacement should be enabled by default in 8u20 and nine,
# keeping this flag around for experimental reasons. Replace + with - to switch it off
#-XX:+UseTypeSpeculation \


# Same with math intrinsics. They should be enabled by default in 8u20 and 9
#-XX:+UseMathExactIntrinsics \


# Add -Dnashorn.time to time the compilation phases.
#-Dnashorn.time \


# Add ShowHiddenFrames to get lambda form internals on the stack traces
#-XX:+ShowHiddenFrames \


# Add print optoassembly to get an asm dump. This requires 1) a debug build, not product,
# That tired compilation is switched off, for C2 only output and that the number of
# compiler threads is set to 1 for determinsm.
#-XX:+PrintOptoAssembly -XX:-TieredCompilation -XX:CICompilerCount=1 \

# Tier compile threasholds. Default value is 10. (1-100 is useful for experiments)
# -XX:IncreaseFirstTierCompileThresholdAt=XX

