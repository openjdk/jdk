#
# Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
# @bug 7182152
# @bug 8007935
# @summary Redefine a subclass that implements two interfaces and
#   verify that the right methods are called.
# @author Daniel D. Daugherty
#
# @run shell MakeJAR3.sh RedefineSubclassWithTwoInterfacesAgent 'Can-Redefine-Classes: true'
# @run build RedefineSubclassWithTwoInterfacesApp
# @run shell RedefineSubclassWithTwoInterfaces.sh
#

if [ "${TESTJAVA}" = "" ]
then
  echo "TESTJAVA not set.  Test cannot execute.  Failed."
  exit 1
fi

if [ "${COMPILEJAVA}" = "" ]
then
  COMPILEJAVA="${TESTJAVA}"
fi
echo "COMPILEJAVA=${COMPILEJAVA}"

if [ "${TESTSRC}" = "" ]
then
  echo "TESTSRC not set.  Test cannot execute.  Failed."
  exit 1
fi

if [ "${TESTCLASSES}" = "" ]
then
  echo "TESTCLASSES not set.  Test cannot execute.  Failed."
  exit 1
fi

JAVAC="${COMPILEJAVA}"/bin/javac
JAVA="${TESTJAVA}"/bin/java

echo "INFO: building the replacement classes."

cp "${TESTSRC}"/RedefineSubclassWithTwoInterfacesTarget_1.java \
    RedefineSubclassWithTwoInterfacesTarget.java
cp "${TESTSRC}"/RedefineSubclassWithTwoInterfacesImpl_1.java \
    RedefineSubclassWithTwoInterfacesImpl.java
"${JAVAC}" ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} \
    -cp "${TESTCLASSES}" -d . \
    RedefineSubclassWithTwoInterfacesTarget.java \
    RedefineSubclassWithTwoInterfacesImpl.java 
status="$?"
if [ "$status" != 0 ]; then
    echo "FAIL: compile of *_1.java files failed."
    exit "$status"
fi

mv RedefineSubclassWithTwoInterfacesTarget.java \
    RedefineSubclassWithTwoInterfacesTarget_1.java
mv RedefineSubclassWithTwoInterfacesTarget.class \
    RedefineSubclassWithTwoInterfacesTarget_1.class
mv RedefineSubclassWithTwoInterfacesImpl.java \
    RedefineSubclassWithTwoInterfacesImpl_1.java
mv RedefineSubclassWithTwoInterfacesImpl.class \
    RedefineSubclassWithTwoInterfacesImpl_1.class

echo "INFO: launching RedefineSubclassWithTwoInterfacesApp"

# TraceRedefineClasses options:
#
#    0x00000001 |          1 - name each target class before loading, after
#                              loading and after redefinition is completed
#    0x00000002 |          2 - print info if parsing, linking or
#                              verification throws an exception
#    0x00000004 |          4 - print timer info for the VM operation
#    0x00001000 |       4096 - detect calls to obsolete methods
#    0x00002000 |       8192 - fail a guarantee() in addition to detection
#    0x00004000 |      16384 - detect old/obsolete methods in metadata
#    0x00100000 |    1048576 - impl details: vtable updates
#    0x00200000 |    2097152 - impl details: itable updates
#
#    1+2+4+4096+8192+16384+1048576+2097152 == 3174407

"${JAVA}" ${TESTVMOPTS} \
    -XX:TraceRedefineClasses=3174407 \
    -javaagent:RedefineSubclassWithTwoInterfacesAgent.jar \
    -classpath "${TESTCLASSES}" \
    RedefineSubclassWithTwoInterfacesApp > output.log 2>&1
status="$?"

echo "INFO: <begin output.log>"
cat output.log
echo "INFO: <end output.log>"

if [ "$status" != 0 ]; then
    echo "FAIL: RedefineSubclassWithTwoInterfacesApp failed."
    exit "$status"
fi

# When this bug manifests, RedefineClasses() will fail to update
# one of the itable entries to refer to the new method. The log
# will include the following line when the bug occurs:
#
#     guarantee(false) failed: OLD and/or OBSOLETE method(s) found
#
# If this guarantee happens, the test should fail in the status
# check above, but just in case it doesn't, we check for "guarantee".
#

FAIL_MESG="guarantee"
grep "$FAIL_MESG" output.log
status=$?
if [ "$status" = 0 ]; then
    echo "FAIL: found '$FAIL_MESG' in the test output."
    result=1
else
    echo "INFO: did NOT find '$FAIL_MESG' in the test output."
    # be optimistic here
    result=0
fi

PASS1_MESG="before any redefines"
cnt=`grep "$PASS1_MESG" output.log | grep 'version-0' | wc -l`
# no quotes around $cnt so any whitespace from 'wc -l' is ignored
if [ $cnt = 2 ]; then
    echo "INFO: found 2 version-0 '$PASS1_MESG' mesgs."
else
    echo "FAIL: did NOT find 2 version-0 '$PASS1_MESG' mesgs."
    echo "INFO: cnt='$cnt'"
    echo "INFO: grep '$PASS1_MESG' output:"
    grep "$PASS1_MESG" output.log
    result=1
fi

PASS2_MESG="after redefine"
cnt=`grep "$PASS2_MESG" output.log | grep 'version-1' | wc -l`
# no quotes around $cnt so any whitespace from 'wc -l' is ignored
if [ $cnt = 2 ]; then
    echo "INFO: found 2 version-1 '$PASS2_MESG' mesgs."
else
    echo "FAIL: did NOT find 2 version-1 '$PASS2_MESG' mesgs."
    echo "INFO: cnt='$cnt'"
    echo "INFO: grep '$PASS2_MESG' output:"
    grep "$PASS2_MESG" output.log
    result=1
fi

if [ "$result" = 0 ]; then
    echo "PASS: test passed both positive and negative output checks."
fi

exit $result
