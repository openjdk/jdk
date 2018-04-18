#!/bin/bash
#
# Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

# This script copies parts of a Visual Studio 2013 installation into a devkit
# suitable for building OpenJDK and OracleJDK. Needs to run in Cygwin.
# erik.joelsson@oracle.com

VS_VERSION="2013"
VS_VERSION_NUM="12.0"
VS_VERSION_NUM_NODOT="120"
SDK_VERSION="8.1"
VS_VERSION_SP="SP4"

SCRIPT_DIR="$(cd "$(dirname $0)" > /dev/null && pwd)"
BUILD_DIR="${SCRIPT_DIR}/../../build/devkit"
DEVKIT_ROOT="${BUILD_DIR}/VS${VS_VERSION}${VS_VERSION_SP}-devkit"
DEVKIT_BUNDLE="${DEVKIT_ROOT}.tar.gz"

echo "Creating devkit in $DEVKIT_ROOT"

MSVCR_DLL=Microsoft.VC${VS_VERSION_NUM_NODOT}.CRT/msvcr${VS_VERSION_NUM_NODOT}.dll
MSVCP_DLL=Microsoft.VC${VS_VERSION_NUM_NODOT}.CRT/msvcp${VS_VERSION_NUM_NODOT}.dll

################################################################################
# Copy Visual Studio files

eval VSNNNCOMNTOOLS="\"\${VS${VS_VERSION_NUM_NODOT}COMNTOOLS}\""
VS_INSTALL_DIR="$(cygpath "$VSNNNCOMNTOOLS/../..")"
echo "VS_INSTALL_DIR: $VS_INSTALL_DIR"

if [ ! -d $DEVKIT_ROOT/VC ]; then
    echo "Copying VC..."
    mkdir -p $DEVKIT_ROOT/VC/bin
    cp -r "$VS_INSTALL_DIR/VC/bin/amd64" $DEVKIT_ROOT/VC/bin/
    cp    "$VS_INSTALL_DIR/VC/bin/"*.* $DEVKIT_ROOT/VC/bin/
    cp -r "$VS_INSTALL_DIR/VC/bin/1033/" $DEVKIT_ROOT/VC/bin/
    mkdir -p $DEVKIT_ROOT/VC/lib
    cp -r "$VS_INSTALL_DIR/VC/lib/amd64" $DEVKIT_ROOT/VC/lib/
    cp    "$VS_INSTALL_DIR/VC/lib/"*.* $DEVKIT_ROOT/VC/lib/
    cp -r "$VS_INSTALL_DIR/VC/include" $DEVKIT_ROOT/VC/
    mkdir -p $DEVKIT_ROOT/VC/atlmfc/lib
    cp -r "$VS_INSTALL_DIR/VC/atlmfc/include" $DEVKIT_ROOT/VC/atlmfc/
    cp -r "$VS_INSTALL_DIR/VC/atlmfc/lib/amd64" $DEVKIT_ROOT/VC/atlmfc/lib/
    cp    "$VS_INSTALL_DIR/VC/atlmfc/lib/"*.* $DEVKIT_ROOT/VC/atlmfc/lib/
    mkdir -p $DEVKIT_ROOT/VC/redist
    cp -r "$VS_INSTALL_DIR/VC/redist/x64" $DEVKIT_ROOT/VC/redist/
    cp -r "$VS_INSTALL_DIR/VC/redist/x86" $DEVKIT_ROOT/VC/redist/
    # The redist runtime libs are needed to run the compiler but may not be
    # installed on the machine where the devkit will be used.
    cp $DEVKIT_ROOT/VC/redist/x86/$MSVCR_DLL $DEVKIT_ROOT/VC/bin/
    cp $DEVKIT_ROOT/VC/redist/x86/$MSVCP_DLL $DEVKIT_ROOT/VC/bin/
    cp $DEVKIT_ROOT/VC/redist/x64/$MSVCR_DLL $DEVKIT_ROOT/VC/bin/amd64/
    cp $DEVKIT_ROOT/VC/redist/x64/$MSVCP_DLL $DEVKIT_ROOT/VC/bin/amd64/
    # The msvcdis dll is needed to run some of the tools in VC/bin but is not
    # shipped in that directory. Copy it from the common dir.
    cp "$VS_INSTALL_DIR/Common7/IDE/msvcdis${VS_VERSION_NUM_NODOT}.dll" \
        $DEVKIT_ROOT/VC/bin/
fi

################################################################################
# Copy SDK files

PROGRAMFILES_X86="`env | sed -n 's/^ProgramFiles(x86)=//p'`"
SDK_INSTALL_DIR="$(cygpath "$PROGRAMFILES_X86/Windows Kits/$SDK_VERSION")"
echo "SDK_INSTALL_DIR: $SDK_INSTALL_DIR"

if [ ! -d $DEVKIT_ROOT/$SDK_VERSION ]; then
    echo "Copying SDK..."
    mkdir -p $DEVKIT_ROOT/$SDK_VERSION/bin
    cp -r "$SDK_INSTALL_DIR/bin/x64" $DEVKIT_ROOT/$SDK_VERSION/bin/
    cp -r "$SDK_INSTALL_DIR/bin/x86" $DEVKIT_ROOT/$SDK_VERSION/bin/
    mkdir -p $DEVKIT_ROOT/$SDK_VERSION/lib
    cp -r "$SDK_INSTALL_DIR/lib/"winv*/um/x64 $DEVKIT_ROOT/$SDK_VERSION/lib/
    cp -r "$SDK_INSTALL_DIR/lib/"winv*/um/x86 $DEVKIT_ROOT/$SDK_VERSION/lib/
    cp -r "$SDK_INSTALL_DIR/include" $DEVKIT_ROOT/$SDK_VERSION/
fi

################################################################################
# Generate devkit.info

echo-info() {
    echo "$1" >> $DEVKIT_ROOT/devkit.info
}

echo "Generating devkit.info..."
rm -f $DEVKIT_ROOT/devkit.info
echo-info "# This file describes to configure how to interpret the contents of this devkit"
echo-info "DEVKIT_NAME=\"Microsoft Visual Studio $VS_VERSION $VS_VERSION_SP (devkit)\""
echo-info "DEVKIT_VS_VERSION=\"$VS_VERSION\""
echo-info ""
echo-info "DEVKIT_TOOLCHAIN_PATH_x86=\"\$DEVKIT_ROOT/VC/bin:\$DEVKIT_ROOT/$SDK_VERSION/bin/x86\""
echo-info "DEVKIT_VS_INCLUDE_x86=\"\$DEVKIT_ROOT/VC/include;\$DEVKIT_ROOT/VC/atlmfc/include;\$DEVKIT_ROOT/$SDK_VERSION/include/shared;\$DEVKIT_ROOT/$SDK_VERSION/include/um;\$DEVKIT_ROOT/$SDK_VERSION/include/winrt\""
echo-info "DEVKIT_VS_LIB_x86=\"\$DEVKIT_ROOT/VC/lib;\$DEVKIT_ROOT/VC/atlmfc/lib;\$DEVKIT_ROOT/$SDK_VERSION/lib/x86\""
echo-info "DEVKIT_MSVCR_DLL_x86=\"\$DEVKIT_ROOT/VC/redist/x86/$MSVCR_DLL\""
echo-info "DEVKIT_MSVCP_DLL_x86=\"\$DEVKIT_ROOT/VC/redist/x86/$MSVCP_DLL\""
echo-info ""
echo-info "DEVKIT_TOOLCHAIN_PATH_x86_64=\"\$DEVKIT_ROOT/VC/bin/amd64:\$DEVKIT_ROOT/$SDK_VERSION/bin/x64:\$DEVKIT_ROOT/$SDK_VERSION/bin/x86\""
echo-info "DEVKIT_VS_INCLUDE_x86_64=\"\$DEVKIT_ROOT/VC/include;\$DEVKIT_ROOT/VC/atlmfc/include;\$DEVKIT_ROOT/$SDK_VERSION/include/shared;\$DEVKIT_ROOT/$SDK_VERSION/include/um;\$DEVKIT_ROOT/$SDK_VERSION/include/winrt\""
echo-info "DEVKIT_VS_LIB_x86_64=\"\$DEVKIT_ROOT/VC/lib/amd64;\$DEVKIT_ROOT/VC/atlmfc/lib/amd64;\$DEVKIT_ROOT/$SDK_VERSION/lib/x64\""
echo-info "DEVKIT_MSVCR_DLL_x86_64=\"\$DEVKIT_ROOT/VC/redist/x64/$MSVCR_DLL\""
echo-info "DEVKIT_MSVCP_DLL_x86_64=\"\$DEVKIT_ROOT/VC/redist/x64/$MSVCP_DLL\""

################################################################################
# Copy this script

echo "Copying this script..."
cp $0 $DEVKIT_ROOT/

################################################################################
# Create bundle

echo "Creating bundle: $DEVKIT_BUNDLE"
(cd "$DEVKIT_ROOT" && tar zcf "$DEVKIT_BUNDLE" .)
