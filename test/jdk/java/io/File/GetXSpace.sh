#
# Copyright (c) 2006, 2020, Oracle and/or its affiliates. All rights reserved.
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

# set platform-dependent variable
OS=`uname -s`
case "$OS" in
  Linux | Darwin ) TMP=/tmp ;;
  Windows_98 )    return    ;;
  Windows* )      SID=`sid`; TMP="c:/temp"  ;;
  * )
    echo "Unrecognized system!"
    exit 1
    ;;
esac

TMP1=${TMP}/tmp1_$$
FAIL=0;

deny() {
  case "$OS" in
  Windows* ) chacl -d ${SID}:f $* ;;
  * )        chmod 000 $*         ;;
  esac
}

allow() {
  case "$OS" in
  Windows* ) chacl -g ${SID}:f $* ;;
  * )        chmod 777 $*         ;;
  esac
}

runTest() {
  ${TESTJAVA}/bin/java ${TESTVMOPTS} -cp ${TESTCLASSES} GetXSpace $*
  if [ $? -eq 0 ]
  then echo "Passed"
  else
    echo "FAILED"
    FAIL=`expr ${FAIL} + 1`
  fi
}

# df output
runTest

# readable file in an unreadable directory
mkdir -p ${TMP1}
touch ${TMP1}/foo
deny ${TMP1}
runTest ${TMP1}/foo
allow ${TMP1}
rm -rf ${TMP1}

if [ ${FAIL} -ne 0 ]
then
  echo ""
  echo "${FAIL} test(s) failed"
  exit 1
fi
