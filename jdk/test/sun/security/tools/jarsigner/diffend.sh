#
# Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
# @bug 6948909
# @summary Jarsigner removes MANIFEST.MF info for badly packages jar's
#

if [ "${TESTSRC}" = "" ] ; then
  TESTSRC="."
fi
if [ "${TESTCLASSES}" = "" ] ; then
  TESTCLASSES="."
fi
if [ "${TESTJAVA}" = "" ] ; then
  echo "TESTJAVA not set.  Test cannot execute."
  echo "FAILED!!!"
  exit 1
fi

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  SunOS | Linux )
    NULL=/dev/null
    PS=":"
    FS="/"
    CP="${FS}bin${FS}cp -f"
    TMP=/tmp
    ;;
  CYGWIN* )
    NULL=/dev/null
    PS=";"
    FS="/"
    CP="cp -f"
    TMP=/tmp
    ;;
  Windows_* )
    NULL=NUL
    PS=";"
    FS="\\"
    CP="cp -f"
    TMP="c:/temp"
    ;;
  * )
    echo "Unrecognized operating system!"
    exit 1;
    ;;
esac

echo 1 > 1
mkdir META-INF

# Create a fake .RSA file so that jarsigner believes it's signed

touch META-INF/x.RSA

# A MANIFEST.MF using \n as newlines and no double newlines at the end

cat > META-INF/MANIFEST.MF <<EOF
Manifest-Version: 1.0
Created-By: 1.7.0-internal (Sun Microsystems Inc.)
Today: Monday
EOF

# With the fake .RSA file, to trigger the if (wasSigned) block

rm diffend.jar
zip diffend.jar META-INF/MANIFEST.MF META-INF/x.RSA 1

${TESTJAVA}${FS}bin${FS}jarsigner \
    -keystore ${TESTSRC}${FS}JarSigning.keystore \
    -storepass bbbbbb \
    -digestalg SHA1 \
    -signedjar diffend.new.jar \
    diffend.jar c

unzip -p diffend.new.jar META-INF/MANIFEST.MF | grep Today || exit 1

# Without the fake .RSA file, to trigger the else block

rm diffend.jar
zip diffend.jar META-INF/MANIFEST.MF 1

${TESTJAVA}${FS}bin${FS}jarsigner \
    -keystore ${TESTSRC}${FS}JarSigning.keystore \
    -storepass bbbbbb \
    -digestalg SHA1 \
    -signedjar diffend.new.jar \
    diffend.jar c

unzip -p diffend.new.jar META-INF/MANIFEST.MF | grep Today || exit 2

