#
# Copyright (c) 2008, 2012, Oracle and/or its affiliates. All rights reserved.
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
# @summary Unit test for java.nio.channels.spi.AsynchronousChannelProvider
# @build Provider1 Provider2 CheckProvider
# @run shell custom_provider.sh

# if TESTJAVA isn't set then we assume an interactive run.

if [ -z "$TESTJAVA" ]; then
    TESTSRC=.
    TESTCLASSES=.
    JAVA=java
else
    JAVA="${TESTJAVA}/bin/java"
fi

OS=`uname -s`
case "$OS" in
    Windows_* | CYGWIN* )
        CLASSPATH="${TESTCLASSES};${TESTSRC}"
        ;;
    * )
        CLASSPATH=${TESTCLASSES}:${TESTSRC}
        ;;
esac
export CLASSPATH

failures=0

go() {
    echo ''
    $JAVA ${TESTVMOPTS} $1 $2 $3 2>&1
    if [ $? != 0 ]; then failures=`expr $failures + 1`; fi
}

# Run the tests

go CheckProvider Provider1
go -Djava.nio.channels.spi.AsynchronousChannelProvider=Provider2 CheckProvider \
  Provider2

#
# Results
#
echo ''
if [ $failures -gt 0 ];
  then echo "$failures test(s) failed";
  else echo "All test(s) passed"; fi
exit $failures
