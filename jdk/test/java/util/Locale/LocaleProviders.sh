#
# Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
#!/bin/sh
#
# @test
# @bug 6336885
# @summary tests for "java.locale.providers" system property
# @compile -XDignore.symbol.file LocaleProviders.java
# @run shell/timeout=600 LocaleProviders.sh

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
  SunOS | Linux | *BSD | Darwin )
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

runTest()
{
    RUNCMD="${TESTJAVA}${FS}bin${FS}java -classpath ${TESTCLASSES} -Duser.language=$DEFLANG -Duser.country=$DEFCTRY -Djava.locale.providers=$PREFLIST LocaleProviders $EXPECTED $TESTLANG $TESTCTRY"
    echo ${RUNCMD}
    ${RUNCMD}
    result=$?
    if [ $result -eq 0 ]
    then
      echo "Execution successful"
    else
      echo "Execution of the test case failed."
      exit $result
    fi
}

# testing HOST is selected for the default locale, if specified on Windows or MacOSX
DEFLANG=en
DEFCTRY=US
PREFLIST=HOST
case "$OS" in
  Windows_NT* )
    WINVER=`uname -r`
    if [ "${WINVER}" = "5" ]
    then
      EXPECTED=JRE
    else
      EXPECTED=HOST
    fi
    ;;
  CYGWIN_NT-6* | Darwin )
    EXPECTED=HOST
    ;;
  * )
    EXPECTED=JRE
    ;;
esac
TESTLANG=en
TESTCTRY=US
runTest

# testing HOST is NOT selected for the non-default locale, if specified
DEFLANG=en
DEFCTRY=US
PREFLIST=HOST
EXPECTED=JRE
TESTLANG=en
TESTCTRY=GB
runTest

# testing SPI is NOT selected, as there is none.
PREFLIST=SPI
EXPECTED=JRE
TESTLANG=en
TESTCTRY=US
runTest

# testing the order, variaton #1. This assumes en_GB DateFormat data are available both in JRE & CLDR
PREFLIST=CLDR
EXPECTED=CLDR
TESTLANG=en
TESTCTRY=GB
runTest

# testing the order, variaton #2. This assumes en_GB DateFormat data are available both in JRE & CLDR
PREFLIST=JRE,CLDR
EXPECTED=JRE
TESTLANG=en
TESTCTRY=GB
runTest

# testing the order, variaton #3 for non-existent locale in JRE assuming "haw" is not in JRE.
PREFLIST=JRE,CLDR
EXPECTED=CLDR
TESTLANG=haw
TESTCTRY=GB
runTest

exit $result
