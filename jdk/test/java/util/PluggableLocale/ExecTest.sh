# 
# Copyright (c) 2007 Sun Microsystems, Inc.  All Rights Reserved.
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
#!/bin/sh
#
#
#
# This script is the actual launcher of each locale service provider test.
# fooprovider.jar contains localized object providers and barprovider.jar
# contains localized name providers.  This way, we can test providers that
# can relate to each other (such as, DateFormatSymbolsProvider and 
# TimeZoneNameProvider) separately.
# 
# Parameters:
#    providersToTest: [foo|bar|foobar]
#    java class name: <class name>
#    providersInExtDir: [true|false]

if [ "${TESTSRC}" = "" ]
then
  echo "TESTSRC not set.  Test cannot execute.  Failed."
  exit 1
fi
echo "TESTSRC=${TESTSRC}"
if [ "${TESTJAVA}" = "" ]
then
  echo "TESTJAVA not set.  Test cannot execute.  Failed."
  exit 1
fi
echo "TESTJAVA=${TESTJAVA}"
if [ "${TESTCLASSES}" = "" ]
then
  echo "TESTCLASSES not set.  Test cannot execute.  Failed."
  exit 1
fi
echo "TESTCLASSES=${TESTCLASSES}"
echo "CLASSPATH=${CLASSPATH}"

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  SunOS | Linux )
    PS=":"
    FS="/"
    ;;
  Windows* | CYGWIN* )
    PS=";"
    FS="\\"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

# set classpath and extension directory variables
if [ -d ${TESTJAVA}${FS}lib${FS}ext ]
then
    EXTDIRS="${TESTJAVA}${FS}lib${FS}ext${PS}${TESTCLASSES}"
else
    EXTDIRS="${TESTJAVA}${FS}jre${FS}lib${FS}ext${PS}${TESTCLASSES}"
fi

case "$1" in 
  "foo" )
    cp ${TESTSRC}${FS}fooprovider.jar ${TESTCLASSES}
    CLASSPATHARG=".${PS}${TESTSRC}${PS}${TESTSRC}${FS}fooprovider.jar"
    ;;
  "bar" )
    cp ${TESTSRC}${FS}barprovider.jar ${TESTCLASSES}
    CLASSPATHARG=".${PS}${TESTSRC}${PS}${TESTSRC}${FS}barprovider.jar"
    ;;
  "foobar" )
    cp ${TESTSRC}${FS}fooprovider.jar ${TESTCLASSES}
    cp ${TESTSRC}${FS}barprovider.jar ${TESTCLASSES}
    CLASSPATHARG=".${PS}${TESTSRC}${PS}${TESTSRC}${FS}fooprovider.jar${PS}${TESTSRC}${PS}${TESTSRC}${FS}barprovider.jar"
    ;;
esac
    
# compile
cp ${TESTSRC}${FS}ProviderTest.java .
cp ${TESTSRC}${FS}$2.java .
COMPILE="${TESTJAVA}${FS}bin${FS}javac -XDignore.symbol.file -d . -classpath ${CLASSPATHARG} $2.java"
echo ${COMPILE}
${COMPILE}
result=$?

if [ $result -eq 0 ]
then
  echo "Compilation of the test case was successful."
else 
  echo "Compilation of the test case failed."
  # Cleanup
  rm -f ${TESTCLASSES}${FS}$2*.class
  rm -f ${TESTCLASSES}${FS}fooprovider.jar
  rm -f ${TESTCLASSES}${FS}barprovider.jar
  exit $result
fi

# run
if [ "$3" = "true" ]
then
  RUNCMD="${TESTJAVA}${FS}bin${FS}java -Djava.ext.dirs=${EXTDIRS} $2 "
else
  RUNCMD="${TESTJAVA}${FS}bin${FS}java -classpath ${CLASSPATHARG} $2 "
fi

echo ${RUNCMD}
${RUNCMD}
result=$?

if [ $result -eq 0 ]
then
  echo "Execution successful"
else
  echo "Execution of the test case failed."
fi

# Cleanup
rm -f ${TESTCLASSES}${FS}$2*.class
rm -f ${TESTCLASSES}${FS}fooprovider.jar
rm -f ${TESTCLASSES}${FS}barprovider.jar

exit $result
