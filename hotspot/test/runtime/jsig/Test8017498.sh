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
## @ignore 8028806
## @test Test8017498.sh
## @bug 8017498
## @bug 8020791
## @bug 8021296
## @summary sigaction(sig) results in process hang/timed-out if sig is much greater than SIGRTMAX
## @run shell/timeout=30 Test8017498.sh
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
    gcc_cmd=`which gcc`
    if [ "x$gcc_cmd" == "x" ]; then
        echo "WARNING: gcc not found. Cannot execute test." 2>&1
        exit 0;
    fi
    if [ "$VM_BITS" = "64" ]
    then
        MY_LD_PRELOAD=${TESTJAVA}${FS}jre${FS}lib${FS}amd64${FS}libjsig.so
    else
        MY_LD_PRELOAD=${TESTJAVA}${FS}jre${FS}lib${FS}i386${FS}libjsig.so
    fi
    echo MY_LD_PRELOAD = ${MY_LD_PRELOAD}
    ;;
  *)
    echo "Test passed; only valid for Linux"
    exit 0;
    ;;
esac

THIS_DIR=.

cp ${TESTSRC}${FS}*.java ${THIS_DIR}
${TESTJAVA}${FS}bin${FS}javac *.java

$gcc_cmd -DLINUX -fPIC -shared \
    -o ${TESTSRC}${FS}libTestJNI.so \
    -I${TESTJAVA}${FS}include \
    -I${TESTJAVA}${FS}include${FS}linux \
    ${TESTSRC}${FS}TestJNI.c

# run the java test in the background
cmd="LD_PRELOAD=$MY_LD_PRELOAD \
    ${TESTJAVA}${FS}bin${FS}java \
    -Djava.library.path=${TESTSRC}${FS} -server TestJNI 100"
echo "$cmd > test.out 2>&1"
eval $cmd > test.out 2>&1

grep "old handler" test.out > ${NULL}
if [ $? = 0 ]
then
    echo "Test Passed"
    exit 0
fi

echo "Test Failed"
exit 1
