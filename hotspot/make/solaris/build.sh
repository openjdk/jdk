#! /bin/sh
#
# Copyright (c) 1998, 2008, Oracle and/or its affiliates. All rights reserved.
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

# Make sure the variable JAVA_HOME is set before running this script.

set -u


usage() {
    (
        echo "Usage : $0 [-sb | -sbfast] config ws_path"
        echo ""
        echo "Where:"
        echo "    -sb     ::= enable source browser info generation for"
        echo "                all configs during compilation"
        echo ""
        echo "    -sbfast ::= enable source browser info generation for"
        echo "                all configs without compilation"
        echo ""
        echo "    config  ::= debug     | debug1     | debugcore"
        echo "                fastdebug | fastdebug1 | fastdebugcore"
        echo "                jvmg      | jvmg1      | jvmgcore"
        echo "                optimized | optimized1 | optimizedcore"
        echo "                profiled  | profiled1  | profiledcore"
        echo "                product   | product1   | productcore"
        echo ""
        echo "    ws_path ::= path to HotSpot workspace"
    ) >&2
    exit 1
}

# extract possible options
options=""
if [ $# -gt 2 ]; then 
    case "$1" in
    -sb)
	options="CFLAGS_BROWSE=-xsb"
	shift
	;;
    -sbfast)
	options="CFLAGS_BROWSE=-xsbfast"
	shift
	;;
    *)
	echo "Unknown option: '$1'" >&2
	usage
	;;
    esac
fi

# should be just two args left at this point
if [ $# != 2 ]; then 
    usage
fi

# Just in case:
case ${JAVA_HOME} in
/*) true;;
?*) JAVA_HOME=`( cd $JAVA_HOME; pwd )`;;
esac

if [ "${JAVA_HOME}" = ""  -o  ! -d "${JAVA_HOME}" -o ! -d ${JAVA_HOME}/jre/lib/`uname -p` ]; then
    echo "JAVA_HOME needs to be set to a valid JDK path"
    echo "ksh : export JAVA_HOME=/net/tetrasparc/export/gobi/JDK1.2_fcs_V/solaris"
    echo "csh : setenv JAVA_HOME /net/tetrasparc/export/gobi/JDK1.2_fcs_V/solaris"
    exit 1
fi


LD_LIBRARY_PATH=${JAVA_HOME}/jre/lib/`uname -p`:\
${JAVA_HOME}/jre/lib/`uname -p`/native_threads:${LD_LIBRARY_PATH-.}

# This is necessary as long as we are using the old launcher
# with the new distribution format:
CLASSPATH=${JAVA_HOME}/jre/lib/rt.jar:${CLASSPATH-.}


for gm in gmake gnumake
do
  if [ "${GNUMAKE-}" != "" ]; then break; fi
  ($gm --version >/dev/null) 2>/dev/null && GNUMAKE=$gm
done
: ${GNUMAKE:?'Cannot locate the gnumake program.  Stop.'}


echo "### ENVIRONMENT SETTINGS:"
export JAVA_HOME		; echo "JAVA_HOME=$JAVA_HOME"
export LD_LIBRARY_PATH		; echo "LD_LIBRARY_PATH=$LD_LIBRARY_PATH"
export CLASSPATH		; echo "CLASSPATH=$CLASSPATH"
export GNUMAKE			; echo "GNUMAKE=$GNUMAKE"
echo "###"

config=$1
ws_path=$2

case ${ws_path} in
/*) true;;
?*) ws_path=`(cd ${ws_path}; pwd)`;;
esac

echo \
${GNUMAKE} -f ${ws_path}/make/solaris/Makefile \
    $config GAMMADIR=${ws_path} $options
${GNUMAKE} -f ${ws_path}/make/solaris/Makefile \
    $config GAMMADIR=${ws_path} $options
