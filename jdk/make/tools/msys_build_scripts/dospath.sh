#
# Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

# A shell script which converts its first argument, which must be an existing 
# path name, into a DOS (aka 8.3) path name. If the path is a file, only the 
# directory part of the whole path will be converted.
# This shell script executes the Visual Basic helper script 'dospath.vbs'
# which must be located in the same directory as this script itself.
# The Visual Basic script will be invoked trough the "Windows Script Host"
# which is available by default on Windows since Windows 98.

pushd `dirname "$0"` > /dev/null
ABS_PATH=`pwd`
popd > /dev/null
if [ -d "$1" ]; then
  echo `cd "$1" && cscript.exe -nologo $ABS_PATH/dospath.vbs`;
elif [ -f "$1" ]; then
  DIR=`dirname "$1"`;
  echo `cd "$DIR" && cscript.exe -nologo $ABS_PATH/dospath.vbs`\\`basename "$1"`;
fi
