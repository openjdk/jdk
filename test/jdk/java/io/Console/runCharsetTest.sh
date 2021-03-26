# Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#! /bin/sh
#

# Cygwin's `expect` command does not emulate console
OS=`uname -s`
case "$OS" in
  Windows* | CYGWIN* )
    echo "Cygwin's `expect` command does not emulate console. Test ignored."
    exit 0
esac

locale=$1
encoding=$2

if [ -f /usr/bin/expect ]
then
    EXPECT=/usr/bin/expect
else
    echo "expect command does not exist. test ignored"
    exit 0
fi

if $EXPECT -n $TESTSRC/script.exp $TESTJAVA/bin/java $locale $encoding $TESTCLASSES ${TESTVMOPTS} ; then
    echo "TEST PASSED!"
    exit 0
else
    echo "TEST FAILED!"
    exit 1
fi
