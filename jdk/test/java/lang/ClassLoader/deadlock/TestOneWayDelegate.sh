#
# Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
# @bug 4735126
# @summary (cl) ClassLoader.loadClass locks all instances in chain 
#          when delegating
# 
# @run shell/timeout=10 TestOneWayDelegate.sh

# if running by hand on windows, change TESTSRC and TESTCLASSES to "."
if [ "${TESTSRC}" = "" ] ; then
    TESTSRC=`pwd`
fi
if [ "${TESTCLASSES}" = "" ] ; then
    TESTCLASSES=`pwd`
fi

# if running by hand on windows, change this to appropriate value
if [ "${TESTJAVA}" = "" ] ; then
    echo "TESTJAVA not set.  Test cannot execute."
    echo "FAILED!!!"
    exit 1
fi
echo TESTSRC=${TESTSRC}
echo TESTCLASSES=${TESTCLASSES}
echo TESTJAVA=${TESTJAVA}
echo ""

# set platform-specific variables
OS=`uname -s`
case "$OS" in
  SunOS )
    FS="/"
    ;;
  Linux )
    FS="/"
    ;;
  Windows* )
    FS="\\"
    ;;
esac

# compile test
${TESTJAVA}${FS}bin${FS}javac \
        -d ${TESTCLASSES} \
        ${TESTSRC}${FS}Starter.java ${TESTSRC}${FS}DelegatingLoader.java

STATUS=$?
if [ ${STATUS} -ne 0 ]
then
    exit ${STATUS}
fi

# set up test
${TESTJAVA}${FS}bin${FS}javac \
        -d ${TESTCLASSES}${FS} \
        ${TESTSRC}${FS}Alice.java ${TESTSRC}${FS}SupBob.java \
        ${TESTSRC}${FS}Bob.java ${TESTSRC}${FS}SupAlice.java

cd ${TESTCLASSES}
DIRS="SA SB"
for dir in $DIRS
do
    if [ -d ${dir} ]; then
        rm -rf ${dir}
    fi
    mkdir ${dir}
    mv com${dir} ${dir}
done

# run test
${TESTJAVA}${FS}bin${FS}java \
        -verbose:class -XX:+TraceClassLoading -cp . \
        -Dtest.classes=${TESTCLASSES} \
        Starter one-way
# -XX:+UnlockDiagnosticVMOptions -XX:+UnsyncloadClass \

# save error status
STATUS=$?

# clean up
rm -rf ${TESTCLASSES}${FS}SA ${TESTCLASSES}${FS}SB

# return
exit ${STATUS}
