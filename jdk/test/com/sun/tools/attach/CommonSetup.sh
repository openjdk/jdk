#!/bin/sh

#
# Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.
#


#
#
# Common setup for the Attach API unit tests. Setups up the following variables:
#
# PS - path sep.
# FS - file sep.
# JAVA - java cmd.
# JAVAC - javac cmd.
# JAR - jar cmd.

OS=`uname -s`
case "$OS" in
  SunOS )
    PS=":"
    FS="/"
    ;;
  Linux )
    PS=":"
    FS="/"
    ;;
  Windows* )
    PS=";"
    OS="Windows"
    FS="\\"
    ;;
  CYGWIN* )
    PS=";"
    OS="Windows"
    FS="\\"
    isCygwin=true
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

if [ "${TESTJAVA}" = "" ]
then
  echo "TESTJAVA not set.  Test cannot execute.  Failed."
  exit 1
fi

if [ "${TESTSRC}" = "" ]
then
  echo "TESTSRC not set.  Test cannot execute.  Failed."
  exit 1
fi
 
if [ "${TESTCLASSES}" = "" ]
then
  echo "TESTCLASSES not set.  Test cannot execute.  Failed."
  exit 1
fi

JAVA="${TESTJAVA}/bin/java"
JAVAC="${TESTJAVA}/bin/javac"
JAR="${TESTJAVA}/bin/jar"

