#!/bin/sh

#
# Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
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


# Common setup for tool tests and other tests that use jtools.
# Checks that TESTJAVA, TESTSRC, and TESTCLASSES environment variables are set.
#
# Creates the following constants for use by the caller:
#   JAVA        - java launcher
#   JHAT        - jhat utility
#   JINFO       - jinfo utility
#   JMAP        - jmap utility
#   JPS         - jps utility
#   JSTACK      - jstack utility
#   JCMD        - jcmd utility
#   OS          - operating system name
#   PATTERN_EOL - grep or sed end-of-line pattern
#   PATTERN_WS  - grep or sed whitespace pattern
#   PS          - path separator (";" or ":")
#
# Sets the following variables:
#
#   isCygwin  - true if environment is Cygwin
#   isMKS     - true if environment is MKS
#   isLinux   - true if OS is Linux
#   isSolaris - true if OS is Solaris
#   isWindows - true if OS is Windows
#   isMacos   - true if OS is Macos X


if [ -z "${TESTJAVA}" ]; then
  echo "ERROR: TESTJAVA not set.  Test cannot execute.  Failed."
  exit 1
fi

if [ -z "${TESTSRC}" ]; then
  echo "ERROR: TESTSRC not set.  Test cannot execute.  Failed."
  exit 1
fi

if [ -z "${TESTCLASSES}" ]; then
  echo "ERROR: TESTCLASSES not set.  Test cannot execute.  Failed."
  exit 1
fi

# only enable these after checking the expected incoming env variables
set -eu

JAVA="${TESTJAVA}/bin/java"
JHAT="${TESTJAVA}/bin/jhat"
JINFO="${TESTJAVA}/bin/jinfo"
JMAP="${TESTJAVA}/bin/jmap"
JPS="${TESTJAVA}/bin/jps"
JSTACK="${TESTJAVA}/bin/jstack"
JCMD="${TESTJAVA}/bin/jcmd"

isCygwin=false
isMKS=false
isLinux=false
isSolaris=false
isUnknownOS=false
isWindows=false
isMacos=false

OS=`uname -s`

# start with some UNIX like defaults
PATTERN_EOL='$'
# blank and tab
PATTERN_WS='[ 	]'
PS=":"

case "$OS" in
  CYGWIN* )
    OS="Windows"
    PATTERN_EOL='[]*$'
    # blank and tab
    PATTERN_WS='[ \t]'
    isCygwin=true
    isWindows=true
    ;;
  Linux )
    OS="Linux"
    isLinux=true
    ;;
  Darwin )
    OS="Mac OS X"
    isMacos=true
    ;;
  SunOS )
    OS="Solaris"
    isSolaris=true
    ;;
  Windows* )
    OS="Windows"
    PATTERN_EOL='[]*$'
    PS=";"
    isWindows=true
    ;;
  * )
    isUnknownOS=true
    ;;
esac
