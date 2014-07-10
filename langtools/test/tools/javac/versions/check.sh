#
# Copyright (c) 2004, 2014, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4981566 5028634 5094412 6304984 7025786 7025789 8001112 8028545 8000961
# @summary Check interpretation of -target and -source options
# @build CheckClassFileVersion
# @run shell check.sh

TESTJAVA=${TESTJAVA:?}
TC=${TESTCLASSES-.}

J="$TESTJAVA/bin/java"
JC="$TESTJAVA/bin/javac"
CFV="${TESTVMOPTS} -cp $TC CheckClassFileVersion"

rm -f $TC/X.java $TC/X.java
echo 'public class X { }' > $TC/X.java
echo 'public enum Y { }' > $TC/Y.java


# Check class-file versions

check() {
  V=$1; shift
  echo "+ javac $* [$V]"
  "$JC" ${TESTTOOLVMOPTS} -Xlint:-options -d $TC $* $TC/X.java && "$J" $CFV $TC/X.class $V || exit 2
}

# check for all combinations of target values
check_target() {
  check $1 -source $2 -target $3
  check $1 -source $2 -target 1.${3}
}
# check for all combinations of source and target values
check_source_target() {
  check_target $1 $2     $3
  check_target $1 1.${2} $3
}


check_source_target 50.0 6   6

check_source_target 51.0 6   7
check_source_target 51.0 7   7

check_source_target 52.0 6   8
check_source_target 52.0 7   8
check_source_target 52.0 8   8

check_source_target 52.0 8   9
check_source_target 52.0 9   9

# and finally the default with no options
check 52.0

# Check source versions

fail() {
  echo "+ javac $*"
  if "$JC" ${TESTTOOLVMOPTS} -Xlint:-options -d $TC $*; then
    echo "-- did not fail as expected"
    exit 3
  else
    echo "-- failed as expected"
  fi
}

pass() {
  echo "+ javac $*"
  if "$JC" ${TESTTOOLVMOPTS} -Xlint:options -d $TC $*; then
    echo "-- passed"
  else
    echo "-- failed"
    exit 4
  fi
}

# the following need to be updated when -source 7 features are available
checksrc14() { pass $* $TC/X.java; fail $* $TC/Y.java; }
checksrc15() { pass $* $TC/X.java; pass $* $TC/Y.java; }
checksrc16() { checksrc15 $* ; }
checksrc17() { checksrc15 $* ; }
checksrc18() { checksrc15 $* ; }
checksrc19() { checksrc15 $* ; }



checksrc16 -source 1.6
checksrc16 -source 6
checksrc16 -source 1.6 -target 1.6
checksrc16 -source 6 -target 6

checksrc17 -source 1.7
checksrc17 -source 7
checksrc17 -source 1.7 -target 1.7
checksrc17 -source 7 -target 7

checksrc18 -source 1.8
checksrc18 -source 8
checksrc18 -source 1.8 -target 1.8
checksrc18 -source 8 -target 8

checksrc19
checksrc19 -source 1.9
checksrc19 -source 9
checksrc19 -source 1.9 -target 1.9
checksrc19 -source 9 -target 9
checksrc19 -target 1.9
checksrc19 -target 9

fail -source 1.6 -target 1.5 $TC/X.java
fail -source 6   -target 1.5 $TC/X.java
fail -source 7   -target 1.6 $TC/X.java
fail -source 8   -target 1.6 $TC/X.java
fail -source 8   -target 1.7 $TC/X.java
fail -source 9   -target 1.7 $TC/X.java
fail -source 9   -target 1.8 $TC/X.java
