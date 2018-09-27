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

# This script creates a devkit for building OpenJDK on Solaris by copying
# part of a Solaris Studio installation and cretaing a sysroot by installing
# a limited set of system packages. It is assumed that a suitable pkg
# publisher is configured for the system where the script is executed.
#
# Note that you will need to be able to sudo to root to run the pkg install
# part of this script. It should not affect the running system, but only
# install in a separate temporary image.
#
# The Solaris Studio installation must contain at least these packages:
#developer/developerstudio-126/backend                12.6-1.0.0.1
#developer/developerstudio-126/c++                    12.6-1.0.2.0
#developer/developerstudio-126/cc                     12.6-1.0.1.0
#developer/developerstudio-126/dbx                    12.6-1.0.0.1
#developer/developerstudio-126/library/c++-libs       12.6-1.0.2.0
#developer/developerstudio-126/library/c-libs         12.6-1.0.0.1
#developer/developerstudio-126/library/f90-libs       12.6-1.0.0.1
#developer/developerstudio-126/library/math-libs      12.6-1.0.0.1
#developer/developerstudio-126/library/studio-gccrt   12.6-1.0.0.1
#developer/developerstudio-126/studio-common          12.6-1.0.0.1
#developer/developerstudio-126/studio-ja              12.6-1.0.0.1
#developer/developerstudio-126/studio-legal           12.6-1.0.0.1
#developer/developerstudio-126/studio-zhCN            12.6-1.0.0.1
#
# erik.joelsson@oracle.com

USAGE="$0 <Solaris Studio installation>"

if [ "$1" = "" ]; then
  echo $USAGE
  exit 1
fi

SOLARIS_STUDIO_VERSION=12u6
SOLARIS_VERSION=11u3
SOLARIS_ENTIRE_VERSION=0.5.11-0.175.3.20.0.6.0
case `uname -p` in
  i*)
    ARCH=x86
    ;;
  sparc*)
    ARCH=sparc
    ;;
esac

SOLARIS_STUDIO_SRC=$1

SCRIPT_DIR="$(cd "$(dirname $0)" > /dev/null && pwd)"
BUILD_DIR="${SCRIPT_DIR}/../../build/devkit"

DEVKIT_NAME=SS${SOLARIS_STUDIO_VERSION}-Solaris${SOLARIS_VERSION}
DEVKIT_ROOT=${BUILD_DIR}/${DEVKIT_NAME}
BUNDLE_NAME=${DEVKIT_NAME}.tar.gz
BUNDLE=${BUILD_DIR}/${BUNDLE_NAME}
INSTALL_ROOT=${BUILD_DIR}/install-root-$SOLARIS_VERSION
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
  sudo pkg -R $INSTALL_ROOT install --accept entire@$SOLARIS_ENTIRE_VERSION \
      system/install developer/gnu-binutils system/library/mmheap system/picl \
      developer/assembler system/library/freetype-2
else
  echo "Skipping installing packages"
fi

if [ ! -d $SYSROOT ]; then
  echo "Copying from $INSTALL_ROOT to $SYSROOT"
  mkdir -p $SYSROOT
  cp -rH $INSTALL_ROOT/lib $SYSROOT/
  mkdir $SYSROOT/usr
  cp -rH $INSTALL_ROOT/usr/lib $SYSROOT/usr/
  cp -rH $INSTALL_ROOT/usr/include $SYSROOT/usr/
  pkg -R $INSTALL_ROOT list > $SYSROOT/pkg-list.txt
else
  echo "Skipping copying to $SYSROOT"
fi

if [ ! -d $DEVKIT_ROOT/tools ]; then
  # Some of the tools in sysroot are needed in the OpenJDK build. We need
  # to copy them into a tools dir, including their specific libraries.
  mkdir -p $DEVKIT_ROOT/tools/usr/bin $DEVKIT_ROOT/tools/lib/sparcv9 \
      $DEVKIT_ROOT/tools/usr/gnu/bin $DEVKIT_ROOT/tools/usr/bin/sparcv9
  cp $INSTALL_ROOT/usr/bin/{as,ar,nm,strip,ld,pigz,ldd} \
       $DEVKIT_ROOT/tools/usr/bin/
  cp $INSTALL_ROOT/usr/sbin/dtrace $DEVKIT_ROOT/tools/usr/bin/
  cp $INSTALL_ROOT/usr/sbin/sparcv9/dtrace $DEVKIT_ROOT/tools/usr/bin/sparcv9/
  cp -rH $INSTALL_ROOT/usr/gnu/bin/* $DEVKIT_ROOT/tools/usr/gnu/bin/
  cp $INSTALL_ROOT/lib/sparcv9/{libelf.so*,libld.so*,liblddbg.so*} $DEVKIT_ROOT/tools/lib/sparcv9/
  for t in $(ls $DEVKIT_ROOT/tools/usr/gnu/bin); do
    if [ -f $DEVKIT_ROOT/tools/usr/gnu/bin/$t ]; then
      ln -s ../gnu/bin/$t $DEVKIT_ROOT/tools/usr/bin/g$t
    fi
  done
else
  echo "Skipping copying to tools dir $DEVKIT_ROOT/tools"
fi

if [ ! -d $SOLARIS_STUDIO_DIR ]; then
  echo "Copying Solaris Studio from $SOLARIS_STUDIO_SRC"
  mkdir -p ${SOLARIS_STUDIO_DIR}
  cp -rH $SOLARIS_STUDIO_SRC/. ${SOLARIS_STUDIO_DIR}/
  # Solaris Studio 12.6 requires libmmheap.so.1 and libsunmath.so.1 to run, but
  # these libs are not installed by default on all Solaris systems.
  # Sneak them in from the sysroot to make it run OOTB on more systems.
  cp $SYSROOT/lib/libsunmath.so.1 $SOLARIS_STUDIO_DIR/lib/compilers/sys/
  cp $SYSROOT/lib/sparcv9/libsunmath.so.1 $SOLARIS_STUDIO_DIR/lib/compilers/sys/sparcv9/
  cp $SYSROOT/lib/libmmheap.so.1 $SOLARIS_STUDIO_DIR/lib/compilers/sys/
  cp $SYSROOT/lib/sparcv9/libmmheap.so.1 $SOLARIS_STUDIO_DIR/lib/compilers/sys/sparcv9/
else
  echo "Skipping copying of Solaris Studio"
fi

# Create the devkit.info file
echo Creating devkit.info
INFO_FILE=$DEVKIT_ROOT/devkit.info
rm -f $INFO_FILE
echo "# This file describes to configure how to interpret the contents of this devkit" >> $INFO_FILE
echo "DEVKIT_NAME=\"Solaris Studio $SOLARIS_STUDIO_VERSION - Solaris $SOLARIS_VERSION - $ARCH\"" >> $INFO_FILE
echo "DEVKIT_TOOLCHAIN_PATH=\"\$DEVKIT_ROOT/$SOLARIS_STUDIO_SUBDIR/bin:\$DEVKIT_ROOT/bin\"" >> $INFO_FILE
echo "DEVKIT_EXTRA_PATH=\"\$DEVKIT_ROOT/tools/usr/bin\"" >> $INFO_FILE
echo "DEVKIT_SYSROOT=\"\$DEVKIT_ROOT/sysroot\"" >> $INFO_FILE

if [ ! -e $BUNDLE ]; then
  GZIP=$(command -v pigz)
  if [ -z "$GZIP" ]; then
    GZIP="gzip"
  fi
  echo "Creating $BUNDLE from $DEVKIT_ROOT"
  (cd $DEVKIT_ROOT && tar cf - . | $GZIP - > "$BUNDLE")
else
  echo "Skipping creation of $BUNDLE"
fi
