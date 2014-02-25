#
# Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4413434
# @summary Verify that class loaded outside of application class loader is
#          correctly resolved during deserialization when read in by custom
#          readObject() method of a bootstrap class (in this case,
#          java.util.Vector).

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

rm -f *.class *.jar
${COMPILEJAVA}/bin/javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} -d . ${TESTSRC}/Foo.java
${COMPILEJAVA}/bin/jar ${TESTTOOLVMOPTS} cf cb.jar *.class
rm -f *.class
${COMPILEJAVA}/bin/javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} -d . ${TESTSRC}/Test.java
${TESTJAVA}/bin/java ${TESTVMOPTS} Test
rm -f *.class *.jar
