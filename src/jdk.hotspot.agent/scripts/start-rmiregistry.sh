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
    echo "usage: $0&"
    echo " Start the rmi registry with with sa-jdi.jar on the bootclasspath"
    echo " for use by the debug server."
    echo " JAVA_HOME must contain the pathname of a J2SE 1.5" 
    echo " installation." 
    exit 0
fi

if [ ! -x ${JAVA_HOME}/bin/rmiregistry -o ! -r ${JAVA_HOME}/lib/sa-jdi.jar ] ; 
then
    echo '${JAVA_HOME} does not point to a working J2SE installation.'
    exit 1
fi

${JAVA_HOME}/bin/rmiregistry -J-Xbootclasspath/p:${JAVA_HOME}/lib/sa-jdi.jar
