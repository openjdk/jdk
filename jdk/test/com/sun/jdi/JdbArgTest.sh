#!/bin/sh

#
# Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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

#  @test
#  @bug 4684386
#  @summary TTY: jdb throws IllegalArumentException on cmd line args
#  @author Jim/suvasis
#  @run shell JdbArgTest.sh

#Call this from anywhere to fail the test with an error message
# usage: fail "reason why the test failed"
fail() 
 { echo "The test failed :-("
   echo "$*" 1>&2
   echo "exit status was $status"
   exit 1
 } #end of fail()

#Call this from anywhere to pass the test with a message
# usage: pass "reason why the test passed if applicable"
pass() 
 { echo "The test passed!!!"
   echo "$*" 1>&2
   exit 0
 } #end of pass()

# end of subroutines

#Set appropriate jdk 

if [ ! -z "${TESTJAVA}" ] ; then
     jdk="$TESTJAVA"
else
     echo "--Error: TESTJAVA must be defined as the pathname of a jdk to test."
     exit 1
fi

echo quit | \
   $TESTJAVA/bin/jdb Server 0RBDebug subcontract,shutdown,transport 2>&1 | \
   fgrep IllegalArgumentException > /dev/null 2<&1

if [ $? = 1 ] ; then
   pass " This test passed and jbd got no IllegalArgumentException"
fi

fail "FAILED: jdb got an IllegalArgumentException"

