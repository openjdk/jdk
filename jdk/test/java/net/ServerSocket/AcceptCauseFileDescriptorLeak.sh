#
# Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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
# @bug 6368984
# @summary configuring unconnected Socket before passing to implAccept can cause fd leak
# @build AcceptCauseFileDescriptorLeak
# @run shell AcceptCauseFileDescriptorLeak.sh

OS=`uname -s`
case "$OS" in
    Windows_* | CYGWIN* )
        echo "ulimit not on Windows"
        exit 0
        ;;
    * )
        CLASSPATH=${TESTCLASSES}:${TESTSRC}
        ;;
esac
export CLASSPATH

# hard limit needs to be less than 1024 for this bug
NOFILES=`ulimit -n -H`
if [ "$NOFILES" = "unlimited" ] || [ $NOFILES -ge 1024 ]; then
    ulimit -n 1024
fi

${TESTJAVA}/bin/java AcceptCauseFileDescriptorLeak
