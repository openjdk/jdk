#!/bin/bash -e
#
# Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
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
# Create a bundle in the current directory, containing what's needed to run
# the 'pandoc' program by the OpenJDK build.

TMPDIR=`mktemp -d -t pandocbundle-XXXX`
trap "rm -rf \"$TMPDIR\"" EXIT

ORIG_DIR=`pwd`
cd "$TMPDIR"
PANDOC_VERSION=1.17.2
FULL_PANDOC_VERSION=1.17.2-1
PACKAGE_VERSION=1.0
TARGET_PLATFORM=linux_x64
BUNDLE_NAME=pandoc-$TARGET_PLATFORM-$PANDOC_VERSION+$PACKAGE_VERSION.tar.gz

wget https://github.com/jgm/pandoc/releases/download/$PANDOC_VERSION/pandoc-$FULL_PANDOC_VERSION-amd64.deb

mkdir pandoc
cd pandoc
ar p ../pandoc-$FULL_PANDOC_VERSION-amd64.deb data.tar.gz | tar xz
cd ..

# Pandoc depends on libgmp.so.10, which in turn depends on libc. No readily
# available precompiled binaries exists which match the requirement of
# support for older linuxes (glibc 2.12), so we'll compile it ourselves.

LIBGMP_VERSION=6.1.2

wget https://gmplib.org/download/gmp/gmp-$LIBGMP_VERSION.tar.xz
mkdir gmp
cd gmp
tar xf ../gmp-$LIBGMP_VERSION.tar.xz
cd gmp-$LIBGMP_VERSION
./configure --prefix=$TMPDIR/pandoc/usr
make
make install
cd ../..

cat > pandoc/pandoc << EOF
#!/bin/bash
# Get an absolute path to this script
this_script_dir=\`dirname \$0\`
this_script_dir=\`cd \$this_script_dir > /dev/null && pwd\`
export LD_LIBRARY_PATH="\$this_script_dir/usr/lib:\$LD_LIBRARY_PATH"
exec \$this_script_dir/usr/bin/pandoc "\$@"
EOF
chmod +x pandoc/pandoc
tar -cvzf ../$BUNDLE_NAME pandoc
cp ../$BUNDLE_NAME "$ORIG_DIR"
