# Copyright 2003-2004 Sun Microsystems, Inc.  All Rights Reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Sun designates this
# particular file as subject to the "Classpath" exception as provided
# by Sun in the LICENSE file that accompanied this code.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.

# @test
# @bug 6466476
# @summary Compatibility test for the old JDK ID mapping and Olson IDs
# @build OldIDMappingTest
# @run shell OldIDMappingTest.sh

: ${TESTJAVA:=${JAVA_HOME}}
: ${TESTCLASSES:="`pwd`"}

JAVA="${TESTJAVA}/bin/java"

STATUS=0

# Expecting the new (Olson compatible) mapping (default)
for I in "" " " no No NO false False FALSE Hello
do
    if [ x"$I" != x ]; then
	D="-Dsun.timezone.ids.oldmapping=${I}"
    fi
    if ! ${JAVA} ${D} -cp ${TESTCLASSES} OldIDMappingTest -new; then
	STATUS=1
    fi
done

# Expecting the old mapping
for I in true True TRUE yes Yes YES
do
    if [ "x$I" != x ]; then
	D="-Dsun.timezone.ids.oldmapping=${I}"
    fi
    if ! ${JAVA} ${D} -cp ${TESTCLASSES} OldIDMappingTest -old; then
	STATUS=1
    fi
done

exit ${STATUS}
