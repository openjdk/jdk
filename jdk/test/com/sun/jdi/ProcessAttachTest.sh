#!/bin/sh

#
# Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.
#


# @test
# @bug 4527279
# @summary Unit test for ProcessAttachingConnector
#
# @build ProcessAttachDebugger ProcessAttachDebuggee ShutdownDebuggee
# @run shell ProcessAttachTest.sh

if [ "${TESTJAVA}" = "" ]
then
  echo "TESTJAVA not set.  Test cannot execute.  Failed."
  exit 1
fi
                                                                                                     
if [ "${TESTSRC}" = "" ]
then
  echo "TESTSRC not set.  Test cannot execute.  Failed."
  exit 1
fi
                                                                                                     
if [ "${TESTCLASSES}" = "" ]
then
  echo "TESTCLASSES not set.  Test cannot execute.  Failed."
  exit 1
fi
                                                                                                     
JAVA="${TESTJAVA}/bin/java"

OS=`uname -s`

case "$OS" in
  Windows*)
    PS=";"
    OS="Windows"
    ;;
  CYGWIN*)
    PS=";"
    OS="CYGWIN"
    ;;
  * )
    PS=":"
    ;;
esac

startDebuggee()
{
  OUTPUTFILE=${TESTCLASSES}/Debuggee.out
  ${JAVA} "$@" > ${OUTPUTFILE} &
  startpid="$!"
  pid="${startpid}"
                                                                                                     
  # CYGWIN startpid is not the native windows PID we want, get the WINPID
  if [ "${OS}" = "CYGWIN" ]; then
    sleep 2
    ps -l -p ${startpid}
    pid=`ps -l -p ${startpid} | tail -1 | awk '{print $4;}'`
  fi
  
  # MKS creates an intermediate shell to launch ${JAVA} so
  # ${startpid} is not the actual pid. We have put in a small sleep
  # to give the intermediate shell process time to launch the
  # "java" process.
  if [ "$OS" = "Windows" ]; then
    sleep 2
    pid=`ps -o pid,ppid,comm|grep ${startpid}|grep "java"|cut -c1-6`
  fi
                                                                                                     
  echo "Waiting for Debuggee to initialize..."
  attempts=0
  while true; do
    sleep 1
    out=`tail -1 ${OUTPUTFILE}`
    if [ ! -z "$out" ]; then
      break
    fi
    attempts=`expr $attempts + 1`
    echo "Waiting $attempts second(s) ..."
  done

  echo "Debuggee is process $pid (startpid=${startpid})"
}

stopDebuggee()
{
  $JAVA -classpath "${TESTCLASSES}" ShutdownDebuggee $1
  if [ $? != 0 ] ; then
    echo "Error: ShutdownDebuggee failed"
    failures=`expr $failures + 1`
    kill -9 ${startpid}
  fi
}

failures=0

#########################################################
echo "Test 1: Debuggee start with suspend=n"

PORTFILE="${TESTCLASSES}"/shutdown1.port

DEBUGGEEFLAGS=
if [ -r $TESTCLASSES/@debuggeeVMOptions ] ; then
   DEBUGGEEFLAGS=`cat $TESTCLASSES/@debuggeeVMOptions`
elif [ -r $TESTCLASSES/../@debuggeeVMOptions ] ; then
   DEBUGGEEFLAGS=`cat $TESTCLASSES/../@debuggeeVMOptions`
fi

startDebuggee \
  $DEBUGGEEFLAGS \
  -agentlib:jdwp=transport=dt_socket,server=y,suspend=n \
  -classpath "${TESTCLASSES}" ProcessAttachDebuggee "${PORTFILE}"

$JAVA -classpath "${TESTCLASSES}${PS}${TESTJAVA}/lib/tools.jar" \
  ProcessAttachDebugger $pid 2>&1
if [ $? != 0 ]; then failures=`expr $failures + 1`; fi

# Note that when the debugger disconnects, the debuggee picks another
# port and outputs another 'Listening for transport ... ' msg.

stopDebuggee "${PORTFILE}"

#########################################################
echo "\nTest 2: Debuggee start with suspend=y"

PORTFILE="${TESTCLASSES}"/shutdown2.port
startDebuggee \
  $DEBUGGEEFLAGS \
  -agentlib:jdwp=transport=dt_socket,server=y,suspend=y \
  -classpath "${TESTCLASSES}" ProcessAttachDebuggee "${PORTFILE}"

$JAVA -classpath "${TESTCLASSES}${PS}${TESTJAVA}/lib/tools.jar" \
  ProcessAttachDebugger $pid 2>&1

# The debuggee is suspended and doesn't run until the debugger
# disconnects.  We have to give it time to write the port number
# to ${PORTFILE}
sleep 10

if [ $? != 0 ]; then failures=`expr $failures + 1`; fi
stopDebuggee "${PORTFILE}"

### 
if [ $failures = 0 ];
  then echo "All tests passed.";
  else echo "$failures test(s) failed:"; cat ${OUTPUTFILE};
fi
exit $failures
