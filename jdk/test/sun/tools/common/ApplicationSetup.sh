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


# Support function to start and stop a given application

# Starts a given application as background process, usage:
#   startApplication <class> [args...]
#
# Waits for application to print something to indicate it is running
# (and initialized). Output is directed to ${TESTCLASSES}/Application.out.
# Sets $pid to be the process-id of the application.

startApplication()
{
  OUTPUTFILE=${TESTCLASSES}/Application.out
  ${JAVA} $1 $2 $3 $4 $5 $6 > ${OUTPUTFILE} &
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
    out=`tail -1 ${OUTPUTFILE}`
    if [ ! -z "$out" ]; then
      break
    fi
    attempts=`expr $attempts + 1`
    echo "Waiting $attempts second(s) ..."
  done

  echo "Application is process $pid"
}

# Stops an application by invoking the given class and argument, usage:
#   stopApplication <class> <argument>
stopApplication()
{
  $JAVA -classpath "${TESTCLASSES}" $1 $2
}

