#
# Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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
# @test
# @summary  Tests for the RMI unmarshalling errors not to cause silent failure.
# @author   Jaroslav Bachorik
# @bug      6937053
#
# @run shell TestSerializationMismatch.sh
#

#set -x

#Set appropriate jdk
#

if [ ! -z "${TESTJAVA}" ] ; then
     jdk="$TESTJAVA"
else
     echo "--Error: TESTJAVA must be defined as the pathname of a jdk to test."
     exit 1
fi

SERVER_TESTCLASSES=$TESTCLASSES/Server
CLIENT_TESTCLASSES=$TESTCLASSES/Client

URL_PATH=$SERVER_TESTCLASSES/jmxurl

rm $URL_PATH

mkdir -p $SERVER_TESTCLASSES
mkdir -p $CLIENT_TESTCLASSES

$TESTJAVA/bin/javac -d $CLIENT_TESTCLASSES $TESTSRC/Client/ConfigKey.java $TESTSRC/Client/TestNotification.java $TESTSRC/Client/Client.java
$TESTJAVA/bin/javac -d $SERVER_TESTCLASSES $TESTSRC/Server/ConfigKey.java $TESTSRC/Server/TestNotification.java $TESTSRC/Server/SteMBean.java $TESTSRC/Server/Ste.java $TESTSRC/Server/Server.java

startServer()
{
   ($TESTJAVA/bin/java -classpath $SERVER_TESTCLASSES Server) 1>$URL_PATH &
   SERVER_PID=$!
}

runClient() 
{
   while true
   do
      [ -f $URL_PATH ] && break
      sleep 2
   done
   read JMXURL < $URL_PATH
   
   HAS_ERRORS=`($TESTJAVA/bin/java -classpath $CLIENT_TESTCLASSES Client $JMXURL) 2>&1 | grep -i "SEVERE: Failed to fetch notification, stopping thread. Error is: java.rmi.UnmarshalException"`
}

startServer

runClient

sleep 1 # wait for notifications to arrive

kill "$SERVER_PID"

if [ -z "$HAS_ERRORS" ]
then
  echo "Test PASSED"
  exit 0
fi

echo "Test FAILED"
echo $HAS_ERRORS 1>&2
exit 1

