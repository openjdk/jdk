#! /bin/sh

#
# Copyright (c) 1998, 2010, Oracle and/or its affiliates. All rights reserved.
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

if [ "x$TESTJAVA" = x ]; then
  TESTJAVA=$1; shift
  TESTCLASSES=.
fi

rm -rf x.Basic.*
rm -f x.Basic.non
printf "%s" "xyzzyN" > x.Basic.rw
touch x.Basic.ro; chmod ugo-w x.Basic.ro
mkdir x.Basic.dir
if $TESTJAVA/bin/java $* -classpath "$TESTCLASSES" Basic; then
  [ -f x.Basic.rw ] && (echo "x.Basic.rw not deleted"; exit 1)
  ([ -d x.Basic.dir ] || [ \! -d x.Basic.dir2 ]) \
    && (echo "x.Basic.dir not renamed"; exit 1)
  [ \! -d x.Basic.nonDir ] && (echo "x.Basic.nonDir not created"; exit 1)
  [ -f x.Basic.non ] && (echo "x.Basic.non not deleted"; exit 1)
  exit 0
else
  exit 1
fi
