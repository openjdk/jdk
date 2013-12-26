#
# Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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

# @test
# @bug 6965072
# @summary Unit test for SDP support
# @build  Sanity
# @run shell sanity.sh

IB_LINKS=ib.links

OS=`uname -s`
case "$OS" in
    SunOS )
        /usr/sbin/dladm show-part -o LINK -p > ${IB_LINKS}
        if [ $? != 0 ]; then
            echo "Unable to get InfiniBand parition link information"
            exit 0
        fi
        ;;
    Linux )
        if [ ! -f /proc/net/sdp ]; then
            echo "InfiniBand SDP module not installed"
            exit 0
        fi
        egrep "^[ \t]+ib" /proc/net/dev|cut -d':' -f1|tr -d '\t ' > ${IB_LINKS}
        ;; 
    * )
        echo "This test only runs on Solaris or Linux"
        exit 0
        ;;
esac

if [ -z "$TESTJAVA" ]; then
    JAVA=java
    TESTCLASSES=.
    TESTSRC=.
else
    JAVA="${TESTJAVA}/bin/java"
fi

CLASSPATH=${TESTCLASSES}:${TESTSRC}
export CLASSPATH

# Run sanity test (IPv4-only for now)
$JAVA ${TESTVMOPTS} -Djava.net.preferIPv4Stack=true Sanity ${IB_LINKS}
