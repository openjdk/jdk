#
# Copyright 2000-2003 Sun Microsystems, Inc.  All Rights Reserved.
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
# @bug 4393671
# @summary URL constructor URL(URL context, String spec) FAILED with specific input in merlin
#
OS=`uname -s`
case "$OS" in
  SunOS | Linux )
    PS=":"
    FS="/"
    ;;
  CYGWIN* )
    PS=";"
    FS="/"
    ;;
  Windows* )
    PS=";"
    FS="\\"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac
${TESTJAVA}${FS}bin${FS}javac -d . ${TESTSRC}${FS}Constructor.java

failures=0

go() {
    echo ''
    ${TESTJAVA}${FS}bin${FS}java Constructor $1
    if [ $? != 0 ]; then failures=`expr $failures + 1`; fi
}

go ${TESTSRC}${FS}share_file_urls
go ${TESTSRC}${FS}jar_urls
go ${TESTSRC}${FS}normal_http_urls
go ${TESTSRC}${FS}ftp_urls
go ${TESTSRC}${FS}abnormal_http_urls

if [ "$failures" != "0" ]; then
    echo $failures tests failed
    exit 1;
fi
