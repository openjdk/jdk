# 
#  Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

 
# @test Test6294277.sh
# @bug 6294277
# @summary java -Xdebug crashes on SourceDebugExtension attribute larger than 64K
# @run shell Test6294277.sh
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

BIT_FLAG=""

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  SunOS | Linux )
    NULL=/dev/null
    PS=":"
    FS="/"
    ## for solaris, linux it's HOME
    FILE_LOCATION=$HOME
    if [ -f ${FILE_LOCATION}${FS}JDK64BIT -a ${OS} = "SunOS" -a `uname -p`='sparc' ]
    then
        BIT_FLAG="-d64"
    fi
    ;;
  Windows_* | Darwin )
    NULL=NUL
    PS=";"
    FS="\\"
    echo "Test skipped"
    exit 0
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

cp ${TESTSRC}${FS}*.java .

${TESTJAVA}${FS}bin${FS}java ${BIT_FLAG} -fullversion

${TESTJAVA}${FS}bin${FS}javac *.java

${TESTJAVA}${FS}bin${FS}java ${BIT_FLAG} -classpath . -Xdebug -Xrunjdwp:transport=dt_socket,address=8888,server=y,suspend=n SourceDebugExtension > test.out 2>&1 &

P_PID=$!

sleep 60
STATUS=1

grep "Test PASSES" test.out > ${NULL}
if [ $? = 0 ]; then
    cat test.out
    STATUS=0
fi

exit $STATUS
