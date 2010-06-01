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
# @bug 6853328
# @summary Support OK-AS-DELEGATE flag
# @run shell/timeout=600 ok-as-delegate.sh
#

if [ "${TESTSRC}" = "" ] ; then
  TESTSRC=`dirname $0`
fi

if [ "${TESTJAVA}" = "" ] ; then
  JAVAC_CMD=`which javac`
  TESTJAVA=`dirname $JAVAC_CMD`/..
fi

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  Windows_* )
    FS="\\"
    SEP=";"
    ;;
  CYGWIN* )
    FS="/"
    SEP=";"
    ;;
  * )
    FS="/"
    SEP=":"
    ;;
esac

${TESTJAVA}${FS}bin${FS}javac -XDignore.symbol.file -d . \
    ${TESTSRC}${FS}OkAsDelegate.java \
    ${TESTSRC}${FS}KDC.java \
    ${TESTSRC}${FS}OneKDC.java \
    ${TESTSRC}${FS}Action.java \
    ${TESTSRC}${FS}Context.java \
    || exit 10

# Testing Kerberos 5

# Add $TESTSRC to classpath so that customized nameservice can be used
J="${TESTJAVA}${FS}bin${FS}java -cp $TESTSRC${SEP}. OkAsDelegate"
JOK="${TESTJAVA}${FS}bin${FS}java -cp $TESTSRC${SEP}. -Dtest.kdc.policy.ok-as-delegate OkAsDelegate"

# FORWARDABLE ticket not allowed, always fail
$J false true true false false false || exit 1

# Service ticket no OK-AS-DELEGATE

# Request nothing, gain nothing
$J true false false false false false || exit 2
# Request deleg policy, gain nothing
$J true false true false false false || exit 3
# Request deleg, granted
$J true true false true false true || exit 4
# Request deleg and deleg policy, granted, with info not by policy
$J true true true true false true || exit 5

# Service ticket has OK-AS-DELEGATE

# Request deleg policy, granted
$JOK true false true true true true || exit 6
# Request deleg and deleg policy, granted, with info by policy
$JOK true true true true true true || exit 7

# Testing SPNEGO

# Add $TESTSRC to classpath so that customized nameservice can be used
J="${TESTJAVA}${FS}bin${FS}java -cp $TESTSRC${SEP}. -Dtest.spnego OkAsDelegate"
JOK="${TESTJAVA}${FS}bin${FS}java -cp $TESTSRC${SEP}. -Dtest.spnego -Dtest.kdc.policy.ok-as-delegate OkAsDelegate"

# FORWARDABLE ticket not allowed, always fail
$J false true true false false false || exit 11

# Service ticket no OK-AS-DELEGATE

# Request nothing, gain nothing
$J true false false false false false || exit 12
# Request deleg policy, gain nothing
$J true false true false false false || exit 13
# Request deleg, granted
$J true true false true false true || exit 14
# Request deleg and deleg policy, granted, with info not by policy
$J true true true true false true || exit 15

# Service ticket has OK-AS-DELEGATE

# Request deleg policy, granted
$JOK true false true true true true || exit 16
# Request deleg and deleg policy, granted, with info by policy
$JOK true true true true true true || exit 17

exit 0
