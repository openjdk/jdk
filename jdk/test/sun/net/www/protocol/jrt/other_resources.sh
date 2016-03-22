#
# Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
# @build OtherResources
# @run shell other_resources.sh
# @summary Access a jrt:/ resource in an observable module that is not in
#  the boot layer and hence not known to the built-in class loaders. This
#  test is a shell test because the run tag doesn't support -limitmods.

set -e

if [ -z "$TESTJAVA" ]; then
  if [ $# -lt 1 ]; then exit 1; fi
  TESTJAVA="$1"; shift
  COMPILEJAVA="${TESTJAVA}"
  TESTSRC="`pwd`"
  TESTCLASSES="`pwd`"
fi

JAVA="$TESTJAVA/bin/java ${TESTVMOPTS}"
$JAVA -limitmods java.base -cp $TESTCLASSES OtherResources

exit 0

