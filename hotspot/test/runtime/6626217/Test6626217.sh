# 
#  Copyright (c) 1998, 2012, Oracle and/or its affiliates. All rights reserved.
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

if [ "${TESTSRC}" = "" ]
  then TESTSRC=.
fi

if [ "${TESTJAVA}" = "" ]
then
  PARENT=`dirname \`which java\``
  TESTJAVA=`dirname ${PARENT}`
  echo "TESTJAVA not set, selecting " ${TESTJAVA}
  echo "If this is incorrect, try setting the variable manually."
fi

if [ "${TESTCLASSES}" = "" ]
then
  echo "TESTCLASSES not set.  Test cannot execute.  Failed."
  exit 1
fi

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  SunOS | Linux | Darwin )
    NULL=/dev/null
    PS=":"
    FS="/"
    RM=/bin/rm
    CP=/bin/cp
    MV=/bin/mv
    ;;
  Windows_* )
    NULL=NUL
    PS=";"
    FS="\\"
    RM=rm
    CP=cp
    MV=mv
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

JEMMYPATH=${CPAPPEND}
CLASSPATH=.${PS}${TESTCLASSES}${PS}${JEMMYPATH} ; export CLASSPATH

THIS_DIR=`pwd`

JAVA=${TESTJAVA}${FS}bin${FS}java
JAVAC=${TESTJAVA}${FS}bin${FS}javac

${JAVA} ${TESTVMOPTS} -version

# Current directory is scratch directory, copy all the test source there
# (for the subsequent moves to work).
${CP} ${TESTSRC}${FS}*  ${THIS_DIR}

# A Clean Compile: this line will probably fail within jtreg as have a clean dir:
${RM} -f *.class *.impl many_loader.java

# Compile all the usual suspects, including the default 'many_loader'
${CP} many_loader1.java.foo many_loader.java
${JAVAC} -source 1.4 -target 1.4 -Xlint *.java

# Rename the class files, so the custom loader (and not the system loader) will find it
${MV} from_loader2.class from_loader2.impl2

# Compile the next version of 'many_loader'
${MV} many_loader.class many_loader.impl1
${CP} many_loader2.java.foo many_loader.java
${JAVAC} -source 1.4 -target 1.4 -Xlint many_loader.java

# Rename the class file, so the custom loader (and not the system loader) will find it
${MV} many_loader.class many_loader.impl2
${MV} many_loader.impl1 many_loader.class
${RM} many_loader.java

${JAVA} ${TESTVMOPTS} -Xverify -Xint -cp . bug_21227 >test.out 2>&1
grep "loader constraint" test.out
exit $?

