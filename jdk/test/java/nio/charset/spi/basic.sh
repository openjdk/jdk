#!/bin/sh

#
# Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4429040 4591027 4814743
# @summary Unit test for charset providers
#
# @build Test FooCharset FooProvider
# @run shell basic.sh
# @run shell basic.sh ja_JP.eucJP
# @run shell basic.sh tr_TR
#

# Command-line usage: sh basic.sh /path/to/build [locale]

if [ -z "$TESTJAVA" ]; then
  if [ $# -lt 1 ]; then exit 1; fi
  TESTJAVA=$1; shift
  COMPILEJDK="${TESTJAVA}"
  TESTSRC=`pwd`
  TESTCLASSES=`pwd`
fi

JAVA=$TESTJAVA/bin/java
JAR=$COMPILEJAVA/bin/jar

DIR=`pwd`
case `uname` in
  SunOS | Linux | Darwin | AIX ) CPS=':' ;;
  Windows* )      CPS=';' ;;
  CYGWIN*  )
    DIR=`/usr/bin/cygpath -a -s -m $DIR`
    CPS=";";;
  *)              echo "Unknown platform: `uname`"; exit 1 ;;
esac

JARD=$DIR/x.jar
APPD=$DIR/x.ext
TESTD=$DIR/x.test

CSS='US-ASCII 8859_1 iso-ir-6 UTF-16 windows-1252 !BAR cp1252'


if [ \! -d $APPD ]; then
    # Initialize
    echo Initializing...
    rm -rf $JARD $APPD $TESTD
    mkdir -p $JARD/META-INF/services x.ext
    echo FooProvider \
      >$JARD/META-INF/services/java.nio.charset.spi.CharsetProvider
    cp $TESTCLASSES/FooProvider.class $TESTCLASSES/FooCharset.class $JARD
    mkdir $TESTD
    cp $TESTCLASSES/Test.class $TESTD
    (cd $JARD; $JAR ${TESTTOOLVMOPTS} -cf $APPD/test.jar *)
fi

if [ $# -gt 0 ]; then
    # Use locale specified on command line, if it's supported
    L="$1"
    shift
    s=`uname -s`
    if [ $s != Linux -a $s != SunOS -a $s != Darwin -a $s != AIX ]; then
      echo "$L: Locales not supported on this system, skipping..."
      exit 0
    fi
    if [ "x`locale -a | grep $L`" != "x$L" ]; then
      echo "$L: Locale not supported, skipping..."
      exit 0
    fi
    LC_ALL=$L; export LC_ALL
fi

TMP=${TMP:-$TEMP}; TMP=${TMP:-/tmp}
cd $TMP

failures=0
for where in app; do
  for security in none minimal-policy cp-policy; do
    echo '';
    echo "LC_ALL=$LC_ALL where=$where security=$security"
    av=''
    if [ $where = app ]; then
      av="$av -cp $TESTD$CPS$APPD/test.jar";
    fi
    case $security in
      none)          css="$CSS FOO";;
      # Minimal policy in this case is more or less carbon copy of jre default
      # security policy and doesn't give explicit runtime permission
      # for user provided runtime loadable charsets
      minimal-policy)  css="$CSS !FOO";
		     av="$av -Djava.security.manager -Djava.security.policy==$TESTSRC/default-pol";;
      cp-policy)     css="$CSS FOO";
		     av="$av -Djava.security.manager
		         -Djava.security.policy==$TESTSRC/charsetProvider.sp";;
    esac
    if (set -x; $JAVA ${TESTVMOPTS} $av Test $css) 2>&1; then
      continue;
    else
      failures=`expr $failures + 1`
    fi
  done
done

echo ''
if [ $failures -gt 0 ];
  then echo "$failures cases failed";
  else echo "All cases passed"; fi
exit $failures
