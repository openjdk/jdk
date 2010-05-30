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
# @run shell/timeout=600 ok-as-delegate-xrealm.sh
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
    ${TESTSRC}${FS}OkAsDelegateXRealm.java \
    ${TESTSRC}${FS}KDC.java \
    ${TESTSRC}${FS}OneKDC.java \
    ${TESTSRC}${FS}Action.java \
    ${TESTSRC}${FS}Context.java \
    || exit 10

# Add $TESTSRC to classpath so that customized nameservice can be used
J="${TESTJAVA}${FS}bin${FS}java -cp $TESTSRC${SEP}."

# KDC no OK-AS-DELEGATE, fail
$J OkAsDelegateXRealm false || exit 1

# KDC set OK-AS-DELEGATE for all, succeed
$J -Dtest.kdc.policy.ok-as-delegate OkAsDelegateXRealm true || exit 2

# KDC set OK-AS-DELEGATE for host/host.r3.local only, fail
$J -Dtest.kdc.policy.ok-as-delegate=host/host.r3.local OkAsDelegateXRealm false || exit 3

# KDC set OK-AS-DELEGATE for all, succeed
$J "-Dtest.kdc.policy.ok-as-delegate=host/host.r3.local krbtgt/R2 krbtgt/R3" OkAsDelegateXRealm true || exit 4

exit 0
