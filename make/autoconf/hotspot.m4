#
# Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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

# All valid JVM variants
VALID_JVM_VARIANTS="server client minimal core zero custom"

###############################################################################
# Check if the specified JVM variant should be built. To be used in shell if
# constructs, like this:
# if HOTSPOT_CHECK_JVM_VARIANT(server); then
#
# Only valid to use after HOTSPOT_SETUP_JVM_VARIANTS has setup variants.

# Definition kept in one line to allow inlining in if statements.
# Additional [] needed to keep m4 from mangling shell constructs.
AC_DEFUN([HOTSPOT_CHECK_JVM_VARIANT],
[ [ [[ " $JVM_VARIANTS " =~ " $1 " ]] ] ])

###############################################################################
# Check which variants of the JVM that we want to build. Available variants are:
#   server: normal interpreter, and a tiered C1/C2 compiler
#   client: normal interpreter, and C1 (no C2 compiler)
#   minimal: reduced form of client with optional features stripped out
#   core: normal interpreter only, no compiler
#   zero: C++ based interpreter only, no compiler
#   custom: baseline JVM with no default features
#
AC_DEFUN_ONCE([HOTSPOT_SETUP_JVM_VARIANTS],
[
  AC_ARG_WITH([jvm-variants], [AS_HELP_STRING([--with-jvm-variants],
      [JVM variants to build, separated by commas (server client minimal core
      zero custom) @<:@server@:>@])])

  if test "x$with_jvm_variants" = x; then
    with_jvm_variants="server"
  fi
  JVM_VARIANTS_OPT="$with_jvm_variants"

  # Has the user listed more than one variant?
  # Additional [] needed to keep m4 from mangling shell constructs.
  if [ [[ "$JVM_VARIANTS_OPT" =~ "," ]] ]; then
    BUILDING_MULTIPLE_JVM_VARIANTS=true
  else
    BUILDING_MULTIPLE_JVM_VARIANTS=false
  fi
  # Replace the commas with AND for use in the build directory name.
  JVM_VARIANTS_WITH_AND=`$ECHO "$JVM_VARIANTS_OPT" | $SED -e 's/,/AND/g'`

  AC_MSG_CHECKING([which variants of the JVM to build])
  # JVM_VARIANTS is a space-separated list.
  # Also use minimal, not minimal1 (which is kept for backwards compatibility).
  JVM_VARIANTS=`$ECHO $JVM_VARIANTS_OPT | $SED -e 's/,/ /g' -e 's/minimal1/minimal/'`
  AC_MSG_RESULT([$JVM_VARIANTS])

  # Check that the selected variants are valid
  UTIL_GET_NON_MATCHING_VALUES(INVALID_VARIANTS, $JVM_VARIANTS, \
      $VALID_JVM_VARIANTS)
  if test "x$INVALID_VARIANTS" != x; then
    AC_MSG_NOTICE([Unknown variant(s) specified: "$INVALID_VARIANTS"])
    AC_MSG_NOTICE([The available JVM variants are: "$VALID_JVM_VARIANTS"])
    AC_MSG_ERROR([Cannot continue])
  fi

  # All "special" variants share the same output directory ("server")
  VALID_MULTIPLE_JVM_VARIANTS="server client minimal"
  UTIL_GET_NON_MATCHING_VALUES(INVALID_MULTIPLE_VARIANTS, $JVM_VARIANTS, \
      $VALID_MULTIPLE_JVM_VARIANTS)
  if  test "x$INVALID_MULTIPLE_VARIANTS" != x && \
      test "x$BUILDING_MULTIPLE_JVM_VARIANTS" = xtrue; then
    AC_MSG_ERROR([You can only build multiple variants using these variants: '$VALID_MULTIPLE_JVM_VARIANTS'])
  fi

  # The "main" variant is the one used by other libs to link against during the
  # build.
  if test "x$BUILDING_MULTIPLE_JVM_VARIANTS" = "xtrue"; then
    MAIN_VARIANT_PRIO_ORDER="server client minimal"
    for variant in $MAIN_VARIANT_PRIO_ORDER; do
      if HOTSPOT_CHECK_JVM_VARIANT($variant); then
        JVM_VARIANT_MAIN="$variant"
        break
      fi
    done
  else
    JVM_VARIANT_MAIN="$JVM_VARIANTS"
  fi

  AC_SUBST(JVM_VARIANTS)
  AC_SUBST(VALID_JVM_VARIANTS)
  AC_SUBST(JVM_VARIANT_MAIN)
])

###############################################################################
# Check if gtest should be built
#
AC_DEFUN_ONCE([HOTSPOT_ENABLE_DISABLE_GTEST],
[
  AC_ARG_ENABLE([hotspot-gtest], [AS_HELP_STRING([--disable-hotspot-gtest],
      [Disables building of the Hotspot unit tests @<:@enabled@:>@])])

  GTEST_AVAILABLE=true

  AC_MSG_CHECKING([if Hotspot gtest test source is present])
  if test -e "${TOPDIR}/test/hotspot/gtest"; then
    AC_MSG_RESULT([yes])
  else
    AC_MSG_RESULT([no, cannot run gtest])
    GTEST_AVAILABLE=false
  fi

  # On solaris, we also must have the libstlport.so.1 library, setup in
  # LIB_SETUP_LIBRARIES.
  if test "x$OPENJDK_TARGET_OS" = "xsolaris"; then
    if test "x$STLPORT_LIB" = "x"; then
      GTEST_AVAILABLE=false
    fi
  fi

  AC_MSG_CHECKING([if Hotspot gtest unit tests should be built])
  if test "x$enable_hotspot_gtest" = "xyes"; then
    if test "x$GTEST_AVAILABLE" = "xtrue"; then
      AC_MSG_RESULT([yes, forced])
      BUILD_GTEST="true"
    else
      AC_MSG_ERROR([Cannot build gtest with missing dependencies])
    fi
  elif test "x$enable_hotspot_gtest" = "xno"; then
    AC_MSG_RESULT([no, forced])
    BUILD_GTEST="false"
  elif test "x$enable_hotspot_gtest" = "x"; then
    if test "x$GTEST_AVAILABLE" = "xtrue"; then
      AC_MSG_RESULT([yes])
      BUILD_GTEST="true"
    else
      AC_MSG_RESULT([no])
      BUILD_GTEST="false"
    fi
  else
    AC_MSG_ERROR([--enable-gtest must be either yes or no])
  fi

  AC_SUBST(BUILD_GTEST)
])

###############################################################################
# Misc hotspot setup that does not fit elsewhere.
#
AC_DEFUN_ONCE([HOTSPOT_SETUP_MISC],
[
  if HOTSPOT_CHECK_JVM_VARIANT(zero); then
    # zero behaves as a platform and rewrites these values. This is a bit weird.
    # But when building zero, we never build any other variants so it works.
    HOTSPOT_TARGET_CPU=zero
    HOTSPOT_TARGET_CPU_ARCH=zero
  fi

  # Override hotspot cpu definitions for ARM platforms
  if test "x$OPENJDK_TARGET_CPU" = xarm; then
    HOTSPOT_TARGET_CPU=arm_32
    HOTSPOT_TARGET_CPU_DEFINE="ARM32"
  fi

  # --with-cpu-port is no longer supported
  UTIL_DEPRECATED_ARG_WITH(with-cpu-port)
])
