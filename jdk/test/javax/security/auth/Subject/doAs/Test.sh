#! /bin/sh

#
# Copyright (c) 2001, 2012, Oracle and/or its affiliates. All rights reserved.
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

# @test 1.1, 02/14/01
# @author  Ram Marti
# @bug 4399067
# @summary Subject.doAs(null, action) does not clear the executing
#
# ${TESTJAVA} is pointing to the jre
#
# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  SunOS )
    PS=":"
    FS="/"
    RM="/bin/rm -f"
    ;;
  Linux )
    PS=":"
    FS="/"
    RM="/bin/rm -f"
    ;;
  Darwin )
    PS=":"
    FS="/"
    RM="/bin/rm -f"
    ;;
  AIX )
    PS=":"
    FS="/"
    RM="/bin/rm -f"
    ;;
  CYGWIN* )
    PS=";"
    FS="/"
    RM="rm"
    ;;
  Windows* )
    PS=";"
    FS="\\"
    RM="rm"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac
# remove any leftover built class
cd ${TESTCLASSES}${FS}
${RM} Test.class
${COMPILEJAVA}${FS}bin${FS}javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} -d ${TESTCLASSES}${FS} \
    ${TESTSRC}${FS}Test.java
WD=`pwd`
cd ${TESTSRC}${FS}
cd $WD
echo $WD
${TESTJAVA}${FS}bin${FS}java ${TESTVMOPTS} -classpath "${TESTCLASSES}${FS}" \
-Djava.security.manager  \
-Djava.security.policy=${TESTSRC}${FS}policy \
Test

exit $?
