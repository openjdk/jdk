#!/bin/sh

#
# Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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
# @bug 6746458
# @summary Verify correct rejection of illegal quoted identifiers.
# @run shell MakeNegTests.sh

default_template=QuotedIdent.java
# the rest of this file is a generic "//BAD"-line tester

: ${TESTSRC=.} ${TESTCLASSES=.}
javac="${TESTJAVA+${TESTJAVA}/bin/}javac"

verbose=false quiet=false

main() {
  case "${@-}" in
  *.java*)
    for template in "$@"; do
      expand_and_test "$template"
    done;;
  *) expand_and_test "${TESTSRC}/$default_template";;
  esac
}

expand_and_test() {
  template=$1
  expand "$@"
  testneg "$@"
}

expand() {
  template=$1
  badlines=` grep -n < "$template" '//BAD' `
  badcount=` echo "$badlines" | wc -l `
  [ $badcount -gt 0 ] || { echo "No negative test cases in $template"; exit 1; }
  $quiet || echo "Expanding $badcount negative test cases from $template:"
  $quiet || echo "$badlines"
  badnums=` echo "$badlines" | sed 's/:.*//' `
  casestem=` getcasestem "$template" `
  tclassname=` basename "$template" .java `
  rm "$casestem"*.java
  for badnum in $badnums; do
    casefile="$casestem"${badnum}.java
    cclassname=` basename "$casefile" .java `
    sed < "$template" > "$casefile" "
      s|@compile|@compile/fail|
      / @[a-z]/s|@|##|
      ${badnum}s:^ *[/*]*:    :
      s/${tclassname}/${cclassname}/g
    "
    $verbose && diff -u "$template" "$casefile"
  done
}

getcasestem() {
  echo `basename $1` | sed 's/.*\///;s/\.java$//;s/_BAD[0-9]*$//;s/$/_BAD/'
}

testneg() {
  template=$1
  for casefile in ` getcasestem "$template" `*.java; do
    $quiet || echo -------- $javac "$casefile"
    $javac "$casefile" > "$casefile".errlog 2>&1 && {
      echo "*** Compilation unexpectedly succeeded:  $casefile"
      exit 1
    }
    $quiet || echo "Compilation failed as expected"
    $quiet || head ` $verbose || echo -3 ` < "$casefile".errlog
    rm "$casefile".errlog
  done
}

main "$@"
