#
# Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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

################################################################################
# Check if the specified JVM variant should be built. To be used in shell if
# constructs, like this:
# if HOTSPOT_CHECK_JVM_VARIANT(server); then
#
# Only valid to use after HOTSPOT_SETUP_JVM_VARIANTS has setup variants.

# Definition kept in one line to allow inlining in if statements.
# Additional [] needed to keep m4 from mangling shell constructs.
AC_DEFUN([HOTSPOT_CHECK_JVM_VARIANT],
[ [ [[ " $JVM_VARIANTS " =~ " $1 " ]] ] ])

################################################################################
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

  # The "main" variant is the one used by other libs to link against during the
  # build.
  if test "x$BUILDING_MULTIPLE_JVM_VARIANTS" = "xtrue"; then
    MAIN_VARIANT_PRIO_ORDER="server client minimal zero"
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

################################################################################
# Setup an optional list of additional JVMs that should be included during
# build.
#
AC_DEFUN_ONCE([HOTSPOT_SETUP_IMPORT_JVMS],
[
  UTIL_ARG_WITH(NAME: import-jvms, TYPE: string,
      DEFAULT: [],
      DESC: [Additional JVMs to import, on the format <name>:<path to jvm lib>, separated by commas],
      OPTIONAL: true)

  IMPORT_JVMS=
  IMPORT_JVM_NAMES=
  AC_MSG_CHECKING([for import JVMs])
  # Create a nicely formatted list of import JVM names
  [ import_jvm_names="`$ECHO $with_import_jvms | $SED -E -e 's/([^:,]+):[^,]+/\1/g; s/,/, /g'`" ]
  if test "x$import_jvm_names" = x; then
    AC_MSG_RESULT([none])
  else
    AC_MSG_RESULT([$import_jvm_names])

    import_jvms="`$ECHO $with_import_jvms | $SED -e 's/,/ /g'`"

    for import_jvm in $import_jvms; do
      import_jvm_name=`$ECHO $import_jvm | $CUT -d: -f1`
      import_jvm_lib=`$ECHO $import_jvm | $CUT -d: -f2`
      if test "x$import_jvm_name" = "x$JVM_VARIANT_MAIN"; then
        AC_MSG_ERROR([JVM name '$JVM_VARIANT_MAIN' is not allowed for an import JVM])
      fi
      if ! test -e $import_jvm_lib; then
        AC_MSG_ERROR([Import JVM '$import_jvm_name' not found at $import_jvm_lib])
      fi

      UTIL_FIXUP_PATH(import_jvm_lib)
      AC_MSG_NOTICE([Importing JVM '$import_jvm_name' from $import_jvm_lib])
      IMPORT_JVM_NAMES="$IMPORT_JVM_NAMES $import_jvm_name"
      IMPORT_JVMS="$IMPORT_JVMS $import_jvm_name:$import_jvm_lib"
    done
  fi
  AC_SUBST(IMPORT_JVMS)
  AC_SUBST(IMPORT_JVM_NAMES)
])

################################################################################
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


  AC_ARG_WITH([hotspot-build-time], [AS_HELP_STRING([--with-hotspot-build-time],
  [timestamp to use in hotspot version string, empty means determined at build time @<:@source-date/empty@:>@])])

  AC_MSG_CHECKING([what hotspot build time to use])

  if test "x$with_hotspot_build_time" != x; then
    HOTSPOT_BUILD_TIME="$with_hotspot_build_time"
    AC_MSG_RESULT([$HOTSPOT_BUILD_TIME (from --with-hotspot-build-time)])
  else
    if test "x$SOURCE_DATE" = xupdated; then
      HOTSPOT_BUILD_TIME=""
      AC_MSG_RESULT([determined at build time (default)])
    else
      # If we have a fixed value for SOURCE_DATE, use it as default
      HOTSPOT_BUILD_TIME="$SOURCE_DATE_ISO_8601"
      AC_MSG_RESULT([$HOTSPOT_BUILD_TIME (from --with-source-date)])
    fi
  fi

  AC_SUBST(HOTSPOT_BUILD_TIME)


  # Override hotspot cpu definitions for ARM platforms
  if test "x$OPENJDK_TARGET_CPU" = xarm; then
    HOTSPOT_TARGET_CPU=arm_32
    HOTSPOT_TARGET_CPU_DEFINE="ARM32"
  fi
])
