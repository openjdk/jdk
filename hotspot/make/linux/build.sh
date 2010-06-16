#! /bin/sh
#
# Copyright (c) 1999, 2008, Oracle and/or its affiliates. All rights reserved.
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


if [ $# != 2 ]; then 
    echo "Usage : $0 Build_Options Location"
    echo "Build Options : debug or optimized or basicdebug or basic or clean"
    echo "Location : specify any workspace which has gamma sources"
    exit 1
fi

# Just in case:
case ${JAVA_HOME} in
/*) true;;
?*) JAVA_HOME=`( cd $JAVA_HOME; pwd )`;;
esac

case `uname -m` in
  i386|i486|i586|i686)
    mach=i386
    ;;
  *)
    echo "Unsupported machine: " `uname -m`
    exit 1
    ;;
esac

if [ "${JAVA_HOME}" = ""  -o  ! -d "${JAVA_HOME}" -o ! -d ${JAVA_HOME}/jre/lib/${mach} ]; then
    echo "JAVA_HOME needs to be set to a valid JDK path"
    echo "ksh : export JAVA_HOME=/net/tetrasparc/export/gobi/JDK1.2_fcs_V/linux"
    echo "csh : setenv JAVA_HOME /net/tetrasparc/export/gobi/JDK1.2_fcs_V/linux"
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

Build_Options=$1
Location=$2

case ${Location} in
/*) true;;
?*) Location=`(cd ${Location}; pwd)`;;
esac

echo \
${GNUMAKE} -f ${Location}/make/linux/Makefile $Build_Options GAMMADIR=${Location}
${GNUMAKE} -f ${Location}/make/linux/Makefile $Build_Options GAMMADIR=${Location}
