#
# Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.
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
SDPADM=/usr/sbin/sdpadm
if [ ! -f ${SDPADM} ]; then
    echo "SDP not available"
    exit 0
fi
${SDPADM} status|grep Enabled
if [ $? != 0 ]; then 
    echo "SDP not enabled"
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
$JAVA -Djava.net.preferIPv4Stack=true ProbeIB > ib_addrs

# Create sdp.conf
SDPCONF=sdp.conf
rm ${SDPCONF}
touch ${SDPCONF}
cat ib_addrs | while read ADDR
do
   echo "bind ${ADDR} *" > ${SDPCONF}
   echo "connect ${ADDR} *" >> ${SDPCONF}
done

# Sanity check
$JAVA -Djava.net.preferIPv4Stack=true -Dcom.sun.sdp.conf=${SDPCONF} -Dcom.sun.sdp.debug Sanity
