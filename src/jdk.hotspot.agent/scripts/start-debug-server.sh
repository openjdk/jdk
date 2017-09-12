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

if [ "$1" = "-help" ] ; then
     echo "Usage: $0 <pid>"
     echo "       $0 <java executable> <core file>"
     echo " Start the JDI debug server on <pid> or <core file>"
     echo " so that it can be debugged from a remote machine."
     echo " JAVA_HOME must contain the pathname of a J2SE 1.5"
     echo " installation."
     exit 0
fi

if [ ! -x ${JAVA_HOME}/bin/java -o ! -r ${JAVA_HOME}/lib/sa-jdi.jar ] ; 
then
    echo '${JAVA_HOME} does not point to a working J2SE 1.5 installation.'
    exit 1
fi

${JAVA_HOME}/bin/java -classpath ${JAVA_HOME}/lib/sa-jdi.jar sun.jvm.hotspot.jdi.SADebugServer $*
