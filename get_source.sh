#!/bin/sh

#
# Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
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

# Version check

# required
reqdmajor=1
reqdminor=5
reqdrev=0

# requested
rqstmajor=2
rqstminor=6
rqstrev=3

# installed
hgwhere="`which hg 2> /dev/null | grep -v '^no hg in '`"
if [ "x$hgwhere" = "x" ]; then
  echo "ERROR: Could not locate Mercurial command" >&2
  exit 126
fi

hgversion="`hg --version 2> /dev/null | sed -n -e 's@^Mercurial Distributed SCM (version \(.*\))\$@\1@p'`"
if [ "x${hgversion}" = "x" ] ; then
  echo "ERROR: Could not determine Mercurial version" >&2
  exit 126
fi

hgmajor="`echo $hgversion | cut -f 1 -d .`"
hgminor="`echo $hgversion | cut -f 2 -d .`"
hgrev="`echo $hgversion.0 | cut -f 3 -d .`" # rev is omitted for minor and major releases

# Require
if [ $hgmajor -lt $reqdmajor -o \( $hgmajor -eq $reqdmajor -a $hgminor -lt $reqdminor \) -o \( $hgmajor -eq $reqdmajor -a $hgminor -eq $reqdminor -a $hgrev -lt $reqdrev \) ] ; then
  echo "ERROR: Mercurial version $reqdmajor.$reqdminor.$reqdrev or later is required. $hgwhere is version $hgversion" >&2
  exit 126
fi

# Request
if [ $hgmajor -lt $rqstmajor -o \( $hgmajor -eq $rqstmajor -a $hgminor -lt $rqstminor \) -o \( $hgmajor -eq $rqstmajor -a $hgminor -eq $rqstminor -a $hgrev -lt $rqstrev \) ] ; then
  echo "WARNING: Mercurial version $rqstmajor.$rqstminor.$rqstrev or later is recommended. $hgwhere is version $hgversion" >&2
fi

# Get clones of all absent nested repositories (harmless if already exist)
sh ./common/bin/hgforest.sh clone "$@" || exit $?

# Update all existing repositories to the latest sources
sh ./common/bin/hgforest.sh pull -u
