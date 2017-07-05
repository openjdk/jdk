#!/bin/sh

#
# Copyright (c) 2004, 2006, Oracle and/or its affiliates. All rights reserved.
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
# @bug 5016507 6173612 6319776 6342019 6484550
# @summary Start a managed VM and test that a management tool can connect
#          without connection or username/password details.
#          TestManager will attempt a connection to the address obtained from 
#          both agent properties and jvmstat buffer.
#
# @build TestManager TestApplication
# @run shell/timeout=300 LocalManagementTest.sh


doTest()
{
    echo ''

    outputfile=${TESTCLASSES}/Test.out
    rm -f ${outputfile}

    # Start VM with given options
    echo "+ $JAVA $1 Test"
    $JAVA $1 TestApplication > ${outputfile}&
    pid=$!
 
    # Wait for managed VM to startup
    echo "Waiting for VM to startup..."
    attempts=0
    while true; do
        sleep 1
  	port=`tail -1 ${outputfile}`
  	if [ ! -z "$port" ]; then
     	    # In case of errors wait time for output to be flushed
     	    sleep 1
     	    cat ${outputfile}
     	    break
	fi
      attempts=`expr $attempts + 1`
      echo "Waiting $attempts second(s) ..."
    done

    # Start the manager - this should connect to VM
    sh -xc "$JAVA -classpath ${TESTCLASSES}:${TESTJAVA}/lib/tools.jar \
        TestManager $pid $port"  2>&1
    if [ $? != 0 ]; then failures=`expr $failures + 1`; fi
}


# Check we are run from jtreg
if [ -z "${TESTCLASSES}" ]; then
    echo "Test is designed to be run from jtreg only"
    exit 0
fi

# For now this test passes silently on Windows - there are 2 reasons
# to skip it :-
#
# 1. No jstat instrumentation buffers if FAT32 so need
#    -XX:+PerfBypassFileSystemCheck 
# 2. $! is used to get the pid of the created process but it's not
#    reliable on older versions of MKS. Also negative pids are returned
#    on Windows 98.

os=`uname -s`
if [ "$os" != "Linux" -a "$os" != "SunOS" ]; then
    echo "Test not designed to run on this operating system, skipping..."
    exit 0
fi

JAVA=${TESTJAVA}/bin/java
CLASSPATH=${TESTCLASSES}
export CLASSPATH

failures=0

# Test 1 
doTest "-Dcom.sun.management.jmxremote" 

# Test 2
AGENT="${TESTJAVA}/jre/lib/management-agent.jar"
if [ ! -f ${AGENT} ]; then
  AGENT="${TESTJAVA}/lib/management-agent.jar"
fi
doTest "-javaagent:${AGENT}" 

# Test 3 - no args (blank) - manager should attach and start agent
doTest " " 

# Test 4 - sanity check arguments to management-agent.jar
echo ' '
sh -xc "${JAVA} -javaagent:${AGENT}=com.sun.management.jmxremote.port=7775,\
com.sun.management.jmxremote.authenticate=false,com.sun.management.jmxremote.ssl=false \
  TestApplication -exit" 2>&1
if [ $? != 0 ]; then failures=`expr $failures + 1`; fi

# Test 5 - use DNS-only name service
doTest "-Dsun.net.spi.namservice.provider.1=\"dns,sun\"" 

#
# Results
#
echo ''
if [ $failures -gt 0 ];
  then echo "$failures test(s) failed";
  else echo "All test(s) passed"; fi
exit $failures

