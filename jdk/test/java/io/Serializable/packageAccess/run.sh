#
# Copyright (c) 2002, 2013, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4765255
# @summary Verify proper functioning of package equality checks used to
#          determine accessibility of superclass constructor and inherited
#          writeReplace/readResolve methods.

if [ "${TESTJAVA}" = "" ]
then
    echo "TESTJAVA not set.  Test cannot execute.  Failed."
exit 1
fi

if [ "${COMPILEJAVA}" = "" ] ; then
    COMPILEJAVA="${TESTJAVA}"
fi

if [ "${TESTSRC}" = "" ]
then
    TESTSRC="."
fi

set -ex

${COMPILEJAVA}/bin/javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} -d . \
    ${TESTSRC}/A.java ${TESTSRC}/B.java ${TESTSRC}/C.java ${TESTSRC}/D.java \
    ${TESTSRC}/Test.java
${COMPILEJAVA}/bin/jar ${TESTTOOLVMOPTS} cf foo.jar B.class D.class
rm -f B.class D.class

${TESTJAVA}/bin/java ${TESTVMOPTS} Test
rm -f *.class *.jar
