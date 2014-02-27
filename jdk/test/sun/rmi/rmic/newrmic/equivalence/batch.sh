#!/bin/sh
#
# Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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
# Usage: batch.sh classpath classes...
#

if [ $# -lt 2 ]
then
    echo "Usage: `basename $0` classpath classes..."
    exit 1
fi

if [ "${TESTJAVA}" = "" ]
then
    echo "TESTJAVA not set.  Test cannot execute.  Failed."
    exit 1
fi

refv11dir=./ref-v1.1-output
refvcompatdir=./ref-vcompat-output
refv12dir=./ref-v1.2-output

newv11dir=./new-v1.1-output
newvcompatdir=./new-vcompat-output
newv12dir=./new-v1.2-output

v11diffs=./diffs-v1.1
vcompatdiffs=./diffs-vcompat
v12diffs=./diffs-v1.2

difflines=./diff-lines

rm -rf $refv11dir $refvcompatdir $refv12dir
rm -rf $newv11dir $newvcompatdir $newv12dir
rm -f $v11diffs $vcompatdiffs $v12diffs $difflines

mkdir $refv11dir $refvcompatdir $refv12dir
mkdir $newv11dir $newvcompatdir $newv12dir

set -ex

${TESTJAVA}/bin/rmic       -keep -nowrite -v1.1 -d $refv11dir -classpath "$@"
${TESTJAVA}/bin/rmic       -keep -nowrite -vcompat -d $refvcompatdir -classpath "$@"
${TESTJAVA}/bin/rmic       -keep -nowrite -v1.2 -d $refv12dir -classpath "$@"

${TESTJAVA}/bin/rmic -Xnew -keep -nowrite -v1.1 -d $newv11dir -classpath "$@"
${TESTJAVA}/bin/rmic -Xnew -keep -nowrite -vcompat -d $newvcompatdir -classpath "$@"
${TESTJAVA}/bin/rmic -Xnew -keep -nowrite -v1.2 -d $newv12dir -classpath "$@"

set +ex

diff -r $refv11dir $newv11dir > $v11diffs
diff -r $refvcompatdir $newvcompatdir > $vcompatdiffs
diff -r $refv12dir $newv12dir > $v12diffs

cat $v11diffs $vcompatdiffs $v12diffs | grep '^[<>O]' | fgrep -v ' server = (' > $difflines

if [ `cat $difflines | wc -l` -gt 0 ]
then
    cat $v11diffs $vcompatdiffs $v12diffs
    echo "TEST FAILED: unexpected diffs"
    exit 1
fi

echo "TEST PASSED: new rmic output identical to reference rmic output"

rm -rf $refv11dir $refvcompatdir $refv12dir
rm -rf $newv11dir $newvcompatdir $newv12dir
rm -f $v11diffs $vcompatdiffs $v12diffs $difflines
