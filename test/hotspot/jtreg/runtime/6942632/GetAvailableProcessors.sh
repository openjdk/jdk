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

javac="${TESTJAVA}/bin/javac"

src_file_base=GetAvailableProcessors
src_file="${TESTSRC}/$src_file_base.java"
log_file="${TESTCLASSES}/$src_file_base.output.log"
$javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} -d ${TESTCLASSES} $src_file

status=$?
if [ ! $status -eq "0" ]; then
  echo "Compilation failed: $src_file";
  exit 1
fi

# Write processor information from Windows APIs to a log file
get_proc_info_name=GetProcessorInfo
get_proc_info_log="${TESTCLASSES}/$get_proc_info_name.output.log"
${TESTNATIVEPATH}/$get_proc_info_name > $get_proc_info_log 2>&1

# Validate output from GetProcessorInfo.exe
unsupported_os_regex="Unsupported OS\\."
grep -Po "$unsupported_os_regex" $get_proc_info_log
status=$?
if [ $status -eq "0" ]; then
  echo "Test skipped: Unsupported Windows version.";
  exit 0
fi

processor_info_regex="Active processors per group: (\\d+,)+"
grep -Po "$processor_info_regex" $get_proc_info_log
status=$?
if [ ! $status -eq "0" ]; then
  echo "TESTBUG: $get_proc_info_name did not output a processor count.";
  exit 1
fi

# Write the processor counts to a file
native_procs="${TESTCLASSES}/processor_count_native.log"
grep -Po "$processor_info_regex" $get_proc_info_log   | sed -e 's/[a-zA-Z: \.]//g' > $native_procs 2>&1
group_processor_counts_str=$(<$native_procs)
IFS=, read -a group_processor_counts <<<"$group_processor_counts_str"

# Find the smallest processor group because on systems with different processor
# group sizes, "start /affinity" can still launch a process in a smaller
# processor group than the affinity provided via the /affinity parameter
let num_processors=64
for i in "${group_processor_counts[@]}"; do
  let group_processor_count=i
  echo "Active processors in group: $group_processor_count"
  if [ $group_processor_count -lt $num_processors ]; then
    num_processors=$group_processor_count
  fi
done

if [ $num_processors -le 0 ]; then
  echo "Test failed: $get_proc_info_name did not output a valid processor count.";
  exit 1
fi

if [ $num_processors -gt 64 ]; then
  echo "Test failed: $get_proc_info_name returned an invalid processor count.";
  exit 1
fi

if [ $num_processors -lt 64 ]; then
  let affinity=$((1 << num_processors))-1
  affinity=$(printf "%x" "$affinity")
else
  affinity=0xffffffffffffffff
fi

# Write Runtime.availableProcessors to a log file
java_cmd_line="${TESTJAVA}/bin/java -XX:+UseAllWindowsProcessorGroups ${TESTVMOPTS} -cp ${TESTCLASSES} $src_file_base"
cmd_line="start /wait /b /affinity $affinity $java_cmd_line > $log_file"

echo "Executing: $cmd_line"
cmd /c $cmd_line
status=$?
if [ ! $status -eq "0" ]; then
  echo "Test FAILED: $src_file";
  exit 1
fi

# Validate output from GetAvailableProcessors.java
available_processors_regex="Runtime\\.availableProcessors: \\d+"
grep -Po "$available_processors_regex" $log_file
status=$?
if [ ! $status -eq "0" ]; then
  echo "TESTBUG: $src_file did not output a processor count.";
  exit 1
fi

# Write the processor count to a file
java_procs_log="${TESTCLASSES}/processor_count_java.log"
grep -Po "$available_processors_regex" $log_file | sed -e 's/[a-zA-Z: \.]//g' > $java_procs_log 2>&1
java_runtime_processors=$(<$java_procs_log)

# Ensure the processor counts are identical

if [ "$java_runtime_processors" != "$num_processors" ]; then
  echo "Test failed: Runtime.availableProcessors ($java_runtime_processors) != Processor count in smallest group ($num_processors)"
  exit 1
else
  echo "Test passed."
fi
