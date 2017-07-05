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

usage()
{
  echo "usage:   $0 <java executable> <core file>"
  exit 1
}
#
if [ $# -lt 2 ]; then
    usage
else
    EXEC_FILE="${1}"
    CORE_FILE="${2}"
    echo "$0 attaching to core=${CORE_FILE}"
fi
#

. `dirname $0`/saenv64.sh

$JAVA_HOME/bin/jdb -J-d64 -J-Xbootclasspath/a:$SA_CLASSPATH:$JAVA_HOME/lib/tools.jar \
 -J-Dsun.boot.library.path=$JAVA_HOME/jre/lib/$CPU:$SA_LIBPATH \
 -connect sun.jvm.hotspot.jdi.SACoreAttachingConnector:core=${CORE_FILE},javaExecutable=${EXEC_FILE}
