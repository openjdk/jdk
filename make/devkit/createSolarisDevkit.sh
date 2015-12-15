#!/bin/bash
#
# Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

# This script creates a devkit for building OpenJDK on Solaris by copying
# part of a Solaris Studio installation and cretaing a sysroot by installing
# a limited set of system packages. It is assumed that a suitable pkg
# publisher is configured for the system where the script is executed.
#
# The Solaris Studio installation must contain at least these packages:
# developer/solarisstudio-124/backend               12.4-1.0.6.0               i--
# developer/solarisstudio-124/c++                   12.4-1.0.10.0              i--
# developer/solarisstudio-124/cc                    12.4-1.0.4.0               i--
# developer/solarisstudio-124/library/c++-libs      12.4-1.0.10.0              i--
# developer/solarisstudio-124/library/math-libs     12.4-1.0.0.1               i--
# developer/solarisstudio-124/library/studio-gccrt  12.4-1.0.0.1               i--
# developer/solarisstudio-124/studio-common         12.4-1.0.0.1               i--
# developer/solarisstudio-124/studio-ja             12.4-1.0.0.1               i--
# developer/solarisstudio-124/studio-legal          12.4-1.0.0.1               i--
# developer/solarisstudio-124/studio-zhCN           12.4-1.0.0.1               i--
# In particular backend 12.4-1.0.6.0 contains a critical patch for the sparc
# version.
#
# erik.joelsson@oracle.com

USAGE="$0 <Solaris Studio installation> <Path to gnu make binary>"

if [ "$1" = "" ] || [ "$2" = "" ]; then
  echo $USAGE
  exit 1
fi

SOLARIS_STUDIO_VERSION=12u4
SOLARIS_VERSION=11u1
case `uname -p` in
  i*)
    ARCH=x86
    ;;
  sparc*)
    ARCH=sparc
    ;;
esac

SOLARIS_STUDIO_SRC=$1
GNU_MAKE=$2

SCRIPT_DIR="$(cd "$(dirname $0)" > /dev/null && pwd)"
BUILD_DIR="${SCRIPT_DIR}/../../build/devkit"

DEVKIT_NAME=SS${SOLARIS_STUDIO_VERSION}-Solaris${SOLARIS_VERSION}
DEVKIT_ROOT=${BUILD_DIR}/${DEVKIT_NAME}
BUNDLE_NAME=${DEVKIT_NAME}.tar.gz
BUNDLE=${BUILD_DIR}/${BUNDLE_NAME}
INSTALL_ROOT=${BUILD_DIR}/install-root
SYSROOT=${DEVKIT_ROOT}/sysroot
SOLARIS_STUDIO_SUBDIR=SS${SOLARIS_STUDIO_VERSION}
SOLARIS_STUDIO_DIR=${DEVKIT_ROOT}/${SOLARIS_STUDIO_SUBDIR}

# Extract the publisher from the system
if [ -z "${PUBLISHER_URI}" ]; then
  PUBLISHER_URI="$(pkg publisher solaris | grep URI | awk '{ print $3 }')"
fi

if [ ! -d $INSTALL_ROOT ]; then
  echo "Creating $INSTALL_ROOT and installing packages"
  pkg image-create $INSTALL_ROOT
  pkg -R $INSTALL_ROOT set-publisher -P -g ${PUBLISHER_URI} solaris
  pkg -R $INSTALL_ROOT install --accept $(cat solaris11.1-package-list.txt)
else
  echo "Skipping installing packages"
fi

if [ ! -d $SYSROOT ]; then
  echo "Copying from $INSTALL_ROOT to $SYSROOT"
  mkdir -p $SYSROOT
  cp -rH $INSTALL_ROOT/lib $SYSROOT/
  mkdir $SYSROOT/usr
  # Some of the tools in sysroot are needed in the OpenJDK build but cannot be
  # run from their current location due to relative runtime paths in the
  # binaries. Move the sysroot/usr/bin directory to the outer bin and have them
  # be runnable from there to force them to link to the system libraries
  cp -rH $INSTALL_ROOT/usr/bin $DEVKIT_ROOT
  cp -rH $INSTALL_ROOT/usr/lib $SYSROOT/usr/
  cp -rH $INSTALL_ROOT/usr/include $SYSROOT/usr/
  pkg -R $INSTALL_ROOT list > $SYSROOT/pkg-list.txt
else
  echo "Skipping copying to $SYSROOT"
fi

if [ ! -d $SOLARIS_STUDIO_DIR ]; then
  echo "Copying Solaris Studio from $SOLARIS_STUDIO_SRC"
  cp -rH $SOLARIS_STUDIO_SRC ${SOLARIS_STUDIO_DIR%/*}
  mv ${SOLARIS_STUDIO_DIR%/*}/${SOLARIS_STUDIO_SRC##*/} $SOLARIS_STUDIO_DIR
  # Solaris Studio 12.4 requires /lib/libmmheap.so.1 to run, but this lib is not
  # installed by default on all Solaris systems. Sneak it in from the sysroot to
  # make it run OOTB on more systems.
  cp $SYSROOT/lib/libmmheap.so.1 $SOLARIS_STUDIO_DIR/lib/compilers/sys/
else
  echo "Skipping copying of Solaris Studio"
fi

echo "Copying gnu make to $DEVKIT_ROOT/bin"
mkdir -p $DEVKIT_ROOT/bin
cp $GNU_MAKE $DEVKIT_ROOT/bin/
if [ ! -e $DEVKIT_ROOT/bin/gmake ]; then
  ln -s make $DEVKIT_ROOT/bin/gmake
fi

# Create the devkit.info file
echo Creating devkit.info
INFO_FILE=$DEVKIT_ROOT/devkit.info
rm -f $INFO_FILE
echo "# This file describes to configure how to interpret the contents of this devkit" >> $INFO_FILE
echo "DEVKIT_NAME=\"Solaris Studio $SOLARIS_STUDIO_VERSION - Solaris $SOLARIS_VERSION - $ARCH\"" >> $INFO_FILE
echo "DEVKIT_TOOLCHAIN_PATH=\"\$DEVKIT_ROOT/$SOLARIS_STUDIO_SUBDIR/bin:\$DEVKIT_ROOT/bin\"" >> $INFO_FILE
echo "DEVKIT_EXTRA_PATH=\"\$DEVKIT_ROOT/bin\"" >> $INFO_FILE
echo "DEVKIT_SYSROOT=\"\$DEVKIT_ROOT/sysroot\"" >> $INFO_FILE

if [ ! -e $BUNDLE ]; then
  echo "Creating $BUNDLE from $DEVKIT_ROOT"
  cd $DEVKIT_ROOT/..
  tar zcf $BUNDLE $DEVKIT_NAME
else
  echo "Skipping creation of $BUNDLE"
fi
