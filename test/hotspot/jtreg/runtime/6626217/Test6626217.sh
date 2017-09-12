# 
#  Copyright (c) 1998, 2014, Oracle and/or its affiliates. All rights reserved.
#  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
# 
#  This code is free software; you can redistribute it and/or modify it
#  under the terms of the GNU General Public License version 2 only, as
#  published by the Free Software Foundation.
# 
#  This code is distributed in the hope that it will be useful, but WITHOUT
#  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
#  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
#  version 2 for more details (a copy is included in the LICENSE file that
#  accompanied this code).
# 
#  You should have received a copy of the GNU General Public License version
#  2 along with this work; if not, write to the Free Software Foundation,
#  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
# 
#  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
#  or visit www.oracle.com if you need additional information or have any
#  questions.
# 


# @test @(#)Test6626217.sh
# @bug 6626217
# @summary Loader-constraint table allows arrays instead of only the base-classes
# @run shell Test6626217.sh
#
## some tests require path to find test source dir
if [ "${TESTSRC}" = "" ]
then
  TESTSRC=${PWD}
  echo "TESTSRC not set.  Using "${TESTSRC}" as default"
fi
echo "TESTSRC=${TESTSRC}"
## Adding common setup Variables for running shell tests.
. ${TESTSRC}/../../test_env.sh

JAVA=${TESTJAVA}${FS}bin${FS}java
JAVAC=${COMPILEJAVA}${FS}bin${FS}javac

# Current directory is scratch directory, copy all the test source there
# (for the subsequent moves to work).
${CP} ${TESTSRC}${FS}* ${THIS_DIR}

# A Clean Compile: this line will probably fail within jtreg as have a clean dir:
${RM} -f *.class *.impl many_loader.java

# Make sure that the compilation steps occurs in the future as not to allow fast systems
# to copy and compile bug_21227.java so fast as to make the class and java have the same
# time stamp, which later on would make the compilation step of many_loader.java fail
sleep 2

# Compile all the usual suspects, including the default 'many_loader'
${CP} many_loader1.java.foo many_loader.java
${JAVAC} ${TESTJAVACOPTS} -Xlint *.java

# Rename the class files, so the custom loader (and not the system loader) will find it
${MV} from_loader2.class from_loader2.impl2

# Compile the next version of 'many_loader'
${MV} many_loader.class many_loader.impl1
${CP} many_loader2.java.foo many_loader.java
${JAVAC} ${TESTJAVACOPTS} -Xlint many_loader.java

# Rename the class file, so the custom loader (and not the system loader) will find it
${MV} many_loader.class many_loader.impl2
${MV} many_loader.impl1 many_loader.class
${RM} many_loader.java

${JAVA} ${TESTOPTS} -Xverify -Xint -cp . bug_21227 >test.out 2>&1
grep "loader constraint" test.out
exit $?

