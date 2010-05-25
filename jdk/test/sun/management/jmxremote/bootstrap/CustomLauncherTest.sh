#!/bin/sh

#
# Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
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
# @bug 6434402
# @summary Start an application using a custom launcher and check that
#          a management tool can connect.
#
# @build TestManager TestApplication
# @run shell CustomLauncherTest.sh

#
# Check we are run from jtreg
#
if [ -z "${TESTCLASSES}" ]; then
    echo "Test is designed to be run from jtreg only"
    exit 0
fi

#
# For now this test passes silently on Windows - this means the test only
# has to locate libjvm.so. Also $! is not reliable on some releases of MKS.
#{
OS=`uname -s`
if [ "$OS" != "Linux" -a "$OS" != "SunOS" ]; then
    echo "Test not designed to run on this operating system, skipping..."
    exit 0
fi

#
# Locate the custom launcher for this platform
#
PLATFORM=unknown
ARCH=unknown
if [ "$OS" = "SunOS" ]; then
    PLATFORM=solaris
    case "`uname -p`" in
	i[3-9]86)
	    ARCH=i586
	    ;;
	sparc)
	    ARCH=sparc
	    ;;
    esac
else
    PLATFORM=linux
    case "`uname -m`" in
	i[3-6]86)
	    ARCH=i586
	    ;;
	x86_64)
	    ARCH=amd64
	    ;;
    esac
fi


#
# On x86 the native libraries are in lib/i386 for
# compatability reasons
#
if [ "$ARCH" = "i586" ]; then
    LIBARCH="i386"
else
    LIBARCH=$ARCH
fi


#
# Check that a custom launcher exists for this platform
#
LAUNCHER="${TESTSRC}/${PLATFORM}-${ARCH}/launcher"
if [ ! -x "${LAUNCHER}" ]; then
    echo "${LAUNCHER} not found"
    exit 0
fi

# 
# Locate the libjvm.so library 
#
JVMLIB="${TESTJAVA}/jre/lib/${LIBARCH}/client/libjvm.so"
if [ ! -f "${JVMLIB}" ]; then
    JVMLIB="${TESTJAVA}/jre/lib/${LIBARCH}/server/libjvm.so"
    if [ ! -f "${JVMLIB}" ]; then
	JVMLIB="${TESTJAVA}/lib/${LIBARCH}/client/libjvm.so"
	if [ ! -f "${JVMLIB}" ]; then
	    JVMLIB="${TESTJAVA}/lib/${LIBARCH}/serevr/libjvm.so"
	    if [ ! -f "${JVMLIB}" ]; then
		echo "Unable to locate libjvm.so in ${TESTJAVA}"
		exit 1
	    fi
	fi
    fi
fi

#
# Start the VM
#
outputfile=${TESTCLASSES}/Test.out
rm -f ${outputfile}

echo ''
echo "Starting custom launcher..."
echo " launcher: ${LAUNCHER}"
echo "   libjvm: ${JVMLIB}"
echo "classpath: ${TESTCLASSES}"


${LAUNCHER} ${JVMLIB} ${TESTCLASSES} TestApplication > ${outputfile} &
pid=$!

# Wait for managed VM to startup (although this looks like a potentially
# infinate loop, the framework will eventually kill it)
echo "Waiting for TestAppication to test..."
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
${TESTJAVA}/bin/java -classpath ${TESTCLASSES}:${TESTJAVA}/lib/tools.jar \
  TestManager $pid $port true
if [ $? != 0 ]; then 
    echo "Test failed"
    exit 1
fi
exit 0
