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


AC_PREREQ([2.69])
AC_INIT(OpenJDK, jdk8, build-dev@openjdk.java.net,,http://openjdk.java.net)

AC_CONFIG_AUX_DIR([build-aux])
m4_include([build-aux/pkg.m4])

# Include these first...
m4_include([basics.m4])
m4_include([basics_windows.m4])
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
m4_include([toolchain_windows.m4])

AC_DEFUN_ONCE([CUSTOM_EARLY_HOOK])
AC_DEFUN_ONCE([CUSTOM_LATE_HOOK])

# This line needs to be here, verbatim, after all includes and the dummy hook
# definitions. It is replaced with custom functionality when building
# custom sources.
#CUSTOM_AUTOCONF_INCLUDE

# Do not change or remove the following line, it is needed for consistency checks:
DATE_WHEN_GENERATED=@DATE_WHEN_GENERATED@

###############################################################################
#
# Initialization / Boot-strapping
#
# The bootstrapping process needs to solve the "chicken or the egg" problem,
# thus it jumps back and forth, each time gaining something needed later on.
#
###############################################################################

# Basic initialization that must happen first of all
BASIC_INIT
BASIC_SETUP_FUNDAMENTAL_TOOLS

# Now we can determine OpenJDK build and target platforms. This is required to
# have early on.
PLATFORM_SETUP_OPENJDK_BUILD_AND_TARGET

# Continue setting up basic stuff. Most remaining code require fundamental tools.
BASIC_SETUP_PATHS
BASIC_SETUP_LOGGING

# Check if it's a pure open build or if custom sources are to be used.
JDKOPT_SETUP_OPEN_OR_CUSTOM

# These are needed to be able to create a configuration name (and thus the output directory)
JDKOPT_SETUP_JDK_VARIANT
JDKOPT_SETUP_JVM_INTERPRETER
JDKOPT_SETUP_JVM_VARIANTS
JDKOPT_SETUP_DEBUG_LEVEL

# With basic setup done, call the custom early hook.
CUSTOM_EARLY_HOOK

# To properly create a configuration name, we need to have the OpenJDK target
# and options (variants and debug level) parsed.
BASIC_SETUP_OUTPUT_DIR

# Must be done before we can call HELP_MSG_MISSING_DEPENDENCY.
HELP_SETUP_DEPENDENCY_HELP

# Setup tools that requires more complex handling, or that is not needed by the configure script.
BASIC_SETUP_COMPLEX_TOOLS

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
JDKOPT_SETUP_JDK_OPTIONS
JDKOPT_SETUP_JDK_VERSION_NUMBERS

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
TOOLCHAIN_SETUP_COMPILER_FLAGS_MISC

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

# After we have toolchain and the paths to all libraries (needed by msys), we can compile the fixpath helper
BASIC_COMPILE_FIXPATH

###############################################################################
#
# We need to do some final tweaking, when everything else is done.
#
###############################################################################

JDKOPT_SETUP_BUILD_TWEAKS
JDKOPT_DETECT_INTREE_EC

###############################################################################
#
# Configure parts of the build that only affect the build performance,
# not the result.
#
###############################################################################

BPERF_SETUP_BUILD_CORES
BPERF_SETUP_BUILD_MEMORY
BPERF_SETUP_BUILD_JOBS

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
CUSTOM_LATE_HOOK

# We're messing a bit with internal autoconf variables to put the config.status
# in the output directory instead of the current directory.
CONFIG_STATUS="$OUTPUT_ROOT/config.status"
# Create the actual output files. Now the main work of configure is done.
AC_OUTPUT

# Make the compare script executable
$CHMOD +x $OUTPUT_ROOT/compare.sh

# Finally output some useful information to the user
HELP_PRINT_SUMMARY_AND_WARNINGS
