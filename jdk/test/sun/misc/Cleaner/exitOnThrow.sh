#! /bin/sh

#
# Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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
# @test
# @bug 4954921 8009259
# @summary Ensure that if a cleaner throws an exception then the VM exits
#
# @build ExitOnThrow
# @run shell exitOnThrow.sh

# Command-line usage: sh exitOnThrow.sh /path/to/build

if [ -z "$TESTJAVA" ]; then
  if [ $# -lt 1 ]; then exit 1; fi
  TESTJAVA=$1; shift
  TESTCLASSES=`pwd`
fi

if $TESTJAVA/bin/java ${TESTVMOPTS} -cp $TESTCLASSES ExitOnThrow; then
  echo Failed: VM exited normally
  exit 1
else
  echo Passed: VM exited with code $?
  exit 0
fi
