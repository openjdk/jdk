#!/bin/sh

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

# @test
# @bug 4772857
# @summary Unit test for Charset.defaultCharset
#
# @build Default
# @run shell default.sh
#

# Command-line usage: sh default.sh [/path/to/build]

if [ -z "$TESTJAVA" ]; then
  if [ $# -lt 1 ]; then exit 1; fi
  TESTJAVA=$1; shift
  TESTSRC=`pwd`
  TESTCLASSES=`pwd`
fi

s="`uname -s`"
if [ "$s" != Linux -a "$s" != SunOS ]; then
  echo "$s: locale command not supported on this system, skipping..."
  exit 0
fi

JAVA=$TESTJAVA/bin/java

tolower() {
  echo "$1" | tr '[A-Z]' '[a-z]'
}

go() {

  L="$1"
  shift
  if [ "x`locale -a | grep \^$L\$`" != "x$L" ]; then
    echo "$L: Locale not supported, skipping..."
    return
  fi

  ecs="$1"; shift

  echo -n "$L: "
  cs="`LC_ALL=$L $JAVA ${TESTVMOPTS} -cp $TESTCLASSES Default`"
  if [ $? != 0 ]; then
    exit 1
  elif [ "`tolower $cs`" != "`tolower $ecs`" ]; then
    echo "$cs, expected $ecs -- ERROR"
    exit 1
  else
    echo "$cs, as expected"
  fi

}

go  en_US       iso-8859-1
go  ja_JP.utf8  utf-8
go  tr_TR       iso-8859-9
go  C           us-ascii

if [ "$s" = Linux ]; then
  go  ja_JP        x-euc-jp-linux
  go  ja_JP.eucjp  x-euc-jp-linux
  go  ja_JP.ujis   x-euc-jp-linux
  go  ja_JP.utf8   utf-8
fi

# Solaris
if [ "$s" = SunOS ]; then
  go  ja           x-eucjp-open
  go  ja_JP.eucJP  x-eucjp-open
  go  ja_JP.PCK    x-PCK
  go  ja_JP.UTF-8  utf-8
fi
