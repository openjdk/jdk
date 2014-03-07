#
# Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4890703
# @summary Unit test for Solaris SDP support
# @build ProbeIB Sanity
# @run shell sanity.sh

# Check we are on Solaris and that SDP is enabled
OS=`uname -s`
if [ "$OS" != "SunOS" ]; then
    echo "This is a Solaris-only test"
    exit 0
fi

IB_LINKS=ib.links
IB_ADDRS=ib.addrs

# Display IB partition link information
# (requires Solaris 11, will fail on Solaris 10)
/usr/sbin/dladm show-part -o LINK -p > ${IB_LINKS}
if [ $? != 0 ]; then
    echo "Unable to get IB parition link information"
    exit 0
fi

if [ -z "$TESTJAVA" ]; then
    JAVA=java
    TESTCLASSES=.
    TESTSRC=.
else
    JAVA="${TESTJAVA}/bin/java"
fi

CLASSPATH=${TESTCLASSES}:${TESTSRC}
export CLASSPATH

# Probe for IP addresses plumbed to IB interfaces
$JAVA ${TESTVMOPTS} -Djava.net.preferIPv4Stack=true ProbeIB ${IB_LINKS} > ${IB_ADDRS}

# Create sdp.conf
SDPCONF=sdp.conf
rm ${SDPCONF}
touch ${SDPCONF}
cat ${IB_ADDRS} | while read ADDR
do
   echo "bind ${ADDR} *" > ${SDPCONF}
   echo "connect ${ADDR} *" >> ${SDPCONF}
done

# Sanity check
$JAVA ${TESTVMOPTS} -Djava.net.preferIPv4Stack=true -Dcom.sun.sdp.conf=${SDPCONF} -Dcom.sun.sdp.debug Sanity
