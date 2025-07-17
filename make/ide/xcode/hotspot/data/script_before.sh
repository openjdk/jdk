#!/bin/bash
#
# Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
#

echo "running script_before.sh"

readonly JDK_BUILD_PATH="..";
readonly JAVAC_LOCATE_PATTERN="images/jdk/bin/javac";
readonly HOTSPOT_TOUCH_FILE="../../../src/hotspot/os/posix/jvm_posix.cpp";

echo ">>>>>>> Making a copy of JDK ...";

javac_file_array=( $(find ${JDK_BUILD_PATH} | grep ${JAVAC_LOCATE_PATTERN}) );
javac_file=${javac_file_array[0]};
if [ -z ${javac_file} ] ; then
{
  echo ">>>>>>>   ERROR: could not locate ${JAVAC_LOCATE_PATTERN} (did you remember to do \"make images\"?)";
  exit 1;
}
fi

jdk_build_path=$(dirname $(dirname ${javac_file}));
if [ ! -f "build/${JAVAC_LOCATE_PATTERN}" ] ; then
{
  echo ">>>>>>>   Copying jdk over...";
  rsync -a "${jdk_build_path}" "build/";
}
fi

# the following files will be supplied by the Xcode build
rm -rf "build/jdk/lib/server/libjvm.dylib";
rm -rf "build/jdk/lib/server/libjvm.dylib.dSYM";

echo ">>>>>>> DONE";

echo ">>>>>>> Touching ${HOTSPOT_TOUCH_FILE} to force HotspotVM rebuilt";
if [ ! -f ${HOTSPOT_TOUCH_FILE} ] ; then
{
    echo ">>>>>>>   Cannot find ${HOTSPOT_TOUCH_FILE}";
    exit 1;
}
fi
touch ${HOTSPOT_TOUCH_FILE};

echo ">>>>>>> DONE";

echo ">>>>>>> Xcode should be building the HotspotVM now...";
