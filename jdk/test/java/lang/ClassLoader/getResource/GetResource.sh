#
# Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
# @bug 6760902
# @summary Empty path on bootclasspath is not default to current working
#          directory for both class lookup and resource lookup whereas
#          empty path on classpath is default to current working directory.
#
# @run shell GetResource.sh

if [ -z "$TESTJAVA" ]; then
  if [ $# -lt 1 ]; then exit 1; fi
  TESTJAVA="$1"; shift
  COMPILEJAVA="${TESTJAVA}"
  TESTSRC="`pwd`"
  TESTCLASSES="`pwd`"
fi

# set platform-specific variables
OS=`uname -s`
case "$OS" in
  Windows*)
    PS=";"
    ;;
  CYGWIN* )
    PS=";"
    TESTCLASSES=`/usr/bin/cygpath -a -s -m ${TESTCLASSES}`
    ;;
  * )
    PS=":"
    ;;
esac

echo TESTSRC=${TESTSRC}
echo TESTCLASSES=${TESTCLASSES}
echo TESTJAVA=${TESTJAVA}
echo ""

${COMPILEJAVA}/bin/javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} \
        -d ${TESTCLASSES} \
        ${TESTSRC}/GetResource.java  || exit 10

setup() {
    dest=${TESTCLASSES}/$1
    rm -rf $dest
    mkdir $dest
    cp ${TESTSRC}/test.properties $dest
    cp ${TESTCLASSES}/GetResource.class $dest
}


count=0
runTest() {
    expected=$1;
    vmoption=$2; shift; shift
    count=`expr $count+1`
    echo "Test $count : $vmoption $@"
    ${TESTJAVA}/bin/java ${TESTVMOPTS} "$vmoption" $@ \
        GetResource $expected     || exit $count
}

# run test
setup "a"
setup "b"

cd ${TESTCLASSES}
DIR=`pwd`

#    Expected    -classpath
runTest "a"      -cp a
runTest "a"      -cp "a${PS}b"
runTest "b"      -cp b
runTest "b"      -cp "b${PS}a"

cd ${DIR}/a

# no -classpath
runTest "a"      -cp "${PS}"                            
runTest "b"      -cp "../b"                   

# Test empty path in classpath default to current working directory
runTest "a"      -cp "${PS}../b"

