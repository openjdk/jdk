#
# Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4640520 6354623 7198496
# @summary Unit test for java.util.ServiceLoader
#
# @build Basic Load FooService FooProvider1 FooProvider2 FooProvider3
# @run shell basic.sh

# Command-line usage: sh basic.sh /path/to/build

if [ -z "$TESTJAVA" ]; then
  if [ $# -lt 1 ]; then exit 1; fi
  TESTJAVA="$1"; shift
  COMPILEJAVA="${TESTJAVA}"
  TESTSRC="`pwd`"
  TESTCLASSES="`pwd`"
fi

JAVA="$TESTJAVA/bin/java"
JAR="$COMPILEJAVA/bin/jar"

OS=`uname -s`
case "$OS" in
    SunOS | Darwin | AIX )
      SEP=':' ;;
    Linux )
      SEP=':' ;;
    * )
      SEP='\;' ;;
esac

JARD=x.jar
EXTD=x.ext
TESTD=x.test

if [ \! -d $EXTD ]; then
    # Initialize
    echo Initializing...
    rm -rf $JARD $EXTD $TESTD
    mkdir -p $JARD $EXTD $TESTD

    for n in 2 3; do
      rm -rf $JARD/*; mkdir -p $JARD/META-INF/services
      echo FooProvider$n \
        >$JARD/META-INF/services/FooService
      cp $TESTCLASSES/FooProvider$n.class $JARD
      if [ $n = 3 ]; then
        cp $TESTCLASSES/FooService.class $JARD
      fi
      (cd $JARD; "$JAR" ${TESTTOOLVMOPTS} -cf ../p$n.jar *)
    done

    mv p3.jar $EXTD

    cp $TESTCLASSES/Load.class $TESTD
    cp $TESTCLASSES/FooService.class $TESTD
    cp $TESTCLASSES/FooProvider1.class $TESTD
    mkdir -p $TESTD/META-INF/services
    echo FooProvider1 \
      >$TESTD/META-INF/services/FooService

    # This gives us:
    #   $TESTD: FooProvider1
    #   .     : FooProvider2, in p2.jar
    #   $EXTD:  FooProvider3, in p3.jar

fi

failures=0

go() {
  echo ''
  cp="$1"; shift
  if [ -z "$cp" ]; then cp="$TESTCLASSES"; else cp="$TESTCLASSES$SEP$cp"; fi
  vmargs="$1"; shift
  sh -xc "'$JAVA' ${TESTVMOPTS} -cp $cp $vmargs $T $*" 2>&1
  if [ $? != 0 ]; then failures=`expr $failures + 1`; fi
}


# Java-level tests

T=Basic
go ".${SEP}$TESTD${SEP}p2.jar" "-Djava.ext.dirs=$EXTD"


# Success cases

T=Load

go "$TESTD" "" FooProvider1

go ".${SEP}p2.jar" "" FooProvider2

go "" "-Djava.ext.dirs=$EXTD" FooProvider3

go "$TESTD${SEP}p2.jar" "" FooProvider1 FooProvider2

go "$TESTD" "-Djava.ext.dirs=$EXTD" FooProvider3 FooProvider1

go "$TESTD${SEP}p2.jar" "-Djava.ext.dirs=$EXTD" \
  FooProvider3 FooProvider1 FooProvider2

# Should only find the installed provider
go "$TESTD${SEP}p2.jar" "-Djava.ext.dirs=$EXTD" -i FooProvider3


# Failure cases

mkdir -p x.meta/META-INF/services

# Simple failures
for p in FooProvider42 'blah blah' 9234 'X!' java.lang.Object; do
  echo $p >x.meta/META-INF/services/FooService
  go ".${SEP}x.meta" "" fail
done

# Failures followed by successes
echo FooProvider42 >x.meta/META-INF/services/FooService
go "$TESTD${SEP}x.meta" "" FooProvider1 fail
go "x.meta${SEP}$TESTD" "" fail FooProvider1
go "$TESTD${SEP}x.meta${SEP}${SEP}p2.jar" "-Djava.ext.dirs=$EXTD" \
  FooProvider3 FooProvider1 fail FooProvider2


# Summary

echo ''
if [ $failures -gt 0 ];
  then echo "$failures case(s) failed";
  else echo "All cases passed"; fi
exit $failures
