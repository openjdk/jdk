#
# Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

###############################################################################
#
# Includes and boilerplate
#
###############################################################################


AC_PREREQ([2.61])
AC_INIT(openjdk, jdk8, build-dev@openjdk.java.net)

# Do not change or remove the following line, it is needed for consistency checks:
# DATE_WHEN_GENERATED: @DATE_WHEN_GENERATED@

AC_CONFIG_AUX_DIR([build-aux])
m4_include([build-aux/pkg.m4])

# Include these first...
m4_include([basics.m4])
m4_include([builddeps.m4])
# ... then the rest
m4_include([boot-jdk.m4])
m4_include([build-performance.m4])
m4_include([help.m4])
m4_include([jdk-options.m4])
m4_include([libraries.m4])
m4_include([platform.m4])
m4_include([source-dirs.m4])
m4_include([toolchain.m4])

# This line needs to be here, verbatim, after all includes.
# It is replaced with custom functionality when building
# custom sources.
AC_DEFUN_ONCE([CUSTOM_HOOK])

###############################################################################
#
# Initialization
#
###############################################################################

# Basic initialization that must happen first of all
BASIC_INIT

# Now we can determine OpenJDK build and target platforms. This is required to
# have early on.
PLATFORM_SETUP_OPENJDK_BUILD_AND_TARGET

# Continue setting up basic stuff.
BASIC_SETUP_PATHS
BASIC_SETUP_LOGGING

# Must be done before we can call HELP_MSG_MISSING_DEPENDENCY.
HELP_SETUP_DEPENDENCY_HELP

# Setup simple tools, that do not need have cross compilation support.
# Without these, we can't properly run the rest of the configure script.
BASIC_SETUP_TOOLS

# Check if pkg-config is available.
PKG_PROG_PKG_CONFIG

# After basic tools have been setup, we can check build os specific details.
PLATFORM_SETUP_OPENJDK_BUILD_OS_VERSION

# Setup builddeps, for automatic downloading of tools we need.
# This is needed before we can call BDEPS_CHECK_MODULE, which is done in
# boot-jdk setup, but we need to have basic tools setup first.
BDEPS_CONFIGURE_BUILDDEPS
BDEPS_SCAN_FOR_BUILDDEPS

###############################################################################
#
# Determine OpenJDK variants, options and version numbers.
#
###############################################################################

# We need build & target for this.
JDKOPT_SETUP_JDK_VARIANT
JDKOPT_SETUP_JVM_VARIANTS
JDKOPT_SETUP_DEBUG_LEVEL
JDKOPT_SETUP_JDK_OPTIONS
JDKOPT_SETUP_JDK_VERSION_NUMBERS

# To properly create a configuration name, we need to have the OpenJDK target
# and options (variants and debug level) parsed.
BASIC_SETUP_OUTPUT_DIR

###############################################################################
#
# Setup BootJDK, used to bootstrap the build.
#
###############################################################################

BOOTJDK_SETUP_BOOT_JDK
BOOTJDK_SETUP_BOOT_JDK_ARGUMENTS

###############################################################################
#
# Configure the sources to use. We can add or override individual directories.
#
###############################################################################

SRCDIRS_SETUP_TOPDIRS
SRCDIRS_SETUP_ALTERNATIVE_TOPDIRS
SRCDIRS_SETUP_OUTPUT_DIRS

###############################################################################
#
# Setup the toolchain (compilers etc), i.e. the tools that need to be
# cross-compilation aware.
#
###############################################################################

TOOLCHAIN_SETUP_SYSROOT_AND_OUT_OPTIONS
TOOLCHAIN_SETUP_VISUAL_STUDIO_ENV
# Locate the actual tools
TOOLCHAIN_SETUP_PATHS

# FIXME: Currently we must test this after paths but before flags. Fix!

# And we can test some aspects on the target using configure macros.
PLATFORM_SETUP_OPENJDK_TARGET_BITS
PLATFORM_SETUP_OPENJDK_TARGET_ENDIANNESS

# Configure flags for the tools
TOOLCHAIN_SETUP_COMPILER_FLAGS_FOR_LIBS
TOOLCHAIN_SETUP_COMPILER_FLAGS_FOR_OPTIMIZATION
TOOLCHAIN_SETUP_COMPILER_FLAGS_FOR_JDK

# After we have toolchain, we can compile the uncygdrive helper
BASIC_COMPILE_UNCYGDRIVE


# Setup debug symbols (need objcopy from the toolchain for that)
JDKOPT_SETUP_DEBUG_SYMBOLS

###############################################################################
#
# Check dependencies for external and internal libraries.
#
###############################################################################

LIB_SETUP_INIT
LIB_SETUP_X11
LIB_SETUP_CUPS
LIB_SETUP_FREETYPE
LIB_SETUP_ALSA
LIB_SETUP_MISC_LIBS
LIB_SETUP_STATIC_LINK_LIBSTDCPP

###############################################################################
#
# We need to do some final tweaking, when everything else is done.
#
###############################################################################

JDKOPT_SETUP_BUILD_TWEAKS

###############################################################################
#
# Configure parts of the build that only affect the build performance,
# not the result.
#
###############################################################################

BPERF_SETUP_BUILD_CORES
BPERF_SETUP_BUILD_MEMORY

# Setup smart javac (after cores and memory have been setup)
BPERF_SETUP_SMART_JAVAC

# Can the C/C++ compiler use precompiled headers?
BPERF_SETUP_PRECOMPILED_HEADERS

# Setup use of ccache, if available
BPERF_SETUP_CCACHE

###############################################################################
#
# And now the finish...
#
###############################################################################

# Check for some common pitfalls
BASIC_TEST_USABILITY_ISSUES

# At the end, call the custom hook. (Dummy macro if no custom sources available)
CUSTOM_HOOK

# We're messing a bit with internal autoconf variables to put the config.status
# in the output directory instead of the current directory.
CONFIG_STATUS="$OUTPUT_ROOT/config.status"
# Create the actual output files. Now the main work of configure is done.
AC_OUTPUT

# Make the compare script executable
$CHMOD +x $OUTPUT_ROOT/compare.sh

# Finally output some useful information to the user
HELP_PRINT_SUMMARY_AND_WARNINGS
