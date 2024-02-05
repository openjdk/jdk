#!/bin/bash -e
#
# Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
# Create a bundle in the build directory, containing what's needed to
# build and run JMH microbenchmarks from the OpenJDK build.

JMH_VERSION=1.37
COMMONS_MATH3_VERSION=3.6.1
JOPT_SIMPLE_VERSION=5.0.4

BUNDLE_NAME=jmh-$JMH_VERSION.tar.gz

SCRIPT_DIR="$(cd "$(dirname $0)" > /dev/null && pwd)"
BUILD_DIR="${SCRIPT_DIR}/../../build/jmh"
JAR_DIR="$BUILD_DIR/jars"

mkdir -p $BUILD_DIR $JAR_DIR
cd $JAR_DIR
rm -f *

fetchJar() {
  url="https://repo.maven.apache.org/maven2/$1/$2/$3/$2-$3.jar"
  if command -v curl > /dev/null; then
      curl -O --fail $url
  elif command -v wget > /dev/null; then
      wget $url
  else
      echo "Could not find either curl or wget"
      exit 1
  fi
}

fetchJar org/apache/commons commons-math3 $COMMONS_MATH3_VERSION
fetchJar net/sf/jopt-simple jopt-simple $JOPT_SIMPLE_VERSION
fetchJar org/openjdk/jmh jmh-core $JMH_VERSION
fetchJar org/openjdk/jmh jmh-generator-annprocess $JMH_VERSION

tar -cvzf ../$BUNDLE_NAME *

echo "Created $BUILD_DIR/$BUNDLE_NAME"
