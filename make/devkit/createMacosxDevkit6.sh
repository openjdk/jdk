#!/bin/bash
#
# Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

# This script copies part of an Xcode installer into a devkit suitable
# for building OpenJDK and OracleJDK. The installation .dmg files for Xcode
# and the aux tools need to be available.
# erik.joelsson@oracle.com

USAGE="$0 <Xcode.dmg> <XQuartz.dmg> <gnu make binary> [<auxtools.dmg>]"

if [ "$1" = "" ] || [ "$2" = "" ]; then
    echo $USAGE
    exit 1
fi

XCODE_DMG="$1"
XQUARTZ_DMG="$2"
GNU_MAKE="$3"
AUXTOOLS_DMG="$4"

SCRIPT_DIR="$(cd "$(dirname $0)" > /dev/null && pwd)"
BUILD_DIR="${SCRIPT_DIR}/../../build/devkit"

# Mount XCODE_DMG
if [ -e "/Volumes/Xcode" ]; then
    hdiutil detach /Volumes/Xcode
fi
hdiutil attach $XCODE_DMG

# Find the version of Xcode
XCODE_VERSION="$(/Volumes/Xcode/Xcode.app/Contents/Developer/usr/bin/xcodebuild -version \
    | awk '/Xcode/ { print $2 }' )"
SDK_VERSION="MacOSX10.9"
if [ ! -e "/Volumes/Xcode/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/${SDK_VERSION}.sdk" ]; then
    echo "Expected SDK version not found: ${SDK_VERSION}"
    exit 1
fi

DEVKIT_ROOT="${BUILD_DIR}/Xcode${XCODE_VERSION}-${SDK_VERSION}"
DEVKIT_BUNDLE="${DEVKIT_ROOT}.tar.gz"

echo "Xcode version: $XCODE_VERSION"
echo "Creating devkit in $DEVKIT_ROOT"

################################################################################
# Copy files to root
mkdir -p $DEVKIT_ROOT
if [ ! -d $DEVKIT_ROOT/Xcode.app ]; then
    echo "Copying Xcode.app..."
    cp -RH "/Volumes/Xcode/Xcode.app" $DEVKIT_ROOT/
fi
# Trim out some seemingly unneeded parts to save space.
rm -rf $DEVKIT_ROOT/Xcode.app/Contents/Applications
rm -rf $DEVKIT_ROOT/Xcode.app/Contents/Developer/Platforms/iPhone*
rm -rf $DEVKIT_ROOT/Xcode.app/Contents/Developer/Documentation
rm -rf $DEVKIT_ROOT/Xcode.app/Contents/Developer/usr/share/man
( cd $DEVKIT_ROOT/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs \
    && rm -rf `ls | grep -v ${SDK_VERSION}` )
rm -rf $DEVKIT_ROOT/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/${SDK_VERSION}.sdk/usr/share/man

hdiutil detach /Volumes/Xcode

################################################################################
# Copy Freetype into sysroot
if [ -e "/Volumes/XQuartz-*" ]; then
    hdiutil detach /Volumes/XQuartz-*
fi
hdiutil attach $XQUARTZ_DMG

echo "Copying freetype..."
rm -rf /tmp/XQuartz
pkgutil --expand /Volumes/XQuartz-*/XQuartz.pkg /tmp/XQuartz/
rm -rf /tmp/x11
mkdir /tmp/x11
cd /tmp/x11
cat /tmp/XQuartz/x11.pkg/Payload | gunzip -dc | cpio -i

mkdir -p $DEVKIT_ROOT/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX10.9.sdk/usr/X11/include/
mkdir -p $DEVKIT_ROOT/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX10.9.sdk/usr/X11/lib/
cp -RH opt/X11/include/freetype2 \
    $DEVKIT_ROOT/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX10.9.sdk/usr/X11/include/
cp -RH opt/X11/include/ft2build.h \
    $DEVKIT_ROOT/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX10.9.sdk/usr/X11/include/
cp -RH opt/X11/lib/libfreetype.* \
    $DEVKIT_ROOT/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX10.9.sdk/usr/X11/lib/

cd -

hdiutil detach /Volumes/XQuartz-*

################################################################################
# Copy gnu make
mkdir -p $DEVKIT_ROOT/bin
cp $GNU_MAKE $DEVKIT_ROOT/bin

################################################################################
# Optionally copy PackageMaker

if [ -e "$AUXTOOLS_DMG" ]; then
    if [ -e "/Volumes/Auxiliary Tools" ]; then
        hdiutil detach "/Volumes/Auxiliary Tools"
    fi
    hdiutil attach $AUXTOOLS_DMG

    echo "Copying PackageMaker.app..."
    cp -RH "/Volumes/Auxiliary Tools/PackageMaker.app" $DEVKIT_ROOT/

    hdiutil detach "/Volumes/Auxiliary Tools"
fi

################################################################################
# Generate devkit.info

echo-info() {
    echo "$1" >> $DEVKIT_ROOT/devkit.info
}

echo "Generating devkit.info..."
rm -f $DEVKIT_ROOT/devkit.info
echo-info "# This file describes to configure how to interpret the contents of this devkit"
echo-info "# The parameters used to create this devkit were:"
echo-info "# $*"
echo-info "DEVKIT_NAME=\"Xcode $XCODE_VERSION (devkit)\""
echo-info "DEVKIT_TOOLCHAIN_PATH=\"\$DEVKIT_ROOT/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin:\$DEVKIT_ROOT/Xcode.app/Contents/Developer/usr/bin\""
echo-info "DEVKIT_SYSROOT=\"\$DEVKIT_ROOT/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX10.9.sdk\""
echo-info "DEVKIT_EXTRA_PATH=\"\$DEVKIT_ROOT/bin:\$DEVKIT_ROOT/PackageMaker.app/Contents/MacOS:\$DEVKIT_TOOLCHAIN_PATH\""

################################################################################
# Copy this script

echo "Copying this script..."
cp $0 $DEVKIT_ROOT/

################################################################################
# Create bundle

echo "Creating bundle..."
(cd $DEVKIT_ROOT && tar c - . | gzip - > "$DEVKIT_BUNDLE")
