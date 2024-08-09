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

# This script cross-compiles SLEEF for a non-native platform. For example,
# it can be used to build the aarch64 inline headers on an x64 machine by
# doing something like:
#
#   1. cd <sleef>
#   2. bash <jdk>/make/devkit/createSleef.sh aarch64-gcc.cmake <path-to>/devkit
#
# The second argument (the devkit path) should point to the devkit
# directory in which devkit.info resides.
#
# After the build completes, the build result is available in the
# <jdk>/build/sleef/build/cross directory. For example, the aarch64
# header files can be found in:
#
#   <jdk>/build/sleef/cross/include/sleefinline_{advsimd,sve}.h
#

SCRIPT_DIR="$(cd "$(dirname $0)" > /dev/null && pwd)"
BUILD_DIR="${SCRIPT_DIR}/../../build/sleef"

set -eu

if [ ! $# = 2 ]; then
    echo "Usage: $0 <cmake toolchain file> <devkit>"
    exit 1
fi

CMAKE_TOOLCHAIN_FILE="$1"
DEVKIT_ROOT="$2"

if [ ! -f "${CMAKE_TOOLCHAIN_FILE}" ]; then
    echo "Failed to locate ${CMAKE_TOOLCHAIN_FILE}"
    exit 1
fi

DEVKIT_INFO="${DEVKIT_ROOT}/devkit.info"
if [ ! -f "${DEVKIT_INFO}" ]; then
    echo "Failed to locate ${DEVKIT_INFO}"
    exit 1
fi

. "${DEVKIT_INFO}"

export PATH="${PATH}:${DEVKIT_EXTRA_PATH}"

BUILD_NATIVE_DIR="${BUILD_DIR}/native"
BUILD_CROSS_DIR="${BUILD_DIR}/cross"

mkdir -p "${BUILD_NATIVE_DIR}"
cmake -S . -B "${BUILD_NATIVE_DIR}"
cmake --build "${BUILD_NATIVE_DIR}" -j

mkdir -p "${BUILD_CROSS_DIR}"
cmake -S . -B "${BUILD_CROSS_DIR}" \
      -DCMAKE_TOOLCHAIN_FILE="${CMAKE_TOOLCHAIN_FILE}" \
      -DNATIVE_BUILD_DIR="${BUILD_NATIVE_DIR}" \
      -DSLEEF_BUILD_INLINE_HEADERS=TRUE
cmake --build "${BUILD_CROSS_DIR}" -j
