#!/bin/sh

#
# Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4673940
# @bug 4930794
# @summary Unit tests for inetd feature
#
# @build StateTest StateTestService EchoTest EchoService CloseTest Launcher Util
# @run shell run_tests.sh

os=`uname -s`

if [ "$os" != "Linux" -a "$os" != "SunOS" ]; then
    echo "Test not designed to run on this operating system, skipping..."
    exit 0
fi


# if TESTJAVA isn't set then we assume an interactive run. So that it's
# clear which version of 'java' is running we do a 'which java' and
# a 'java -version'.

if [ -z "$TESTJAVA" ]; then
    TESTSRC=`pwd`
    TESTCLASSES=`pwd`
    JAVA=java
    which $JAVA
    ${JAVA} -d64 -version > /dev/null 2<&1
    if [ $? = 1 ]; then
	${JAVA} -version
    else
	${JAVA} -d64 -version
    fi
else
    JAVA="${TESTJAVA}/bin/java"
fi

CLASSPATH=${TESTCLASSES}
export CLASSPATH


# Check that we have libLauncher.so for the right platform.
# On Solaris we assume 64-bit if java -d64 works.

DFLAG=
if [ "$os" = "SunOS" ]; then
    PLATFORM=solaris
    case "`uname -p`" in
	i[3-9]86) 
	    ARCH=i586
	    ;;
	sparc)
	    ARCH=sparc
	    ${JAVA} -d64 -version > /dev/null 2<&1 
	    if [ $? = 1 ]; then
	        ARCH=sparc
	    else
		ARCH=sparcv9
		DFLAG=-d64
	    fi
	    ;;
    esac 
fi

if [ "$os" = "Linux" ]; then
    PLATFORM=linux
    ARCH=unknown
    case "`uname -m`" in
	i[3-6]86)
	    ARCH=i586
	    ;;
	ia64)
	    ARCH=ia64
	    ;;
	x86_64)
	    ARCH=amd64
	    ;;
    esac
fi

LIBDIR=lib/${PLATFORM}-${ARCH}
LAUNCHERLIB=${LIBDIR}/libLauncher.so
echo $LIBDIR

if [ ! -f "${TESTSRC}/${LAUNCHERLIB}" ]; then
    echo "Cannot find ${LAUNCHERLIB} - library not available for this system"
    exit 0
fi

LD_LIBRARY_PATH=${TESTSRC}/${LIBDIR}
export LD_LIBRARY_PATH

failures=0

go() {
    echo ''
    sh -xc "$JAVA $DFLAG $1 $2 $3 $4 $5 $6 $7 $8" 2>&1
    if [ $? != 0 ]; then failures=`expr $failures + 1`; fi
}

# Run the tests

go StateTest
go EchoTest
go CloseTest

# Re-run StateTest with a SecurityManager set
# Note that the system properties are arguments to StateTest and not options.
# These system properties are passed to the launched service as options:
#   java [-options] class [args...]

go StateTest -Djava.security.manager -Djava.security.policy=${TESTSRC}/java.policy.pass
go StateTest -expectFail -Djava.security.manager -Djava.security.policy=${TESTSRC}/java.policy.fail


#
# Results
#
echo ''
if [ $failures -gt 0 ];
  then echo "$failures test(s) failed";
  else echo "All test(s) passed"; fi
exit $failures
