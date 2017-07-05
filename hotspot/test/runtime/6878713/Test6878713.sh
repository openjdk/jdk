#!/bin/sh

# 
#  Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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

 

##
## @test
## @bug 6878713
## @bug 7030610
## @bug 7037122
## @bug 7123945
## @summary Verifier heap corruption, relating to backward jsrs
## @run shell Test6878713.sh
##
## some tests require path to find test source dir
if [ "${TESTSRC}" = "" ]
then
  TESTSRC=${PWD}
  echo "TESTSRC not set.  Using "${TESTSRC}" as default"
fi
echo "TESTSRC=${TESTSRC}"
## Adding common setup Variables for running shell tests.
. ${TESTSRC}/../../test_env.sh

TARGET_CLASS=OOMCrashClass1960_2

echo "INFO: extracting the target class."
${COMPILEJAVA}${FS}bin${FS}jar xvf \
    ${TESTSRC}${FS}testcase.jar ${TARGET_CLASS}.class

# remove any hs_err_pid that might exist here
rm -f hs_err_pid*.log

echo "INFO: checking for 32-bit versus 64-bit VM."
${TESTJAVA}${FS}bin${FS}java ${TESTVMOPTS} -version 2>&1 \
    | grep "64-Bit [^ ][^ ]* VM" > /dev/null 2>&1
status="$?"
if [ "$status" = 0 ]; then
    echo "INFO: testing a 64-bit VM."
    is_64_bit=true
else
    echo "INFO: testing a 32-bit VM."
fi

if [ "$is_64_bit" = true ]; then
    # limit is 768MB in 8-byte words (1024 * 1024 * 768 / 8) == 100663296
    MALLOC_MAX=100663296
else
    # limit is 768MB in 4-byte words (1024 * 1024 * 768 / 4) == 201326592
    MALLOC_MAX=201326592
fi
echo "INFO: MALLOC_MAX=$MALLOC_MAX"

echo "INFO: executing the target class."
# -XX:+PrintCommandLineFlags for debugging purposes
# -XX:+IgnoreUnrecognizedVMOptions so test will run on a VM without
#     the new -XX:MallocMaxTestWords option
# -XX:+UnlockDiagnosticVMOptions so we can use -XX:MallocMaxTestWords
# -XX:MallocMaxTestWords limits malloc to $MALLOC_MAX
${TESTJAVA}${FS}bin${FS}java \
    -XX:+PrintCommandLineFlags \
    -XX:+IgnoreUnrecognizedVMOptions \
    -XX:+UnlockDiagnosticVMOptions \
    -XX:MallocMaxTestWords=$MALLOC_MAX \
    ${TESTVMOPTS} ${TARGET_CLASS} > test.out 2>&1

echo "INFO: begin contents of test.out:"
cat test.out
echo "INFO: end contents of test.out."

echo "INFO: checking for memory allocation error message."
# We are looking for this specific memory allocation failure mesg so
# we know we exercised the right allocation path with the test class:
MESG1="Native memory allocation (malloc) failed to allocate 25696531[0-9][0-9] bytes"
grep "$MESG1" test.out
status="$?"
if [ "$status" = 0 ]; then
    echo "INFO: found expected memory allocation error message."
else
    echo "INFO: did not find expected memory allocation error message."

    # If we didn't find MESG1 above, then there are several scenarios:
    # 1) -XX:MallocMaxTestWords is not supported by the current VM and we
    #    didn't fail TARGET_CLASS's memory allocation attempt; instead
    #    we failed to find TARGET_CLASS's main() method. The TARGET_CLASS
    #    is designed to provoke a memory allocation failure during class
    #    loading; we actually don't care about running the class which is
    #    why it doesn't have a main() method.
    # 2) we failed a memory allocation, but not the one we were looking
    #    so it might be that TARGET_CLASS no longer tickles the same
    #    memory allocation code path
    # 3) TARGET_CLASS reproduces the failure mode (SIGSEGV) fixed by
    #    6878713 because the test is running on a pre-fix VM.
    echo "INFO: checking for no main() method message."
    MESG2="Error: Main method not found in class"
    grep "$MESG2" test.out
    status="$?"
    if [ "$status" = 0 ]; then
        echo "INFO: found no main() method message."
    else
        echo "FAIL: did not find no main() method message."
        # status is non-zero for exit below

        if [ -s hs_err_pid*.log ]; then
            echo "INFO: begin contents of hs_err_pid file:"
            cat hs_err_pid*.log
            echo "INFO: end contents of hs_err_pid file."
        fi
    fi
fi

if [ "$status" = 0 ]; then
    echo "PASS: test found one of the expected messages."
fi
exit "$status"
