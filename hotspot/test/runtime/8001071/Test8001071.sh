#!/bin/sh

# Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.

# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.

# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).

# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.

# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.

## @test
## @bug 8001071
## @summary Add simple range check into VM implemenation of Unsafe access methods 
## @compile Test8001071.java
## @run shell Test8001071.sh
## @author filipp.zhinkin@oracle.com

VERSION=`${TESTJAVA}/bin/java ${TESTVMOPTS} -version 2>&1`

if [ -n "`echo $VERSION | grep debug`" -o -n "`echo $VERSION | grep jvmg`" ]; then
        echo "Build type check passed"
        echo "Continue testing"
else
        echo "Fastdebug build is required for this test"
        exit 0
fi

${TESTJAVA}/bin/java -cp ${TESTCLASSES} ${TESTVMOPTS} Test8001071 2>&1

HS_ERR_FILE=hs_err_pid*.log

if [ ! -f $HS_ERR_FILE ]
then
    echo "hs_err_pid log file was not found"
    echo "Test failed"
    exit 1
fi

grep "assert(byte_offset < p_size) failed: Unsafe access: offset.*> object's size.*" $HS_ERR_FILE

if [ "0" = "$?" ];
then
    echo "Range check assertion failed as expected"
    echo "Test passed"
    exit 0
else
    echo "Range check assertion was not failed"
    echo "Test failed"
    exit 1
fi
