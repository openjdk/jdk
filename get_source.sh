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

to_stderr() {
    echo "$@" >&2
}

error() {
    to_stderr "ERROR: $1"
    exit ${2:-126}
}

warning() {
    to_stderr "WARNING: $1"
}

version_field() {
  # rev is typically omitted for minor and major releases
  field=`echo ${1}.0 | cut -f ${2} -d .`
  if expr 1 + $field >/dev/null 2> /dev/null; then
    echo $field
  else
    echo -1
  fi
}

# Version check

# required
reqdmajor=1
reqdminor=4
reqdrev=0

# requested
rqstmajor=2
rqstminor=6
rqstrev=3


# installed
hgwhere="`command -v hg`"
if [ "x$hgwhere" = "x" ]; then
  error "Could not locate Mercurial command"
fi

hgversion="`LANGUAGE=en hg --version 2> /dev/null | sed -n -e 's@^Mercurial Distributed SCM (version \([^+]*\).*)\$@\1@p'`"
if [ "x${hgversion}" = "x" ] ; then
  error "Could not determine Mercurial version of $hgwhere"
fi

hgmajor="`version_field $hgversion 1`"
hgminor="`version_field $hgversion 2`"
hgrev="`version_field $hgversion 3`"

if [ $hgmajor -eq -1 -o $hgminor -eq -1 -o $hgrev -eq -1 ] ; then
  error "Could not determine Mercurial version of $hgwhere from \"$hgversion\""
fi


# Require
if [ $hgmajor -lt $reqdmajor -o \( $hgmajor -eq $reqdmajor -a $hgminor -lt $reqdminor \) -o \( $hgmajor -eq $reqdmajor -a $hgminor -eq $reqdminor -a $hgrev -lt $reqdrev \) ] ; then
  error "Mercurial version $reqdmajor.$reqdminor.$reqdrev or later is required. $hgwhere is version $hgversion"
fi


# Request
if [ $hgmajor -lt $rqstmajor -o \( $hgmajor -eq $rqstmajor -a $hgminor -lt $rqstminor \) -o \( $hgmajor -eq $rqstmajor -a $hgminor -eq $rqstminor -a $hgrev -lt $rqstrev \) ] ; then
  warning "Mercurial version $rqstmajor.$rqstminor.$rqstrev or later is recommended. $hgwhere is version $hgversion"
fi


# Get clones of all absent nested repositories (harmless if already exist)
sh ./common/bin/hgforest.sh clone "$@" || exit $?

# Update all existing repositories to the latest sources
sh ./common/bin/hgforest.sh pull -u
