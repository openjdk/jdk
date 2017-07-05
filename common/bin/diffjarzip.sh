#!/bin/bash
#
# Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

# Simple tool to diff two jar or zip files. It unpacks the jar/zip files and
# reports if files differs and if files are new or missing.
# Assumes gnu diff.

# There are a few source files that have DOS line endings in the
# jaxp/jaxws source drops, when the sources were added to the repository
# the source files were converted to UNIX line endings.
# For now we ignore these differences.
DIFF_FLAGS="--strip-trailing-cr"
#set -x

if [ $# -lt 2 ] 
then
  echo "Diff two jar/zip files. Return codes: 0 - no diff, 1 - diff, 2 - couldn't perform diff"
  echo "Syntax: $0 old_archive new_archive [old_root new_root]"
  exit 2
fi

if [ ! -f $1 ]
then
  echo $1 does not exist
  exit 2
fi

if [ ! -f $2 ]
then
  echo $2 does not exist
  exit 2
fi

IGNORES="cat"
OLD=$(cd $(dirname $1) && pwd)/$(basename $1)
NEW=$(cd $(dirname $2) && pwd)/$(basename $2)

if [ $# -gt 3 ]
then
    ROOT1=$(cd $3 && pwd)
    ROOT2=$(cd $4 && pwd)
    OLD_NAME=$(echo $OLD | sed "s|$ROOT1/||")
    NEW_NAME=$(echo $NEW | sed "s|$ROOT2/||")
    if [ $# == 5 ]; then IGNORES="$5"; fi
else
    ROOT1=$(dirname $OLD)/
    ROOT2=$(dirname $NEW)/
    OLD_NAME=$OLD
    NEW_NAME=$NEW
    if [ $# == 3 ]; then IGNORES="$3"; fi
fi

if [ "`uname`" == "SunOS" ]; then
    DIFF=gdiff
else
    DIFF=diff
fi

OLD_SUFFIX="${OLD##*.}"
NEW_SUFFIX="${NEW##*.}"
if [ "$OLD_SUFFIX" != "$NEW_SUFFIX" ]; then
    echo The files do not have the same suffix type!
    exit 2
fi

if [ "$OLD_SUFFIX" != "zip" ] && [ "$OLD_SUFFIX" != "jar" ] && [ "$OLD_SUFFIX" != "sym" ]; then
    echo The files have to be zip, jar or sym! They are $OLD_SUFFIX
    exit 2
fi

UNARCHIVE="unzip -q"

TYPE="$OLD_SUFFIX"

if cmp $OLD $NEW > /dev/null
then
    # The files were bytewise identical.
    exit 0
fi

# Not quite identical, the might still contain the same data.
# Unpack the jar/zip files in temp dirs
if test "x$COMPARE_ROOT" == "x"; then
    COMPARE_ROOT=/tmp/compare_root.$$
    REMOVE_COMPARE_ROOT=true
fi
OLD_TEMPDIR=$COMPARE_ROOT/$OLD_NAME.old
NEW_TEMPDIR=$COMPARE_ROOT/$NEW_NAME.new
mkdir -p $OLD_TEMPDIR
mkdir -p $NEW_TEMPDIR
(cd $OLD_TEMPDIR && rm -rf * ; $UNARCHIVE $OLD)
(cd $NEW_TEMPDIR && rm -rf * ; $UNARCHIVE $NEW)

ONLY1=$(LANG=C $DIFF -rq $OLD_TEMPDIR $NEW_TEMPDIR | grep "^Only in $OLD_TEMPDIR")

if [ -n "$ONLY1" ]; then
    echo "        Only the OLD $OLD_NAME contains:"
    LANG=C $DIFF -rq $DIFF_FLAGS $OLD_TEMPDIR $NEW_TEMPDIR | grep "^Only in $OLD_TEMPDIR" \
        | sed "s|Only in $OLD_TEMPDIR|            |"g | sed 's|: |/|g'
fi

ONLY2=$(LANG=C $DIFF -rq $OLD_TEMPDIR $NEW_TEMPDIR | grep "^Only in $NEW_TEMPDIR")

if [ -n "$ONLY2" ]; then
    echo "        Only the NEW $NEW_NAME contains:"
    LANG=C $DIFF -rq $DIFF_FLAGS $OLD_TEMPDIR $NEW_TEMPDIR | grep "^Only in $NEW_TEMPDIR" \
        | sed "s|Only in $NEW_TEMPDIR|            |"g | sed 's|: |/|g'
fi

DIFFTEXT="/bin/bash `dirname $0`/difftext.sh"

LANG=C $DIFF -rq $DIFF_FLAGS $OLD_TEMPDIR $NEW_TEMPDIR | grep differ | cut -f 2,4 -d ' ' | \
   awk "{ print \"$DIFFTEXT \"\$1\" \"\$2 }" > $COMPARE_ROOT/diffing

/bin/bash $COMPARE_ROOT/diffing > $COMPARE_ROOT/diffs

if [ -s "$COMPARE_ROOT/diffs" ]; then
   echo "        Differing files in $OLD_NAME"
   cat $COMPARE_ROOT/diffs | grep differ | $IGNORES | cut -f 2 -d ' ' | \
          sed "s|$OLD_TEMPDIR|            |g"
fi

# Clean up

if [ "x$REMOVE_COMPARE_ROOT" == xtrue ]; then
    rm -rf $REMOVE_COMPARE_ROOT
fi

exit 1

