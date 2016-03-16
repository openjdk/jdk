#
# Copyright (c) 2010, 2015, Oracle and/or its affiliates. All rights reserved.
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

COMMONSRC_REL=src
ALTSRC_REL=src/closed # Change this to pick up alt sources from somewhere else

COMMONSRC=${WorkSpace}/${COMMONSRC_REL}
if [ "x$OPENJDK" != "xtrue" ]; then
  ALTSRC=${WorkSpace}/${ALTSRC_REL}
else
  ALTSRC=PATH_THAT_DOES_NOT_EXIST
fi

BASE_PATHS="`if [ -d ${ALTSRC}/share/vm ]; then $FIND ${ALTSRC}/share/vm ! -name vm -prune -type d \! \( -name adlc -o -name c1 -o -name gc -o -name opto -o -name shark -o -name libadt \); fi`"
BASE_PATHS="${BASE_PATHS} ` $FIND ${COMMONSRC}/share/vm ! -name vm -prune -type d \! \( -name adlc -o -name c1 -o -name gc -o -name opto -o -name shark -o -name libadt \)`"

for sd in \
    share/vm/gc/shared \
    os/${Platform_os_family}/vm \
    cpu/${Platform_arch}/vm \
    os_cpu/${Platform_os_arch}/vm; do 
  if [ -d "${ALTSRC}/${sd}" ]; then
    BASE_PATHS="${BASE_PATHS} ${ALTSRC}/${sd}"
  fi
  BASE_PATHS="${BASE_PATHS} ${COMMONSRC}/${sd}"
done

BASE_PATHS="${BASE_PATHS} ${GENERATED}/jvmtifiles ${GENERATED}/tracefiles"

if [ -d "${ALTSRC}/share/vm/jfr/buffers" ]; then
  BASE_PATHS="${BASE_PATHS} ${ALTSRC}/share/vm/jfr/buffers"
fi

BASE_PATHS="${BASE_PATHS} ${COMMONSRC}/share/vm/prims/wbtestmethods"

# shared is already in BASE_PATHS. Should add vm/memory but that one is also in BASE_PATHS.
if [ -d "${ALTSRC}/share/vm/gc" ]; then
  BASE_PATHS="${BASE_PATHS} `$FIND ${ALTSRC}/share/vm/gc ! -name gc -prune -type d \! -name shared`"
fi
BASE_PATHS="${BASE_PATHS} `$FIND ${COMMONSRC}/share/vm/gc ! -name gc -prune -type d \! -name shared`"

if [ -d "${ALTSRC}/share/vm/c1" ]; then
  COMPILER1_PATHS="${ALTSRC}/share/vm/c1"
fi
COMPILER1_PATHS="${COMPILER1_PATHS} ${COMMONSRC}/share/vm/c1"

if [ -d "${ALTSRC}/share/vm/opto" ]; then
  COMPILER2_PATHS="${ALTSRC}/share/vm/opto"
fi
COMPILER2_PATHS="${COMPILER2_PATHS} ${COMMONSRC}/share/vm/opto"
if [ -d "${ALTSRC}/share/vm/libadt" ]; then
  COMPILER2_PATHS="${COMPILER2_PATHS} ${ALTSRC}/share/vm/libadt"
fi
COMPILER2_PATHS="${COMPILER2_PATHS} ${COMMONSRC}/share/vm/libadt"
COMPILER2_PATHS="${COMPILER2_PATHS} ${GENERATED}/adfiles"

# Include dirs per type.
case "${TYPE}" in
    "compiler1") Src_Dirs="${BASE_PATHS} ${COMPILER1_PATHS}" ;;
    "compiler2") Src_Dirs="${BASE_PATHS} ${COMPILER2_PATHS}" ;;
    "tiered")    Src_Dirs="${BASE_PATHS} ${COMPILER1_PATHS} ${COMPILER2_PATHS}" ;;
    "zero")      Src_Dirs="${BASE_PATHS}" ;;
    "shark")     Src_Dirs="${BASE_PATHS}" ;;
esac

COMPILER2_SPECIFIC_FILES="opto libadt bcEscapeAnalyzer.cpp c2_* runtime_*"
COMPILER1_SPECIFIC_FILES="c1_*"
JVMCI_SPECIFIC_FILES="*jvmci* *JVMCI*"
SHARK_SPECIFIC_FILES="shark"
ZERO_SPECIFIC_FILES="zero"

# Always exclude these.
Src_Files_EXCLUDE="jsig.c jvmtiEnvRecommended.cpp jvmtiEnvStub.cpp"

# Exclude per type.
case "${TYPE}" in
    "compiler1") Src_Files_EXCLUDE="${Src_Files_EXCLUDE} ${COMPILER2_SPECIFIC_FILES} ${JVMCI_SPECIFIC_FILES} ${ZERO_SPECIFIC_FILES} ${SHARK_SPECIFIC_FILES} ciTypeFlow.cpp" ;;
    "compiler2") Src_Files_EXCLUDE="${Src_Files_EXCLUDE} ${COMPILER1_SPECIFIC_FILES} ${ZERO_SPECIFIC_FILES} ${SHARK_SPECIFIC_FILES}" ;;
    "tiered")    Src_Files_EXCLUDE="${Src_Files_EXCLUDE} ${ZERO_SPECIFIC_FILES} ${SHARK_SPECIFIC_FILES}" ;;
    "zero")      Src_Files_EXCLUDE="${Src_Files_EXCLUDE} ${COMPILER1_SPECIFIC_FILES} ${COMPILER2_SPECIFIC_FILES} ${JVMCI_SPECIFIC_FILES} ${SHARK_SPECIFIC_FILES} ciTypeFlow.cpp" ;;
    "shark")     Src_Files_EXCLUDE="${Src_Files_EXCLUDE} ${COMPILER1_SPECIFIC_FILES} ${COMPILER2_SPECIFIC_FILES} ${JVMCI_SPECIFIC_FILES} ${ZERO_SPECIFIC_FILES}" ;;
esac

# Special handling of arch model.
case "${Platform_arch_model}" in
	"x86_32") Src_Files_EXCLUDE="${Src_Files_EXCLUDE} *x86_64* ${JVMCI_SPECIFIC_FILES}" ;;
	"x86_64") Src_Files_EXCLUDE="${Src_Files_EXCLUDE} *x86_32*" ;;
esac

# Locate all source files in the given directory, excluding files in Src_Files_EXCLUDE.
function findsrc {
    $FIND ${1}/. ! -name . -prune \
		-a \( -name \*.c -o -name \*.cpp -o -name \*.s \) \
		-a \! \( -name ${Src_Files_EXCLUDE// / -o -name } \) \
		| sed 's/.*\/\(.*\)/\1/';
}

Src_Files=
for e in ${Src_Dirs}; do
   Src_Files="${Src_Files}`findsrc ${e}` "
done 

Obj_Files=" "
for e in ${Src_Files}; do
        o="${e%\.[!.]*}.obj"
        set +e
        chk=`expr "${Obj_Files}" : ".* $o"`
        set -e
        if [ "$chk" != 0 ]; then
             echo "# INFO: skipping duplicate $o"
             continue
        fi
	Obj_Files="${Obj_Files}$o "
done
Obj_Files=`echo ${Obj_Files} | tr ' ' '\n' | sort`

echo Obj_Files=${Obj_Files}
