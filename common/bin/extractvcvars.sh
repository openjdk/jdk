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

# Must be bash, but that is ok since we are running from cygwin.
# The first argument is the vcvarsall.bat file to run.
# The second argument is the arch arg to give to vcvars.
VCVARSALL="$1"
ARCH_ARG="$2"

# Cannot use the VS10 setup script directly (since it only updates the DOS subshell environment)
# but calculate the difference in Cygwin environment before/after running it and then
# apply the diff.
_vs10varsall=`cygpath -a -m -s "$VCVARSALL"`
_dosvs10varsall=`cygpath -a -w -s $_vs10varsall`
_dosbash=`cygpath -a -w -s \`which bash\`.*`

# generate the set of exported vars before/after the vs10 setup
echo "@echo off" > localdevenvtmp.bat
echo "$_dosbash -c \"export -p\" > localdevenvtmp.export0" >> localdevenvtmp.bat
echo "call $_dosvs10varsall $ARCH_ARG" >> localdevenvtmp.bat
echo "$_dosbash -c \"export -p\" > localdevenvtmp.export1" >> localdevenvtmp.bat
cmd /c localdevenvtmp.bat

# apply the diff (less some non-vs10 vars named by "!")
sort localdevenvtmp.export0 |grep -v "!" > localdevenvtmp.export0.sort
sort localdevenvtmp.export1 |grep -v "!" > localdevenvtmp.export1.sort
comm -1 -3 localdevenvtmp.export0.sort localdevenvtmp.export1.sort > localdevenv.sh
cat localdevenv.sh | sed 's/declare -x /export /g' | sed 's/="/:="/g' | sed 's/\\\\/\\/g' | sed 's/"//g' | \
    sed 's/#/\$\(HASH\)/g' > localdevenv.gmk

# cleanup
rm -f localdevenvtmp*
