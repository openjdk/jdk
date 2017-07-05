#!/bin/sh
#
# Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
# @library /lib/testlibrary
# @compile -XDignore.symbol.file=true SimpleNameService.java
#            LookupTest.java SimpleNameServiceDescriptor.java
# @build jdk.testlibrary.Utils
# @run shell/timeout=50 lookup.sh
#

OS=`uname -s`
case ${OS} in
Windows_* | CYGWIN*)
    PS=";"
    FS="\\"
    ;;
*)
    PS=":"
    FS="/"
    ;;
esac


port=`${TESTJAVA}/bin/java -cp ${TESTCLASSES} LookupTest -getport`

cat << POLICY > policy
grant {
    permission java.net.URLPermission "http://allowedAndFound.com:${port}/-", "*:*";
    permission java.net.URLPermission "http://allowedButNotfound.com:${port}/-", "*:*";
    permission java.io.FilePermission "<<ALL FILES>>", "read,write,delete";
    permission java.util.PropertyPermission "java.io.tmpdir", "read";

    // needed for HttpServer
    permission "java.net.SocketPermission" "localhost:1024-", "resolve,accept";
};
POLICY

${TESTJAVA}/bin/java ${TESTVMOPTS} \
    -Djava.security.policy=file:./policy \
    -Dsun.net.spi.nameservice.provider.1=simple,sun \
    -cp ${TESTCLASSES}${PS}${TESTSRC} LookupTest -runtest ${port}
