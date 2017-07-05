#!/bin/sh
#
#  Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
#  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
# 
#  This code is free software; you can redistribute it and/or modify it
#  under the terms of the GNU General Public License version 2 only, as
#  published by the Free Software Foundation.
# 
#  This code is distributed in the hope that it will be useful, but WITHOUT
#  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
#  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
#  version 2 for more details (a copy is included in the LICENSE file that
#  accompanied this code).
# 
#  You should have received a copy of the GNU General Public License version
#  2 along with this work; if not, write to the Free Software Foundation,
#  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
# 
#  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
#  or visit www.oracle.com if you need additional information or have any
#  questions.
# 

#
# This Environment script was written to capture typically used environment
# setup for a given shell test. 
#

# TESTJAVA can be a JDK or JRE. If JRE you need to set COMPILEJAVA
if [ "${TESTJAVA}" = "" ]
then
  echo "TESTJAVA not set.  Test cannot execute.  Failed."
  exit 1
fi
echo "TESTJAVA=${TESTJAVA}"

# COMPILEJAVA requires a JDK, some shell test use javac,jar,etc 
if [ "${COMPILEJAVA}" = "" ]
then
 echo "COMPILEJAVA not set.  Using TESTJAVA as default"
 COMPILEJAVA=${TESTJAVA}
fi
echo "COMPILEJAVA=${COMPILEJAVA}"

if [ "${TESTCLASSES}" = "" ]
then
  echo "TESTCLASES not set.  Using "." as default"
  TESTCLASSES=.
fi
echo "TESTCLASSES=${TESTCLASSES}"

TESTOPTS="${TESTVMOPTS} ${TESTJAVAOPTS}"
echo "TESTOPTS=${TESTOPTS}"

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  AIX | Darwin | Linux | SunOS )
    NULL=/dev/null
    PS=":"
    FS="/"
    RM=/bin/rm
    CP=/bin/cp
    MV=/bin/mv
    ;;
  Windows_* )
    NULL=NUL
    PS=";"
    FS="\\"
    RM=rm
    CP=cp
    MV=mv
    ;;
  CYGWIN_* )
    NULL=/dev/null
    PS=";"
    FS="/"
    RM=rm
    CP=cp
    MV=mv
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

export NULL PS FS RM CP MV
echo "NULL =${NULL}"
echo "PS =${PS}"
echo "FS =${FS}"
echo "RM =${RM}"
echo "CP =${CP}"
echo "MV =${MV}"

# jtreg -classpathappend:<path>
JEMMYPATH=${CPAPPEND}
CLASSPATH=.${PS}${TESTCLASSES}${PS}${JEMMYPATH} ; export CLASSPATH
echo "CLASSPATH =${CLASSPATH}"

# Current directory is scratch directory 
THIS_DIR=.
echo "THIS_DIR=${THIS_DIR}"

# Check to ensure the java defined actually works
${TESTJAVA}${FS}bin${FS}java ${TESTOPTS} -version
if [ $? != 0 ]; then
  echo "Wrong TESTJAVA or TESTJAVAOPTS or TESTVMOPTS:"
  echo ''$TESTJAVA'' ''$TESTJAVAOPTS'' ''$TESTVMOPTS''
  exit 1
fi

${TESTJAVA}${FS}bin${FS}java ${TESTOPTS} -Xinternalversion | sed -e 's/[(][^)]*[)]//g' -e 's/ by "[^"]*"//g' > vm_version.out 2>&1
echo "INT_VERSION=`cat vm_version.out 2>&1`"

VM_TYPE="unknown"
grep "Server" vm_version.out > ${NULL}
if [ $? = 0 ]
then
  VM_TYPE="server"
fi
grep "Client" vm_version.out > ${NULL}
if [ $? = 0 ]
then
  VM_TYPE="client"
fi

VM_BITS="32"
grep "64-Bit" vm_version.out > ${NULL}
if [ $? = 0 ]
then
  VM_BITS="64"
fi

VM_OS="unknown"
grep "aix" vm_version.out > ${NULL}
if [ $? = 0 ]
then
  VM_OS="aix"
fi
grep "bsd" vm_version.out > ${NULL}
if [ $? = 0 ]
then
  VM_OS="bsd"
fi
grep "linux" vm_version.out > ${NULL}
if [ $? = 0 ]
then
  VM_OS="linux"
fi
grep "solaris" vm_version.out > ${NULL}
if [ $? = 0 ]
then
  VM_OS="solaris"
fi
grep "windows" vm_version.out > ${NULL}
if [ $? = 0 ]
then
  VM_OS="windows"
fi

VM_CPU="unknown"
grep "sparc" vm_version.out > ${NULL}
if [ $? = 0 ]
then
  VM_CPU="sparc"
  if [ $VM_BITS = "64" ]
  then
    VM_CPU="sparcv9"
  fi
fi
grep "x86" vm_version.out > ${NULL}
if [ $? = 0 ]
then
  VM_CPU="i386"
fi
grep "amd64" vm_version.out > ${NULL}
if [ $? = 0 ]
then
  VM_CPU="amd64"
fi
grep "arm" vm_version.out > ${NULL}
if [ $? = 0 ]
then
  VM_CPU="arm"
fi
grep "ppc" vm_version.out > ${NULL}
if [ $? = 0 ]
then
  VM_CPU="ppc"
  if [ $VM_BITS = "64" ]
  then
    VM_CPU="ppc64"
  fi
fi
grep "ia64" vm_version.out > ${NULL}
if [ $? = 0 ]
then
  VM_CPU="ia64"
fi
grep "aarch64" vm_version.out > ${NULL}
if [ $? = 0 ]
then
  VM_CPU="aarch64"
fi
export VM_TYPE VM_BITS VM_OS VM_CPU
echo "VM_TYPE=${VM_TYPE}"
echo "VM_BITS=${VM_BITS}"
echo "VM_OS=${VM_OS}"
echo "VM_CPU=${VM_CPU}"
