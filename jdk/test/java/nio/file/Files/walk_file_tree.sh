#
# Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.
#

# @test
# @bug 4313887
# @summary Unit test for walkFileTree method
# @build CreateFileTree PrintFileTree SkipSiblings TerminateWalk
# @run shell walk_file_tree.sh

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
        echo "This test does not run on Windows" 
        exit 0
        ;;
    * )
        CLASSPATH=${TESTCLASSES}:${TESTSRC}
        ;;
esac
export CLASSPATH

# create the file tree
ROOT=`$JAVA CreateFileTree`
if [ $? != 0 ]; then exit 1; fi

failures=0

# print the file tree and compare output with find(1)
$JAVA PrintFileTree "$ROOT" > out1
find "$ROOT" > out2
diff out1 out2
if [ $? != 0 ]; then failures=`expr $failures + 1`; fi

# repeat test following links (use -follow instead of -L
# to allow running on older systems)
$JAVA PrintFileTree -L "$ROOT" > out1
find "$ROOT" -follow > out2
diff out1 out2
if [ $? != 0 ]; then failures=`expr $failures + 1`; fi

# test SKIP_SIBLINGS
$JAVA SkipSiblings "$ROOT"
if [ $? != 0 ]; then failures=`expr $failures + 1`; fi

# test TERMINATE
$JAVA TerminateWalk "$ROOT"
if [ $? != 0 ]; then failures=`expr $failures + 1`; fi

# clean-up
rm -r "$ROOT"

echo ''
if [ $failures -gt 0 ];
  then echo "$failures test(s) failed";
  else echo "Test passed"; fi
exit $failures
