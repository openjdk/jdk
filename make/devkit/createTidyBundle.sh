#!/bin/bash
#
# Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

# Creates a tidy bundle in the build directory. A dependency that can be
# used to validate and correct HTML.

# wget, cmake and gcc are required to build tidy.

set -e

GITHUB_USER="htacg"
REPO_NAME="tidy-html5"
COMMIT_HASH="d08ddc2860aa95ba8e301343a30837f157977cba"
SCRIPT_DIR="$(cd "$(dirname $0)" > /dev/null && pwd)"
INSTALL_PREFIX="${SCRIPT_DIR}/../../build/tidy/tidy/"
BUILD_DIR="build/cmake"

OS_NAME=$(uname -s)
OS_ARCH=$(uname -m)

DOWNLOAD_URL="https://github.com/$GITHUB_USER/$REPO_NAME/archive/$COMMIT_HASH.tar.gz"
OUTPUT_FILE="$REPO_NAME-$COMMIT_HASH.tar.gz"

wget "$DOWNLOAD_URL" -O "$OUTPUT_FILE"

tar -xzf "$OUTPUT_FILE"
rm -rf "$OUTPUT_FILE"

SRC_DIR="$REPO_NAME-$COMMIT_HASH"

mkdir -p "$SRC_DIR/$BUILD_DIR"
cd "$SRC_DIR/$BUILD_DIR"

case $OS_NAME in
  Linux|Darwin)
    echo "Building Tidy HTML5 for Unix-like platform ($OS_NAME)..."

    CMAKE_ARCH_OPTIONS=""
    if [ "$OS_NAME" == "Darwin" ]; then
      if [[ "$OS_ARCH" == "arm64" || "$OS_ARCH" == "x86_64" ]]; then
        CMAKE_ARCH_OPTIONS="-DCMAKE_OSX_ARCHITECTURES=x86_64;arm64"
      fi
    fi

    cmake ../.. -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX="$INSTALL_PREFIX" $CMAKE_ARCH_OPTIONS
    make install
    ;;

  *)
    echo "Unsupported OS: $OS_NAME"
    exit 1
    ;;
esac

cd "$SCRIPT_DIR"
rm -rf "$SRC_DIR"

cd "$INSTALL_PREFIX.."
PACKAGED_FILE="tidy-html5.tar.gz"

tar -czvf "$PACKAGED_FILE" -C "$INSTALL_PREFIX.." tidy

echo "Created $INSTALL_PREFIX..$PACKAGED_FILE"
