#! /bin/sh
#
# Copyright (c) 1999, 2014, Oracle and/or its affiliates. All rights reserved.
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

# This script is used only from top.make.
# The macro $(MFLAGS-adjusted) calls this script to
# adjust the "-j" arguments to take into account
# the HOTSPOT_BUILD_JOBS variable.  The default
# handling of the "-j" argument by gnumake does
# not meet our needs, so we must adjust it ourselves.

# This argument adjustment applies to two recursive
# calls to "$(MAKE) $(MFLAGS-adjusted)" in top.make.
# One invokes adlc.make, and the other invokes vm.make.
# The adjustment propagates the desired concurrency
# level down to the sub-make (of the adlc or vm).
# The default behavior of gnumake is to run all
# sub-makes without concurrency ("-j1").

# Also, we use a make variable rather than an explicit
# "-j<N>" argument to control this setting, so that
# the concurrency setting (which must be tuned separately
# for each MP system) can be set via an environment variable.
# The recommended setting is 1.5x to 2x the number of available
# CPUs on the MP system, which is large enough to keep the CPUs
# busy (even though some jobs may be I/O bound) but not too large,
# we may presume, to overflow the system's swap space.

set -eu

default_build_jobs=4

case $# in
[12])	true;;
*)	>&2 echo "Usage: $0 ${MFLAGS} ${HOTSPOT_BUILD_JOBS}"; exit 2;;
esac

MFLAGS=$1
HOTSPOT_BUILD_JOBS=${2-}

# Normalize any -jN argument to the form " -j${HBJ}"
MFLAGS=`
	echo "$MFLAGS" \
	| sed '
		s/^-/ -/
		s/ -\([^ 	I][^ 	I]*\)j/ -\1 -j/
		s/ -j[0-9][0-9]*/ -j/
		s/ -j\([^ 	]\)/ -j -\1/
		s/ -j/ -j'${HOTSPOT_BUILD_JOBS:-${default_build_jobs}}'/
	' `

case ${HOTSPOT_BUILD_JOBS} in \

'') case ${MFLAGS} in
    *\ -j*)
	>&2 echo "# Note: -jN is ineffective for setting parallelism in this makefile." 
	>&2 echo "# please set HOTSPOT_BUILD_JOBS=${default_build_jobs} in the command line or environment."
    esac;;

?*) case ${MFLAGS} in
     *\ -j*) true;;
     *)      MFLAGS="-j${HOTSPOT_BUILD_JOBS} ${MFLAGS}";;
    esac;;
esac

echo "${MFLAGS}"
