#
#  Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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


# @test Test7162488.sh
# @bug 7162488
# @summary VM not printing unknown -XX options
# @run shell Test7162488.sh
#

if [ "${TESTSRC}" = "" ]
then
  TESTSRC=${PWD}
  echo "TESTSRC not set.  Using "${TESTSRC}" as default"
fi
echo "TESTSRC=${TESTSRC}"
## Adding common setup Variables for running shell tests.
. ${TESTSRC}/../../test_env.sh

JAVA=${TESTJAVA}${FS}bin${FS}java

#
# Just run with an option we are confident will not be recognized,
# and check for the message:
#
OPTION=this_is_not_an_option

${JAVA} -showversion -XX:${OPTION} 2>&1 | grep "Unrecognized VM option" 
if [ "$?" != "0" ]
then
  printf "FAILED: option not flagged as unrecognized.\n"
  exit 1
fi

${JAVA} -showversion -XX:${OPTION} 2>&1 | grep ${OPTION}
if [ "$?" != "0" ]
then
  printf "FAILED: bad option not named as being bad.\n"
  exit 1
fi

printf "Passed.\n"

