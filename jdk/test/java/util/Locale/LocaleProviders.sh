#!/bin/sh
#
# Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
#
# @test
# @bug 6336885 7196799 7197573 7198834 8000245 8000615 8001440 8008577
#      8010666 8013086 8013233 8013903 8015960 8028771 8054482 8062006
#      8150432
# @summary tests for "java.locale.providers" system property
# @modules java.base/sun.util.locale
#          java.base/sun.util.locale.provider
# @compile LocaleProviders.java
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
if [ "${COMPILEJAVA}" = "" ]
then
  COMPILEJAVA="${TESTJAVA}"
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
  SunOS | Linux | *BSD | Darwin | AIX )
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

# create SPI implementations
mk() {
  d=`dirname $1`
  if [ ! -d $d ]; then mkdir -p $d; fi
  cat - >$1
}

SPIDIR=${TESTCLASSES}${FS}spi
rm -rf ${SPIDIR}
mk ${SPIDIR}${FS}src${FS}tznp.java << EOF
import java.util.spi.TimeZoneNameProvider;
import java.util.Locale;

public class tznp extends TimeZoneNameProvider {
    public String getDisplayName(String ID, boolean daylight, int style, Locale locale) {
        return "tznp";
    }

    public Locale[] getAvailableLocales() {
        Locale[] locales = {Locale.US};
        return locales;
    }
}
EOF
mk ${SPIDIR}${FS}src${FS}tznp8013086.java << EOF
import java.util.spi.TimeZoneNameProvider;
import java.util.Locale;
import java.util.TimeZone;

public class tznp8013086 extends TimeZoneNameProvider {
    public String getDisplayName(String ID, boolean daylight, int style, Locale locale) {
        if (!daylight && style==TimeZone.LONG) {
            return "tznp8013086";
        } else {
            return null;
        }
    }

    public Locale[] getAvailableLocales() {
        Locale[] locales = {Locale.JAPAN};
        return locales;
    }
}
EOF
mk ${SPIDIR}${FS}dest${FS}META-INF${FS}services${FS}java.util.spi.TimeZoneNameProvider << EOF
tznp
tznp8013086
EOF

EXTRAOPTS="-XaddExports:java.base/sun.util.locale=ALL-UNNAMED,java.base/sun.util.locale.provider=ALL-UNNAMED"

${COMPILEJAVA}${FS}bin${FS}javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} -d ${SPIDIR}${FS}dest \
    ${SPIDIR}${FS}src${FS}tznp.java \
    ${SPIDIR}${FS}src${FS}tznp8013086.java
${COMPILEJAVA}${FS}bin${FS}jar ${TESTTOOLVMOPTS} cvf ${SPIDIR}${FS}tznp.jar -C ${SPIDIR}${FS}dest .

# get the platform default locales
PLATDEF=`${TESTJAVA}${FS}bin${FS}java ${TESTVMOPTS} ${EXTRAOPTS} -classpath ${TESTCLASSES} LocaleProviders getPlatformLocale display`
DEFLANG=`echo ${PLATDEF} | sed -e "s/,.*//"`
DEFCTRY=`echo ${PLATDEF} | sed -e "s/.*,//"`
echo "DEFLANG=${DEFLANG}"
echo "DEFCTRY=${DEFCTRY}"
PLATDEF=`${TESTJAVA}${FS}bin${FS}java ${TESTVMOPTS} ${EXTRAOPTS} -classpath ${TESTCLASSES} LocaleProviders getPlatformLocale format`
DEFFMTLANG=`echo ${PLATDEF} | sed -e "s/,.*//"`
DEFFMTCTRY=`echo ${PLATDEF} | sed -e "s/.*,//"`
echo "DEFFMTLANG=${DEFFMTLANG}"
echo "DEFFMTCTRY=${DEFFMTCTRY}"

runTest()
{
    RUNCMD="${TESTJAVA}${FS}bin${FS}java ${TESTVMOPTS} ${EXTRAOPTS} -classpath ${TESTCLASSES}${PS}${SPICLASSES} -Djava.locale.providers=$PREFLIST LocaleProviders $METHODNAME $PARAM1 $PARAM2 $PARAM3"
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
METHODNAME=adapterTest
PREFLIST=HOST,JRE
case "$OS" in
  Windows_NT* )
    WINVER=`uname -r`
    if [ "${WINVER}" = "5" ]
    then
      PARAM1=JRE
    else
      PARAM1=HOST
    fi
    ;;
  CYGWIN_NT-6* | CYGWIN_NT-10* | Darwin )
    PARAM1=HOST
    ;;
  * )
    PARAM1=JRE
    ;;
esac
PARAM2=${DEFLANG}
PARAM3=${DEFCTRY}
runTest

# testing HOST is NOT selected for the non-default locale, if specified
METHODNAME=adapterTest
PREFLIST=HOST,JRE
PARAM1=JRE
# Try to find the locale JRE supports which is not the platform default (HOST supports that one)
if [ "${DEFLANG}" != "en" ] && [ "${DEFFMTLANG}" != "en" ]; then
  PARAM2=en
  PARAM3=US
elif [ "${DEFLANG}" != "ja" ] && [ "${DEFFMTLANG}" != "ja" ]; then
  PARAM2=ja
  PARAM3=JP
else
  PARAM2=zh
  PARAM3=CN
fi
SPICLASSES=
runTest

# testing SPI is NOT selected, as there is none.
METHODNAME=adapterTest
PREFLIST=SPI,JRE
PARAM1=JRE
PARAM2=en
PARAM3=US
SPICLASSES=
runTest
PREFLIST=SPI,COMPAT
runTest

# testing the order, variaton #1. This assumes en_GB DateFormat data are available both in JRE & CLDR
METHODNAME=adapterTest
PREFLIST=CLDR,JRE
PARAM1=CLDR
PARAM2=en
PARAM3=GB
SPICLASSES=
runTest
PREFLIST=CLDR,COMPAT
runTest

# testing the order, variaton #2. This assumes en_GB DateFormat data are available both in JRE & CLDR
METHODNAME=adapterTest
PREFLIST=JRE,CLDR
PARAM1=JRE
PARAM2=en
PARAM3=GB
SPICLASSES=
runTest
PREFLIST=COMPAT,CLDR
runTest

# testing the order, variaton #3 for non-existent locale in JRE assuming "haw" is not in JRE.
METHODNAME=adapterTest
PREFLIST=JRE,CLDR
PARAM1=CLDR
PARAM2=haw
PARAM3=
SPICLASSES=
runTest
PREFLIST=COMPAT,CLDR
runTest

# testing the order, variaton #4 for the bug 7196799. CLDR's "zh" data should be used in "zh_CN"
METHODNAME=adapterTest
PREFLIST=CLDR
PARAM1=CLDR
PARAM2=zh
PARAM3=CN
SPICLASSES=
runTest

# testing FALLBACK provider. SPI and invalid one cases.
METHODNAME=adapterTest
PREFLIST=SPI
PARAM1=FALLBACK
PARAM2=en
PARAM3=US
SPICLASSES=
runTest
PREFLIST=FOO
PARAM1=CLDR
PARAM2=en
PARAM3=US
SPICLASSES=
runTest
PREFLIST=BAR,SPI
PARAM1=FALLBACK
PARAM2=en
PARAM3=US
SPICLASSES=
runTest

# testing 7198834 fix. Only works on Windows Vista or upper.
METHODNAME=bug7198834Test
PREFLIST=HOST
PARAM1=
PARAM2=
PARAM3=
SPICLASSES=
runTest

# testing 8000245 fix.
METHODNAME=tzNameTest
PREFLIST=JRE
PARAM1=Europe/Moscow
PARAM2=
PARAM3=
SPICLASSES=${SPIDIR}
runTest
PREFLIST=COMPAT
runTest

# testing 8000615 fix.
METHODNAME=tzNameTest
PREFLIST=JRE
PARAM1=America/Los_Angeles
PARAM2=
PARAM3=
SPICLASSES=${SPIDIR}
runTest
PREFLIST=COMPAT
runTest

# testing 8001440 fix.
METHODNAME=bug8001440Test
PREFLIST=CLDR
PARAM1=
PARAM2=
PARAM3=
SPICLASSES=
runTest

# testing 8010666 fix.
if [ "${DEFLANG}" = "en" ]
then
  METHODNAME=bug8010666Test
  PREFLIST=HOST
  PARAM1=
  PARAM2=
  PARAM3=
  SPICLASSES=
  runTest
fi

# testing 8013086 fix.
METHODNAME=bug8013086Test
PREFLIST=JRE,SPI
PARAM1=ja
PARAM2=JP
PARAM3=
SPICLASSES=${SPIDIR}
runTest
PREFLIST=COMPAT,SPI
runTest

# testing 8013903 fix. (Windows only)
METHODNAME=bug8013903Test
PREFLIST=HOST,JRE
PARAM1=
PARAM2=
PARAM3=
SPICLASSES=
runTest
PREFLIST=HOST
runTest
PREFLIST=HOST,COMPAT
runTest

# testing 8027289 fix, if the platform format default is zh_CN
# this assumes Windows' currency symbol for zh_CN is \u00A5, the yen
# (yuan) sign.
if [ "${DEFFMTLANG}" = "zh" ] && [ "${DEFFMTCTRY}" = "CN" ]; then
  METHODNAME=bug8027289Test
  PREFLIST=JRE,HOST
  PARAM1=FFE5
  PARAM2=
  PARAM3=
  SPICLASSES=
  runTest
  PREFLIST=COMPAT,HOST
  runTest
  PREFLIST=HOST
  PARAM1=00A5
  runTest
fi

exit $result
