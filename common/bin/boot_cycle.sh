#!/bin/bash
#
# Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

# The boot_cycle.sh script performs two complete image builds (no javadoc though....)
# where the second build uses the first build as the boot jdk.
#
# This is useful to verify that the build is self hoisting and assists
# in flushing out bugs. You can follow up with compare_objects.sh to check
# that the two boot_cycle_?/images/j2sdk are identical. They should be.
#
# Usage:
# Specify the configure arguments to boot_cycle.sh, for example:
#
#    sh common/bin/boot_cycle.sh --enable-debug --with-jvm-variants=server
#
# The same arguments will be used for both builds, except of course --with-boot-jdk
# that will be adjusted to boot_cycle_1 for the second build.

SCRIPT_DIR=`pwd`/`dirname $0`
ROOT_DIR=`(cd $SCRIPT_DIR/../.. ; pwd)`
BUILD_DIR=$ROOT_DIR/build
mkdir -p $BUILD_DIR
AUTOCONF_DIR=`(cd $SCRIPT_DIR/../autoconf ; pwd)`
BOOT_CYCLE_1_DIR=$BUILD_DIR/boot_cycle_1
BOOT_CYCLE_2_DIR=$BUILD_DIR/boot_cycle_2

# Create the boot cycle dirs in the build directory.
mkdir -p $BOOT_CYCLE_1_DIR
mkdir -p $BOOT_CYCLE_2_DIR

cd $BOOT_CYCLE_1_DIR
# Configure!
sh $AUTOCONF_DIR/configure "$@"
# Now build!
make images

if ! test -x $BOOT_CYCLE_1_DIR/images/j2sdk-image/bin/java ; then
    echo Failed to build the executable $BOOT_CYCLE_1_DIR/images/j2sdk-image/bin/java
    exit 1
fi

cd $BOOT_CYCLE_2_DIR
# Pickup the configure arguments, but drop any --with-boot-jdk=....
# and add the correct --with-boot-jdk=...boot_cycle_1... at the end.
ARGUMENTS="`cat $BOOT_CYCLE_1_DIR/configure-arguments|sed 's/--with-boot-jdk=[^ ]*//'` --with-boot-jdk=$BOOT_CYCLE_1_DIR/images/j2sdk-image"
# Configure using these adjusted arguments.
sh $AUTOCONF_DIR/configure $ARGUMENTS
# Now build!
make images

if ! test -x $BOOT_CYCLE_2_DIR/images/j2sdk-image/bin/java ; then
    echo Failed to build the final executable $BOOT_CYCLE_2_DIR/images/j2sdk-image/bin/java
    exit 1
fi


