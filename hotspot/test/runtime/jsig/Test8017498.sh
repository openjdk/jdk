#!/bin/sh

#
#  Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
## @test Test8017498.sh
## @bug 8017498
## @summary sigaction(sig) results in process hang/timed-out if sig is much greater than SIGRTMAX
## @run shell Test8017498.sh
##

if [ "${TESTSRC}" = "" ]
then
  TESTSRC=${PWD}
  echo "TESTSRC not set.  Using "${TESTSRC}" as default"
fi
echo "TESTSRC=${TESTSRC}"
## Adding common setup Variables for running shell tests.
. ${TESTSRC}/../../test_env.sh

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  Linux)
    echo "Testing on Linux"
    if [ "$VM_BITS" = "64" ]
    then
        LD_PRELOAD=${TESTJAVA}${FS}jre${FS}lib${FS}amd64${FS}libjsig.so
    else
        LD_PRELOAD=${TESTJAVA}${FS}jre${FS}lib${FS}i386${FS}libjsig.so
    fi
    echo LD_PRELOAD = ${LD_PRELOAD}
    export LD_PRELOAD=${LD_PRELOAD}
    ;;
  *)
    NULL=NUL
    PS=";"
    FS="\\"
    echo "Test passed; only valid for Linux"
    exit 0;
    ;;
esac

THIS_DIR=.

cp ${TESTSRC}${FS}*.java ${THIS_DIR}
${TESTJAVA}${FS}bin${FS}javac *.java

gcc -fPIC -shared -o ${TESTSRC}${FS}libTestJNI.so -I${TESTJAVA}${FS}include -I${TESTJAVA}${FS}include${FS}linux ${TESTSRC}${FS}TestJNI.c

# run the java test in the background
echo ${TESTJAVA}${FS}bin${FS}java -Djava.library.path=${TESTSRC}${FS} -server TestJNI 100 > test.out 2>&1 &
${TESTJAVA}${FS}bin${FS}java -Djava.library.path=${TESTSRC}${FS} -server TestJNI 100 > test.out 2>&1 &

# obtain the process id
C_PID=$!

# sleep for 1s
sleep 1

# reset LD_PRELOAD
unset LD_PRELOAD

# check the output file (test.out)
grep "old handler" test.out > ${NULL}
if [ $? = 0 ]
then
    echo "Test Passed"
    exit 0
else
    kill -9 ${C_PID}
    echo "Test Failed"
    exit 1
fi
