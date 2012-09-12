#
# Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
#  @test
#  @bug 7056731
#  @summary Race condition in CORBA code causes re-use of ABORTed connections
#
#  @run shell 7056731.sh
#

OS=`uname -s`
case "$OS" in
  SunOS | Linux | Darwin )
    PS=":"
    FS="/"
    ;;
  Windows* | CYGWIN* )
    PS=";"
    FS="\\"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

if [ "${TESTJAVA}" = "" ] ; then
  echo "TESTJAVA not set.  Test cannot execute.  Failed."
  exit 1
fi

JAVA="${TESTJAVA}${FS}bin${FS}java"
PORT=1052
cp -r ${TESTSRC}${FS}*.java  ${TESTSRC}${FS}Hello.idl .
echo "Testing...please wait"

${TESTJAVA}${FS}bin${FS}idlj -fall Hello.idl
${TESTJAVA}${FS}bin${FS}javac *.java HelloApp/*.java

echo "starting orbd"
${TESTJAVA}${FS}bin${FS}orbd -ORBInitialPort $PORT -ORBInitialHost localhost &
ORB_PROC=$!
sleep 2 #give orbd time to start
echo "started orb"
echo "starting server"
${TESTJAVA}${FS}bin${FS}java -cp . HelloServer -ORBInitialPort $PORT -ORBInitialHost localhost &
SERVER_PROC=$!
sleep 2 #give server time to start
echo "started server"
echo "starting client (debug mode)"
${TESTJAVA}${FS}bin${FS}java -cp . -agentlib:jdwp=transport=dt_socket,server=y,address=8000 HelloClient -ORBInitialPort $PORT -ORBInitialHost localhost > client.$$ 2>&1 & 
JVM_PROC=$!
sleep 2 #give jvm/debugger/client time to start

echo "started client (debug mode)"
echo "starting debugger and issuing commands"
(sleep 2;
echo "stop in com.sun.corba.se.impl.protocol.CorbaClientRequestDispatcherImpl.unregisterWaiter";
sleep 2;
echo "run";
sleep 2;
echo "cont";
sleep 2;
echo "cont";
sleep 2;
echo "cont";
sleep 2;
echo "suspend 1";
sleep 2;
kill -9 $SERVER_PROC &> /dev/null;
sleep 2;
echo "cont";
sleep 2;
echo "thread 1"
sleep 2;
echo "clear com.sun.corba.se.impl.protocol.CorbaClientRequestDispatcherImpl.unregisterWaiter"
sleep 2;
echo "resume 1";
)| ${TESTJAVA}${FS}bin${FS}jdb -connect com.sun.jdi.SocketAttach:hostname=localhost,port=8000

sleep 5 # give time for Client to throw exception

# JVM_PROC should have exited but just in case, include it.
kill -9 $ORB_PROC $JVM_PROC

grep "ORBUtilSystemException.writeErrorSend" client.$$
result=$?
if [ $result -eq 0 ]
then
    echo "Failed"
    exitCode=1;
else 
    echo "Passed"
    exitCode=0
fi

#jtreg complaining about not being able to clean up; let's sleep
sleep 2
rm -rf out.$$ client.$$
sleep 2
exit ${exitCode}
