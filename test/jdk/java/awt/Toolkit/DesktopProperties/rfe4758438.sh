# Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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

if [ -z "${TESTJAVA}" ]; then
  echo "TESTJAVA undefined: can't continue."
  exit 1
fi

OS=`uname`

case "$OS" in
    Linux* )
        ;;
    * )
        echo "This Feature is not to be tested on $OS"
        exit 0
        ;;
esac

printf "\n/* Test env:\n\n"
env
printf "\n*/\n\n"

XDG_GNOME=$(echo $XDG_CURRENT_DESKTOP | grep -i gnome)

if [ -z "$XDG_GNOME" ] \
     && [ ${GNOME_DESKTOP_SESSION_ID:-nonset} = "nonset" ] \
     && [ ${GNOME_SESSION_NAME:-nonset} = "nonset" ]
then
    echo "This test should run under Gnome"
    exit 0
fi

SCHEMAS=`gsettings list-schemas | wc -l`

if [ $SCHEMAS -eq 0 ];
then
    TOOL=`which gconftool-2`
    USE_GSETTINGS="false"
else
    TOOL=`which gsettings`
    USE_GSETTINGS="true"
fi

cd ${TESTSRC}
echo $PWD
echo "${TESTJAVA}/bin/javac -d ${TESTCLASSES} rfe4758438.java"

set -e
${TESTJAVA}/bin/javac -d ${TESTCLASSES} rfe4758438.java
set +e


cd ${TESTCLASSES}
${TESTJAVA}/bin/java -DuseGsettings=${USE_GSETTINGS} -Dtool=${TOOL} ${TESTVMOPTS} rfe4758438

if [ $? -ne 0 ]
then
    echo "Test failed. See the error stream output"
    exit 1
fi
exit 0
