#   
# Copyright 2009 Sun Microsystems, Inc. All rights reserved.
# SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
#

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

BIT_FLAG=""

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  SunOS | Linux )
    NULL=/dev/null
    PS=":"
    FS="/"
    RM=/bin/rm
    CP=/bin/cp
    MV=/bin/mv
    ## for solaris, linux it's HOME
    FILE_LOCATION=$HOME
    if [ -f ${FILE_LOCATION}${FS}JDK64BIT -a ${OS} = "SunOS" ]
    then
        BIT_FLAG=`cat ${FILE_LOCATION}${FS}JDK64BIT`
    fi
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

${JAVA} ${BIT_FLAG} -version

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

${JAVA} ${BIT_FLAG} -Xverify -Xint -cp . bug_21227 >test.out 2>&1
grep "violates loader constraints" test.out
exit $?

