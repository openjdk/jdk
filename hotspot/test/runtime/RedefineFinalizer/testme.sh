#!/bin/sh

# Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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


# @test
# @bug 6904403
# @summary Don't assert if we redefine finalize method
# @run shell testme.sh

# This test shouldn't provoke and assert(f == k->has_finalizer()) failed: inconsistent has_finalizer

. ${TESTSRC}/../../test_env.sh

JAVAC=${COMPILEJAVA}${FS}bin${FS}javac
JAR=${COMPILEJAVA}${FS}bin${FS}jar
JAVA=${TESTJAVA}${FS}bin${FS}java

TOOLS_JAR=${TESTJAVA}${FS}lib${FS}tools.jar

cp ${TESTSRC}${FS}*.java .
${JAVAC} -XDignore.symbol.file -classpath ${TOOLS_JAR} -sourcepath ${TESTSRC} *.java
if [ $? -eq 1 ]
  then
    echo "Compilation failed"
    exit
fi

${JAR} cvfm testcase.jar ${TESTSRC}/manifest.mf .
${JAVA} -Xbootclasspath/a:${TOOLS_JAR} -javaagent:${PWD}/testcase.jar Main
