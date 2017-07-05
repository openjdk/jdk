#!/bin/sh

#
# Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
# @bug 6829503
# @summary  1) Test Console and DeleteOnExitHook can be initialized
#              while shutdown is in progress
#           2) Test if files that are added by the application shutdown
#              hook are deleted on exit during shutdown
#
# @build ShutdownHooks 
# @run shell ShutdownHooks.sh

if [ "${TESTJAVA}" = "" ]
then
  echo "TESTJAVA not set.  Test cannot execute.  Failed."
  exit 1
fi

FILENAME=fileToBeDeleted
rm -f ${TESTCLASSES}/${FILENAME}

# create the file to be deleted on exit
echo "testing shutdown" > ${TESTCLASSES}/${FILENAME}

${TESTJAVA}/bin/java ${TESTVMOPTS} -classpath ${TESTCLASSES} ShutdownHooks ${TESTCLASSES} $FILENAME 
if [ $? != 0 ] ; then
  echo "Test Failed"; exit 1
fi

if [ -f ${TESTCLASSES}/${FILENAME} ]; then
  echo "Test Failed: ${TESTCLASSES}/${FILENAME} not deleted"; exit 2
fi
echo "ShutdownHooks test passed.";
