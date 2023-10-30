#!/bin/bash
#
# Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

# This script generates a libffi bundle. On linux by building it from source
# using a devkit, which should match the devkit used to build the JDK.
#
# Set MAKE_ARGS to add parameters to make. Ex:
#
# $ MAKE_ARGS=-j32 bash createLibffiBundle.sh
#
# The script tries to behave well on multiple invocations, only performing steps
# not already done. To redo a step, manually delete the target files from that
# step.
#
# Note that the libtool and texinfo packages are needed to build libffi
# $ sudo apt install libtool texinfo

# Note that while the build system supports linking against libffi on Windows (x64),
# I couldn't get this script working with a Windows devkit, and instead had to manually create
# a libffi bundle for Windows. The steps I took were as follows:
#
# 1. run 'x64 Native Tools Command Prompt for VS 2022'. After that, cl.exe and link.exe should be on path
#
# 2. in the same shell, run `ucrt64` (this is one of the shell environments that comes with MSYS2).
#    This should carry over the environment set up by the VS dev prompt into the ucrt64 prompt.
#
# 3. then, in the libffi repo root folder:
#   3.a run `autogen.sh`
#   3.b run:
# ```
# bash configure \
#   CC="/path/to/libffi/msvcc.sh -m64" \
#   CXX="/path/to/libffi/msvcc.sh -m64" \
#   CPPFLAGS="-DFFI_BUILDING_DLL" \
#   --disable-docs \
#   --prefix=<install dest>
# ```
# (`<install dest>` can be whatever you like. That's what you point `--with-libffi` to).
#
# 4. run `make install`. This should create the `<install dest>` directory with the files:
#    `include/ffi.h`, `include/ffitarget.h`, `lib/libffi.dll`. It also creates a `lib/libffi.lib` file,
#    but it is of the wrong file type, `DLL` rather than `LIBRARY`.
#
# 5. Manually create a working `.lib` file (in the <install dest>/lib dir):
#   5.a use `dumpbin /exports libffi.dll` to get a list of exported symbols
#   5.b put them in a `libffi.def` file: `EXPORTS` on the first line, then a symbol on each line following
#   5.c run `lib /def:libffi.def /machine:x64 /out:libffi.lib` to create the right `.lib` file (`lib` is a visual studio tool)
#

LIBFFI_VERSION=3.4.2

BUNDLE_NAME=libffi-$LIBFFI_VERSION.tar.gz

SCRIPT_FILE="$(basename $0)"
SCRIPT_DIR="$(cd "$(dirname $0)" > /dev/null && pwd)"
OUTPUT_DIR="${SCRIPT_DIR}/../../build/libffi"
SRC_DIR="$OUTPUT_DIR/src"
DOWNLOAD_DIR="$OUTPUT_DIR/download"
INSTALL_DIR="$OUTPUT_DIR/install"
IMAGE_DIR="$OUTPUT_DIR/image"
OS_NAME=$(uname -s)

USAGE="$0 <devkit dir>"

if [ "$1" = "" ]; then
    echo $USAGE
    exit 1
fi
DEVKIT_DIR="$1"

# Download source distros
mkdir -p $DOWNLOAD_DIR
cd $DOWNLOAD_DIR
SOURCE_TAR=v$LIBFFI_VERSION.tar.gz
if [ ! -f $SOURCE_TAR ]; then
    wget https://github.com/libffi/libffi/archive/refs/tags/v$LIBFFI_VERSION.tar.gz
fi

# Unpack src
mkdir -p $SRC_DIR
cd $SRC_DIR
LIBFFI_DIRNAME=libffi-$LIBFFI_VERSION
LIBFFI_DIR=$SRC_DIR/$LIBFFI_DIRNAME
if [ ! -d $LIBFFI_DIRNAME ]; then
    echo "Unpacking $SOURCE_TAR"
    tar xf $DOWNLOAD_DIR/$SOURCE_TAR
fi

# Build
cd $LIBFFI_DIR
if [ ! -e $LIBFFI_DIR/configure ]; then
  bash ./autogen.sh
fi

case $OS_NAME in
  Linux)
    CC=$DEVKIT_DIR/bin/gcc
    CXX=$DEVKIT_DIR/bin/g++
    # For Linux/x86 it's under /lib/ instead of /lib64/
    LIB_FOLDER=lib64
    LIB_NAME=libffi.so*
    ;;
  Darwin)
    CC=$DEVKIT_DIR/Xcode/Contents/Developer/usr/bin/gcc
    CXX=$DEVKIT_DIR/Xcode/Contents/Developer/usr/bin/gcc
    LIB_FOLDER=lib
    LIB_NAME=libffi.*.dylib
    ;;
  *)
    echo " Unsupported OS: $OS_NAME"
    exit 1
    ;;
esac

# For Linux/x86, add --build=i686-pc-linux-gnu CFLAGS=-m32 CXXFLAGS=-m32 LDFLAGS=-m32
bash ./configure \
  --disable-docs \
  --prefix=$INSTALL_DIR \
  CC=$CC \
  CXX=$CXX

# Run with nice to keep system usable during build.
nice make $MAKE_ARGS install

mkdir -p $IMAGE_DIR
# Extract what we need into an image
if [ ! -e $IMAGE_DIR/lib/libffi.so ]; then
  echo "Copying ${LIB_NAME} to image"
  mkdir -p $IMAGE_DIR/lib
  cp -a $INSTALL_DIR/${LIB_FOLDER}/${LIB_NAME} $IMAGE_DIR/lib/
fi
if [ ! -e $IMAGE_DIR/include/ ]; then
  echo "Copying include to image"
  mkdir -p $IMAGE_DIR/include
  cp -a $INSTALL_DIR/include/. $IMAGE_DIR/include/
fi
if [ ! -e $IMAGE_DIR/$SCRIPT_FILE ]; then
  echo "Copying this script to image"
  cp -a $SCRIPT_DIR/$SCRIPT_FILE $IMAGE_DIR/
fi

# Create bundle
if [ ! -e $OUTPUT_DIR/$BUNDLE_NAME ]; then
  echo "Creating $OUTPUT_DIR/$BUNDLE_NAME"
  cd $IMAGE_DIR
  tar zcf $OUTPUT_DIR/$BUNDLE_NAME *
fi
