#!/bin/sh
# Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

# The output of this script may require some degree of human curation:
# - Redundant headers, e.g. both x.hpp, x.inline.hpp are included;
# - Headers relative to a non-default feature should be protected by an
#   appropriate 'if' clause to make sure all variants can build without 
#   errors.

# Time threshold for header compilation, if the time exceeds the
# threshold the header will be precompiled.
if [ -z "$MIN_MS" ]; then
  MIN_MS=100000
fi

if [ -z "$CLEAN" ]; then
  CLEAN=true
elif [ "$CLEAN" != "true" ] && [ "$CLEAN" != "false" ]; then
  echo "Expected either 'true' or 'false' for CLEAN"
fi

# CBA_PATH should point to a valid ClangBuildAnalyzer executable.
# Build steps:
# git clone --depth 1 git@github.com:aras-p/ClangBuildAnalyzer.git
# cd ClangBuildAnalyzer
# make -f projects/make/Makefile
if [ -z "$CBA_PATH" ]; then
  CBA_PATH="./ClangBuildAnalyzer/build/ClangBuildAnalyzer"
fi

set -eux

PRECOMPILED_HPP="src/hotspot/share/precompiled/precompiled.hpp"
CBA_CONFIG="ClangBuildAnalyzer.ini"
TIMESTAMP="$(date +%Y%m%d-%H%M)"
RUN_NAME="pch_update_$TIMESTAMP"
CBA_OUTPUT="cba_out_$TIMESTAMP"

if [ "$CLEAN" = "true" ]; then
  trap 'rm -rf "build/'"$RUN_NAME"'" "$CBA_OUTPUT" "$CBA_CONFIG"' EXIT
fi

sh configure --with-toolchain-type=clang \
             --with-conf-name="$RUN_NAME" \
             --disable-precompiled-headers \
             --with-extra-cxxflags="-ftime-trace" \
             --with-extra-cflags="-ftime-trace"

make clean CONF_NAME="$RUN_NAME"
make hotspot CONF_NAME="$RUN_NAME"
"$CBA_PATH" --all "./build/$RUN_NAME/hotspot/variant-server/libjvm/objs" \
  "$CBA_OUTPUT"

# Preserve license and comments on top
cat "$PRECOMPILED_HPP" | awk '/^#include/ {exit} {print}' > "$PRECOMPILED_HPP.tmp"

if [ ! -f "$CBA_CONFIG" ]; then
cat <<EOF > "$CBA_CONFIG"
[counts]
header=100
headerChain=0
template=0
function=0
fileCodegen=0
fileParse=0

[misc]
onlyRootHeaders=true
EOF
fi

"$CBA_PATH" --analyze "$CBA_OUTPUT" | \
  grep " ms: " | \
  # Keep the headers more expensive than ${1}ms
  awk -v x="$MIN_MS" '$1 < x { exit } { print $3 }' | \
  # Filter away non-hotspot headers
  grep hotspot/share | \
  awk -F "hotspot/share/" '{ printf "#include \"%s\"\n", $2 }' \
  >> "$PRECOMPILED_HPP.tmp"
mv "$PRECOMPILED_HPP.tmp" "$PRECOMPILED_HPP"

java test/hotspot/jtreg/sources/SortIncludes.java --update "$PRECOMPILED_HPP"
