#!/bin/sh

#
# Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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
#
# Application Setup - creates ${TESTCLASSES}/Application.jar and the following
# procedures:
#	startApplication - starts target application
#	stopApplication $1 - stops application via TCP shutdown port $1

$JAVAC -d "${TESTCLASSES}" "${TESTSRC}"/Application.java "${TESTSRC}"/Shutdown.java
$JAR -cfm "${TESTCLASSES}"/Application.jar "${TESTSRC}"/application.mf \
  -C "${TESTCLASSES}" Application.class

OUTPUTFILE=${TESTCLASSES}/Application.out
rm -f ${OUTPUTFILE}

startApplication() 
{
  ${JAVA} $1 $2 $3 -jar "${TESTCLASSES}"/Application.jar > ${OUTPUTFILE} &
  pid="$!"

  # MKS creates an intermediate shell to launch ${JAVA} so
  # ${pid} is not the actual pid. We have put in a small sleep
  # to give the intermediate shell process time to launch the
  # "java" process.
  if [ "$OS" = "Windows" ]; then
    sleep 2
    if [ "${isCygwin}" = "true" ] ; then
      realpid=`ps -p ${pid} | tail -1 | awk '{print $4;}'`
    else
      realpid=`ps -o pid,ppid,comm|grep ${pid}|grep "java"|cut -c1-6`
    fi
    pid=${realpid}
  fi
                                                                                                                  
  echo "Waiting for Application to initialize..."
  attempts=0
  while true; do
    sleep 1
    port=`tail -1 ${OUTPUTFILE} | sed -e 's@\\r@@g' `
    if [ ! -z "$port" ]; then
      # In case of errors wait time for output to be flushed
      sleep 1
      cat ${OUTPUTFILE}
      break
    fi
    attempts=`expr $attempts + 1`
    echo "Waiting $attempts second(s) ..."
  done
  echo "Application is process $pid, shutdown port is $port"
  return $port
}

stopApplication() 
{
  $JAVA -classpath "${TESTCLASSES}" Shutdown $1
}

