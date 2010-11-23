#
# Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
#

set -e

# Note that we currently do not have a way to set HotSpotMksHome in
# the batch build, but so far this has not seemed to be a problem. The
# reason this environment variable is necessary is that it seems that
# Windows truncates very long PATHs when executing shells like MKS's
# sh, and it has been found that sometimes `which sh` fails.

if [ "x$HotSpotMksHome" != "x" ]; then
  TOOL_DIR="$HotSpotMksHome"
else
  # HotSpotMksHome is not set so use the directory that contains "sh".
  # This works with both MKS and Cygwin.
  SH=`which sh`
  TOOL_DIR=`dirname "$SH"`
fi

DIRNAME="$TOOL_DIR/dirname"
FIND="$TOOL_DIR/find"

TYPE=$1
Platform_arch=$2
Platform_arch_model=$3
Platform_os_family=windows
Platform_os_arch=windows_$Platform_arch

WorkSpace=$4
GENERATED=$5

BASE_PATHS="` $FIND ${WorkSpace}/src/share/vm ! -name vm -prune -type d \! \( -name adlc -o -name c1 -o -name gc_implementation -o -name opto -o -name shark -o -name libadt \)`"
BASE_PATHS="${BASE_PATHS} ${WorkSpace}/src/share/vm/gc_implementation/shared"
BASE_PATHS="${BASE_PATHS} ${WorkSpace}/src/os/${Platform_os_family}/vm"
BASE_PATHS="${BASE_PATHS} ${WorkSpace}/src/cpu/${Platform_arch}/vm"
BASE_PATHS="${BASE_PATHS} ${WorkSpace}/src/os_cpu/${Platform_os_arch}/vm"
BASE_PATHS="${BASE_PATHS} ${GENERATED}/jvmtifiles"

CORE_PATHS="${BASE_PATHS}"
# shared is already in BASE_PATHS. Should add vm/memory but that one is also in BASE_PATHS.
CORE_PATHS="${CORE_PATHS} `$FIND ${WorkSpace}/src/share/vm/gc_implementation ! -name gc_implementation -prune -type d \! -name shared`"

COMPILER1_PATHS="${WorkSpace}/src/share/vm/c1"

COMPILER2_PATHS="${WorkSpace}/src/share/vm/opto"
COMPILER2_PATHS="${COMPILER2_PATHS} ${WorkSpace}/src/share/vm/libadt"
COMPILER2_PATHS="${COMPILER2_PATHS} ${GENERATED}/adfiles"

# Include dirs per type.
case "${TYPE}" in
    "core")      Src_Dirs="${CORE_PATHS}" ;;
    "kernel")    Src_Dirs="${BASE_PATHS} ${COMPILER1_PATHS}" ;;
    "compiler1") Src_Dirs="${CORE_PATHS} ${COMPILER1_PATHS}" ;;
    "compiler2") Src_Dirs="${CORE_PATHS} ${COMPILER2_PATHS}" ;;
    "tiered")    Src_Dirs="${CORE_PATHS} ${COMPILER1_PATHS} ${COMPILER2_PATHS}" ;;
    "zero")      Src_Dirs="${CORE_PATHS}" ;;
    "shark")     Src_Dirs="${CORE_PATHS}" ;;
esac

COMPILER2_SPECIFIC_FILES="opto libadt bcEscapeAnalyzer.cpp chaitin* c2_* runtime_*"
COMPILER1_SPECIFIC_FILES="c1_*"
SHARK_SPECIFIC_FILES="shark"
ZERO_SPECIFIC_FILES="zero"

# These files need to be excluded when building the kernel target.
KERNEL_EXCLUDED_FILES="attachListener.cpp attachListener_windows.cpp dump.cpp dump_${Platform_arch_model}.cpp forte.cpp fprofiler.cpp heapDumper.cpp heapInspection.cpp jniCheck.cpp jvmtiCodeBlobEvents.cpp jvmtiExtensions.cpp jvmtiImpl.cpp jvmtiRawMonitor.cpp jvmtiTagMap.cpp jvmtiTrace.cpp restore.cpp serialize.cpp vmStructs.cpp g1MemoryPool.cpp psMemoryPool.cpp gcAdaptivePolicyCounters.cpp concurrentGCThread.cpp mutableNUMASpace.cpp allocationStats.cpp gSpaceCounters.cpp immutableSpace.cpp mutableSpace.cpp spaceCounters.cpp yieldingWorkgroup.cpp"

# Always exclude these.
Src_Files_EXCLUDE="jsig.c jvmtiEnvRecommended.cpp jvmtiEnvStub.cpp"

# Exclude per type.
case "${TYPE}" in
    "core")      Src_Files_EXCLUDE="${Src_Files_EXCLUDE} ${COMPILER1_SPECIFIC_FILES} ${COMPILER2_SPECIFIC_FILES} ${ZERO_SPECIFIC_FILES} ${SHARK_SPECIFIC_FILES} ciTypeFlow.cpp" ;;
    "kernel")    Src_Files_EXCLUDE="${Src_Files_EXCLUDE} ${COMPILER2_SPECIFIC_FILES} ${ZERO_SPECIFIC_FILES} ${SHARK_SPECIFIC_FILES} ${KERNEL_EXCLUDED_FILES} ciTypeFlow.cpp" ;;
    "compiler1") Src_Files_EXCLUDE="${Src_Files_EXCLUDE} ${COMPILER2_SPECIFIC_FILES} ${ZERO_SPECIFIC_FILES} ${SHARK_SPECIFIC_FILES} ciTypeFlow.cpp" ;;
    "compiler2") Src_Files_EXCLUDE="${Src_Files_EXCLUDE} ${COMPILER1_SPECIFIC_FILES} ${ZERO_SPECIFIC_FILES} ${SHARK_SPECIFIC_FILES}" ;;
    "tiered")    Src_Files_EXCLUDE="${Src_Files_EXCLUDE} ${ZERO_SPECIFIC_FILES} ${SHARK_SPECIFIC_FILES}" ;;
    "zero")      Src_Files_EXCLUDE="${Src_Files_EXCLUDE} ${COMPILER1_SPECIFIC_FILES} ${COMPILER2_SPECIFIC_FILES} ${SHARK_SPECIFIC_FILES} ciTypeFlow.cpp" ;;
    "shark")     Src_Files_EXCLUDE="${Src_Files_EXCLUDE} ${COMPILER1_SPECIFIC_FILES} ${COMPILER2_SPECIFIC_FILES} ${ZERO_SPECIFIC_FILES}" ;;
esac

# Special handling of arch model.
case "${Platform_arch_model}" in
	"x86_32") Src_Files_EXCLUDE="${Src_Files_EXCLUDE} *x86_64*" ;;
	"x86_64") Src_Files_EXCLUDE="${Src_Files_EXCLUDE} *x86_32*" ;;
esac

function findsrc {
    $FIND ${1} \( -name \*.c -o -name \*.cpp -o -name \*.s \) -a \! \( -name ${Src_Files_EXCLUDE// / -o -name } \) | sed 's/.*\/\(.*\)/\1/';
}

Src_Files=
for e in ${Src_Dirs}; do
   Src_Files="${Src_Files}`findsrc ${e}` "
done 

Obj_Files=
for e in ${Src_Files}; do
	Obj_Files="${Obj_Files}${e%\.[!.]*}.obj "
done

echo Obj_Files=${Obj_Files}
