#
# Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
# @bug 5088398
# @ignore until bug 6835233 dealt with
# @summary Test parallel class loading by parallel transformers.
# @author Daniel D. Daugherty as modified from the code of Daryl Puryear @ Wily
#
# @run shell MakeJAR3.sh ParallelTransformerLoaderAgent
# @run build ParallelTransformerLoaderApp
# @run shell/timeout=240 ParallelTransformerLoader.sh
#

if [ "${TESTJAVA}" = "" ]
then
  echo "TESTJAVA not set.  Test cannot execute.  Failed."
  exit 1
fi

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

JAR="${TESTJAVA}"/bin/jar
JAVAC="${TESTJAVA}"/bin/javac
JAVA="${TESTJAVA}"/bin/java

"${JAVAC}" -d . \
    "${TESTSRC}"/TestClass1.java \
    "${TESTSRC}"/TestClass2.java \
    "${TESTSRC}"/TestClass3.java

"${JAR}" cvf Test.jar Test*.class
# Removing the test class files is important. If these
# .class files are available on the classpath other
# than via Test.jar, then the deadlock will not reproduce.
rm -f Test*.class

"${JAVA}" ${TESTVMOPTS} -javaagent:ParallelTransformerLoaderAgent.jar=Test.jar \
    -classpath "${TESTCLASSES}" ParallelTransformerLoaderApp
result=$?
echo "result=$result"

exit $result
