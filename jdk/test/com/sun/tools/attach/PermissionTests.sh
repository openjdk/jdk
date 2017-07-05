#!/bin/sh

#
# Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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
# @bug 6173612
# @summary Security manager and permission tests for Attach API
#
# @build PermissionTest 
# @run shell PermissionTests.sh

if [ "${TESTSRC}" = "" ]
then
  echo "TESTSRC not set.  Test cannot execute.  Failed."
  exit 1
fi

. ${TESTSRC}/CommonSetup.sh
. ${TESTSRC}/ApplicationSetup.sh

failures=0

# Start target VM
startApplication
# pid = process-id, port = shutdown port

echo "Deny test"
# deny 
$JAVA -classpath "${TESTCLASSES}${PS}${TESTJAVA}/lib/tools.jar" \
    -Djava.security.manager \
    -Djava.security.policy=${TESTSRC}/java.policy.deny \
    PermissionTest $pid true 2>&1
if [ $? != 0 ]; then failures=`expr $failures + 1`; fi

# allow
echo "Allow test"
$JAVA -classpath "${TESTCLASSES}${PS}${TESTJAVA}/lib/tools.jar" \
    -Djava.security.manager \
    -Djava.security.policy=${TESTSRC}/java.policy.allow \
    PermissionTest $pid false 2>&1 
if [ $? != 0 ]; then failures=`expr $failures + 1`; fi

# Stop target VM
stopApplication $port

if [ $failures = 0 ]; 
  then echo "All tests passed.";
  else echo "$failures test(s) failed:"; cat ${OUTPUTFILE};
fi
exit $failures
