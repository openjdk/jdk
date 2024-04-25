#
# Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

# @test
# @bug 6942632
# @requires os.family == "windows"
# @summary This test ensures that OpenJDK respects the process affinity
#          masks set when launched from the Windows command prompt using
#          "start /affinity HEXAFFINITY java.exe" when the
#          UseAllWindowsProcessorGroups flag is enabled.

if [ "${TESTSRC}" = "" ]
then
  echo "TESTSRC not set. Test cannot execute."
  exit 1
fi

if [ "${TESTNATIVEPATH}" = "" ]
then
  echo "TESTNATIVEPATH not set. Test cannot execute."
  exit 1
fi

if [ -z "${TESTCLASSES}" ]; then
  echo "TESTCLASSES undefined: defaulting to ."
  TESTCLASSES=.
fi

echo "TESTCLASSES:    $TESTCLASSES"
echo "TESTJAVACOPTS:  $TESTJAVACOPTS"
echo "TESTTOOLVMOPTS: $TESTTOOLVMOPTS"

JAVAC="${TESTJAVA}/bin/javac"

SRCFILEBASE=GetAvailableProcessors
SRCFILE="${TESTSRC}/$SRCFILEBASE.java"
LOGFILE="${TESTCLASSES}/$SRCFILEBASE.output.log"
$JAVAC ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} -d ${TESTCLASSES} $SRCFILE

status=$?
if [ ! $status -eq "0" ]; then
  echo "Compilation failed: $SRCFILE";
  exit 1
fi

# Write SYSTEM_INFO.dwNumberOfProcessors to a log file
GETPROCINFONAME=GetProcessorInfo
GETPROCINFOLOG="${TESTCLASSES}/$GETPROCINFONAME.output.log"
${TESTNATIVEPATH}/$GETPROCINFONAME > $GETPROCINFOLOG 2>&1

# Validate output from GetProcessorInfo.exe
unsupported_os_regex="Unsupported OS\\."
grep -Po "$unsupported_os_regex" $GETPROCINFOLOG
status=$?
if [ $status -eq "0" ]; then
  echo "Test skipped: Unsupported Windows version.";
  exit 0
fi

processor_info_regex="Active processors per group: (\\d+,)+"
grep -Po "$processor_info_regex" $GETPROCINFOLOG
status=$?
if [ ! $status -eq "0" ]; then
  echo "TESTBUG: $GETPROCINFONAME did not output a processor count.";
  exit 1
fi

# Write the processor counts to a file
NATIVEPROCS="${TESTCLASSES}/processor_count_native.log"
grep -Po "$processor_info_regex" $GETPROCINFOLOG   | sed -e 's/[a-zA-Z: \.]//g' > $NATIVEPROCS 2>&1
group_processor_counts_str=$(<$NATIVEPROCS)
IFS=, read -a group_processor_counts <<<"$group_processor_counts_str"

# Find the smallest processor group because on systems with different processor
# group sizes, "start /affinity" can still launch a process in a smaller
# processor group than the affinity provided via the /affinity parameter
let dwNumberOfProcessors=64
for i in "${group_processor_counts[@]}"; do
  let group_processor_count=i
  echo "Active processors in group: $group_processor_count"
  if [ $group_processor_count -lt $dwNumberOfProcessors ]; then
    dwNumberOfProcessors=$group_processor_count
  fi
done

if [ $dwNumberOfProcessors -le 0 ]; then
  echo "Test failed: $GETPROCINFONAME did not output a valid processor count.";
  exit 1
fi

if [ $dwNumberOfProcessors -gt 64 ]; then
  echo "Test failed: $GETPROCINFONAME returned an invalid processor count.";
  exit 1
fi

if [ $dwNumberOfProcessors -lt 64 ]; then
  let affinity=$((1 << dwNumberOfProcessors))-1
  affinity=$(printf "%x" "$affinity")
else
  affinity=0xffffffffffffffff
fi

# Write Runtime.availableProcessors to a log file
javaCmdLine="${TESTJAVA}/bin/java -XX:+UseAllWindowsProcessorGroups ${TESTVMOPTS} -cp ${TESTCLASSES} $SRCFILEBASE"
commandLine="start /wait /b /affinity $affinity $javaCmdLine > $LOGFILE"

echo "Executing: $commandLine"
cmd /c $commandLine
status=$?
if [ ! $status -eq "0" ]; then
  echo "Test FAILED: $SRCFILE";
  exit 1
fi

# Validate output from GetAvailableProcessors.java
available_processors_regex="Runtime\\.availableProcessors: \\d+"
grep -Po "$available_processors_regex" $LOGFILE
status=$?
if [ ! $status -eq "0" ]; then
  echo "TESTBUG: $SRCFILE did not output a processor count.";
  exit 1
fi

# Write the processor count to a file
JAVAPROCS="${TESTCLASSES}/processor_count_java.log"
grep -Po "$available_processors_regex" $LOGFILE | sed -e 's/[a-zA-Z: \.]//g' > $JAVAPROCS 2>&1
runtimeAvailableProcessors=$(<$JAVAPROCS)

# Ensure the processor counts are identical

echo "java.lang.Runtime.availableProcessors: $runtimeAvailableProcessors"
echo "SYSTEM_INFO.dwNumberOfProcessors:      $dwNumberOfProcessors"

if [ "$runtimeAvailableProcessors" != "$dwNumberOfProcessors" ]; then
  echo "Test failed: Runtime.availableProcessors ($runtimeAvailableProcessors) != dwNumberOfProcessors ($dwNumberOfProcessors)"
  exit 1
else
  echo "Test passed."
fi
