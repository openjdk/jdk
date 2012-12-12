#
# Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
# @bug 7102369 7094468 7100592
# @library ../../testlibrary
# @build TestLibrary
# @summary remove java.rmi.server.codebase property parsing from RegistryImpl
# @run shell readTest.sh

OS=`uname -s`
case "$OS" in
  SunOS | Linux | Darwin )
    PS=":"
    FS="/"
    FILEURL="file:"
    ;;
  Windows* )
    PS=";"
    FS="\\"
    FILEURL="file:/"
    ;;
  CYGWIN* )
    PS=";"
    FS="/"
    FILEURL="file:/"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

TEST_CLASSPATH=.$PS${TESTCLASSPATH:-$TESTCLASSES}
cp -r ${TESTSRC}${FS}* .
${TESTJAVA}${FS}bin${FS}javac testPkg${FS}*java
${TESTJAVA}${FS}bin${FS}javac -cp $TEST_CLASSPATH readTest.java

mkdir rmi_tmp
RMIREG_OUT=rmi.out
#start rmiregistry without any local classes on classpath
cd rmi_tmp
# NOTE: This RMI Registry port must match TestLibrary.READTEST_REGISTRY_PORT
${TESTJAVA}${FS}bin${FS}rmiregistry 64005 > ..${FS}${RMIREG_OUT} 2>&1 &
RMIREG_PID=$!
# allow some time to start
sleep 3
cd ..

case "$OS" in
  CYGWIN* )
    CODEBASE=`cygpath -w $PWD`
    ;;
  * )
    CODEBASE=`pwd`
    ;;  
esac
# trailing / after code base is important for rmi codebase property.
${TESTJAVA}${FS}bin${FS}java -cp $TEST_CLASSPATH -Djava.rmi.server.codebase=${FILEURL}$CODEBASE/ readTest > OUT.TXT 2>&1 &
TEST_PID=$!
#bulk of testcase - let it run for a while
sleep 5

#we're done, kill processes first
kill -9 ${RMIREG_PID} ${TEST_PID}
sleep 3

echo "Test output : "

cat OUT.TXT
echo "=============="
echo "rmiregistry output  : "
cat ${RMIREG_OUT}
echo "=============="

grep "Server ready" OUT.TXT
result1=$?
grep "Test passed" OUT.TXT
result2=$?

if [ $result1 -eq 0  -a $result2 -eq 0 ]
then 
    echo "Passed"
    exitCode=0;
else
    echo "Failed"
    exitCode=1
fi
rm -rf OUT.TXT ${RMIREG_OUT} rmi_tmp
exit ${exitCode}    


