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

# Usage: sh shell-tracer.sh <TIME_CMD> <OUTPUT_FILE> <OLD_SHELL> <shell command line>
#
# This shell script is supposed to be set as a replacement for SHELL in make,
# causing it to be called whenever make wants to execute shell commands.
# The <shell command line> is suitable for passing on to the old shell,
# typically beginning with -c.
#
# This script will make sure the shell command line is executed with
# OLD_SHELL -x, and it will also store a simple log of the the time it takes to
# execute the command in the OUTPUT_FILE, using the "time" utility as pointed
# to by TIME_CMD. If TIME_CMD is "-", no timestamp will be stored.

TIME_CMD="$1"
OUTPUT_FILE="$2"
OLD_SHELL="$3"
shift
shift
shift
if [ "$TIME_CMD" != "-" ]; then
"$TIME_CMD" -f "[TIME:%E] $*" -a -o "$OUTPUT_FILE" "$OLD_SHELL" -x "$@"
else
"$OLD_SHELL" -x "$@"
fi
