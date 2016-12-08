#
# Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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

# All valid JVM features, regardless of platform
VALID_JVM_FEATURES="compiler1 compiler2 zero shark minimal dtrace jvmti jvmci \
    fprof vm-structs jni-check services management all-gcs nmt cds static-build"

# All valid JVM variants
VALID_JVM_VARIANTS="server client minimal core zero zeroshark custom"

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
# Check if the specified JVM features are explicitly enabled. To be used in
# shell if constructs, like this:
# if HOTSPOT_CHECK_JVM_FEATURE(jvmti); then
#
# Only valid to use after HOTSPOT_SETUP_JVM_FEATURES has setup features.

# Definition kept in one line to allow inlining in if statements.
# Additional [] needed to keep m4 from mangling shell constructs.
AC_DEFUN([HOTSPOT_CHECK_JVM_FEATURE],
[ [ [[ " $JVM_FEATURES " =~ " $1 " ]] ] ])

###############################################################################
# Check which variants of the JVM that we want to build. Available variants are:
#   server: normal interpreter, and a tiered C1/C2 compiler
#   client: normal interpreter, and C1 (no C2 compiler)
#   minimal: reduced form of client with optional features stripped out
#   core: normal interpreter only, no compiler
#   zero: C++ based interpreter only, no compiler
#   zeroshark: C++ based interpreter, and a llvm-based compiler
#   custom: baseline JVM with no default features
#
AC_DEFUN_ONCE([HOTSPOT_SETUP_JVM_VARIANTS],
[
  AC_ARG_WITH([jvm-variants], [AS_HELP_STRING([--with-jvm-variants],
      [JVM variants (separated by commas) to build (server,client,minimal,core,zero,zeroshark,custom) @<:@server@:>@])])

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

  # grep filter function inspired by a comment to http://stackoverflow.com/a/1617326
  # Notice that the original variant failes on SLES 10 and 11
  NEEDLE=${VALID_JVM_VARIANTS// /$'\n'}
  STACK=${JVM_VARIANTS// /$'\n'}
  INVALID_VARIANTS=`$GREP -Fvx "${NEEDLE}" <<< "${STACK}"`
  if test "x$INVALID_VARIANTS" != x; then
    AC_MSG_NOTICE([Unknown variant(s) specified: $INVALID_VARIANTS])
    AC_MSG_ERROR([The available JVM variants are: $VALID_JVM_VARIANTS])
  fi

  # All "special" variants share the same output directory ("server")
  VALID_MULTIPLE_JVM_VARIANTS="server client minimal"
  NEEDLE=${VALID_MULTIPLE_JVM_VARIANTS// /$'\n'}
  STACK=${JVM_VARIANTS// /$'\n'}
  INVALID_MULTIPLE_VARIANTS=`$GREP -Fvx "${NEEDLE}" <<< "${STACK}"`
  if  test "x$INVALID_MULTIPLE_VARIANTS" != x && test "x$BUILDING_MULTIPLE_JVM_VARIANTS" = xtrue; then
    AC_MSG_ERROR([You cannot build multiple variants with anything else than $VALID_MULTIPLE_JVM_VARIANTS.])
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

  if HOTSPOT_CHECK_JVM_VARIANT(zero) || HOTSPOT_CHECK_JVM_VARIANT(zeroshark); then
    # zero behaves as a platform and rewrites these values. This is really weird. :(
    # We are guaranteed that we do not build any other variants when building zero.
    HOTSPOT_TARGET_CPU=zero
    HOTSPOT_TARGET_CPU_ARCH=zero
  fi
])

###############################################################################
# Check if dtrace should be enabled and has all prerequisites present.
#
AC_DEFUN_ONCE([HOTSPOT_SETUP_DTRACE],
[
  # Test for dtrace dependencies
  AC_ARG_ENABLE([dtrace], [AS_HELP_STRING([--enable-dtrace@<:@=yes/no/auto@:>@],
      [enable dtrace. Default is auto, where dtrace is enabled if all dependencies
      are present.])])

  DTRACE_DEP_MISSING=false

  AC_MSG_CHECKING([for dtrace tool])
  if test "x$DTRACE" != "x" && test -x "$DTRACE"; then
    AC_MSG_RESULT([$DTRACE])
  else
    AC_MSG_RESULT([not found, cannot build dtrace])
    DTRACE_DEP_MISSING=true
  fi

  AC_CHECK_HEADERS([sys/sdt.h], [DTRACE_HEADERS_OK=yes],[DTRACE_HEADERS_OK=no])
  if test "x$DTRACE_HEADERS_OK" != "xyes"; then
    DTRACE_DEP_MISSING=true
  fi

  AC_MSG_CHECKING([if dtrace should be built])
  if test "x$enable_dtrace" = "xyes"; then
    if test "x$DTRACE_DEP_MISSING" = "xtrue"; then
      AC_MSG_RESULT([no, missing dependencies])
      HELP_MSG_MISSING_DEPENDENCY([dtrace])
      AC_MSG_ERROR([Cannot enable dtrace with missing dependencies. See above. $HELP_MSG])
    else
      INCLUDE_DTRACE=true
      AC_MSG_RESULT([yes, forced])
    fi
  elif test "x$enable_dtrace" = "xno"; then
    INCLUDE_DTRACE=false
    AC_MSG_RESULT([no, forced])
  elif test "x$enable_dtrace" = "xauto" || test "x$enable_dtrace" = "x"; then
    if test "x$DTRACE_DEP_MISSING" = "xtrue"; then
      INCLUDE_DTRACE=false
      AC_MSG_RESULT([no, missing dependencies])
    else
      INCLUDE_DTRACE=true
      AC_MSG_RESULT([yes, dependencies present])
    fi
  else
    AC_MSG_ERROR([Invalid value for --enable-dtrace: $enable_dtrace])
  fi
  AC_SUBST(INCLUDE_DTRACE)
])

###############################################################################
# Set up all JVM features for each JVM variant.
#
AC_DEFUN_ONCE([HOTSPOT_SETUP_JVM_FEATURES],
[
  # The user can in some cases supply additional jvm features. For the custom
  # variant, this defines the entire variant.
  AC_ARG_WITH([jvm-features], [AS_HELP_STRING([--with-jvm-features],
      [additional JVM features to enable (separated by comma),  use '--help' to show possible values @<:@none@:>@])])
  if test "x$with_jvm_features" != x; then
    AC_MSG_CHECKING([additional JVM features])
    JVM_FEATURES=`$ECHO $with_jvm_features | $SED -e 's/,/ /g'`
    AC_MSG_RESULT([$JVM_FEATURES])
  fi

  # Verify that dependencies are met for explicitly set features.
  if HOTSPOT_CHECK_JVM_FEATURE(jvmti) && ! HOTSPOT_CHECK_JVM_FEATURE(services); then
    AC_MSG_ERROR([Specified JVM feature 'jvmti' requires feature 'services'])
  fi

  if HOTSPOT_CHECK_JVM_FEATURE(management) && ! HOTSPOT_CHECK_JVM_FEATURE(nmt); then
    AC_MSG_ERROR([Specified JVM feature 'management' requires feature 'nmt'])
  fi

  if HOTSPOT_CHECK_JVM_FEATURE(jvmci) && ! (HOTSPOT_CHECK_JVM_FEATURE(compiler1) || HOTSPOT_CHECK_JVM_FEATURE(compiler2)); then
    AC_MSG_ERROR([Specified JVM feature 'jvmci' requires feature 'compiler2' or 'compiler1'])
  fi

  if HOTSPOT_CHECK_JVM_FEATURE(compiler2) && ! HOTSPOT_CHECK_JVM_FEATURE(all-gcs); then
    AC_MSG_ERROR([Specified JVM feature 'compiler2' requires feature 'all-gcs'])
  fi

  if HOTSPOT_CHECK_JVM_FEATURE(vm-structs) && ! HOTSPOT_CHECK_JVM_FEATURE(all-gcs); then
    AC_MSG_ERROR([Specified JVM feature 'vm-structs' requires feature 'all-gcs'])
  fi

  # Turn on additional features based on other parts of configure
  if test "x$INCLUDE_DTRACE" = "xtrue"; then
    JVM_FEATURES="$JVM_FEATURES dtrace"
  else
    if HOTSPOT_CHECK_JVM_FEATURE(dtrace); then
      AC_MSG_ERROR([To enable dtrace, you must use --enable-dtrace])
    fi
  fi

  if test "x$STATIC_BUILD" = "xtrue"; then
    JVM_FEATURES="$JVM_FEATURES static-build"
  else
    if HOTSPOT_CHECK_JVM_FEATURE(static-build); then
      AC_MSG_ERROR([To enable static-build, you must use --enable-static-build])
    fi
  fi

  if ! HOTSPOT_CHECK_JVM_VARIANT(zero) && ! HOTSPOT_CHECK_JVM_VARIANT(zeroshark); then
    if HOTSPOT_CHECK_JVM_FEATURE(zero); then
      AC_MSG_ERROR([To enable zero/zeroshark, you must use --with-jvm-variants=zero/zeroshark])
    fi
  fi

  if ! HOTSPOT_CHECK_JVM_VARIANT(zeroshark); then
    if HOTSPOT_CHECK_JVM_FEATURE(shark); then
      AC_MSG_ERROR([To enable shark, you must use --with-jvm-variants=zeroshark])
    fi
  fi

  # Only enable jvmci on x86_64, sparcv9 and aarch64.
  if test "x$OPENJDK_TARGET_CPU" = "xx86_64" || \
      test "x$OPENJDK_TARGET_CPU" = "xsparcv9" || \
      test "x$OPENJDK_TARGET_CPU" = "xaarch64" ; then
    JVM_FEATURES_jvmci="jvmci"
  else
    JVM_FEATURES_jvmci=""
  fi

  # All variants but minimal (and custom) get these features
  NON_MINIMAL_FEATURES="$NON_MINIMAL_FEATURES jvmti fprof vm-structs jni-check services management all-gcs nmt cds"

  # Enable features depending on variant.
  JVM_FEATURES_server="compiler1 compiler2 $NON_MINIMAL_FEATURES $JVM_FEATURES $JVM_FEATURES_jvmci"
  JVM_FEATURES_client="compiler1 $NON_MINIMAL_FEATURES $JVM_FEATURES $JVM_FEATURES_jvmci"
  JVM_FEATURES_core="$NON_MINIMAL_FEATURES $JVM_FEATURES"
  JVM_FEATURES_minimal="compiler1 minimal $JVM_FEATURES"
  JVM_FEATURES_zero="zero $NON_MINIMAL_FEATURES $JVM_FEATURES"
  JVM_FEATURES_zeroshark="zero shark $NON_MINIMAL_FEATURES $JVM_FEATURES"
  JVM_FEATURES_custom="$JVM_FEATURES"

  AC_SUBST(JVM_FEATURES_server)
  AC_SUBST(JVM_FEATURES_client)
  AC_SUBST(JVM_FEATURES_core)
  AC_SUBST(JVM_FEATURES_minimal)
  AC_SUBST(JVM_FEATURES_zero)
  AC_SUBST(JVM_FEATURES_zeroshark)
  AC_SUBST(JVM_FEATURES_custom)

  # Used for verification of Makefiles by check-jvm-feature
  AC_SUBST(VALID_JVM_FEATURES)

  # We don't support --with-jvm-interpreter anymore, use zero instead.
  BASIC_DEPRECATED_ARG_WITH(jvm-interpreter)
])

###############################################################################
# Validate JVM features once all setup is complete, including custom setup.
#
AC_DEFUN_ONCE([HOTSPOT_VALIDATE_JVM_FEATURES],
[
  # Keep feature lists sorted and free of duplicates
  JVM_FEATURES_server="$($ECHO $($PRINTF '%s\n' $JVM_FEATURES_server | $SORT -u))"
  JVM_FEATURES_client="$($ECHO $($PRINTF '%s\n' $JVM_FEATURES_client | $SORT -u))"
  JVM_FEATURES_core="$($ECHO $($PRINTF '%s\n' $JVM_FEATURES_core | $SORT -u))"
  JVM_FEATURES_minimal="$($ECHO $($PRINTF '%s\n' $JVM_FEATURES_minimal | $SORT -u))"
  JVM_FEATURES_zero="$($ECHO $($PRINTF '%s\n' $JVM_FEATURES_zero | $SORT -u))"
  JVM_FEATURES_zeroshark="$($ECHO $($PRINTF '%s\n' $JVM_FEATURES_zeroshark | $SORT -u))"
  JVM_FEATURES_custom="$($ECHO $($PRINTF '%s\n' $JVM_FEATURES_custom | $SORT -u))"

  # Validate features
  for variant in $JVM_VARIANTS; do
    AC_MSG_CHECKING([JVM features for JVM variant '$variant'])
    features_var_name=JVM_FEATURES_$variant
    JVM_FEATURES_TO_TEST=${!features_var_name}
    AC_MSG_RESULT([$JVM_FEATURES_TO_TEST])
    NEEDLE=${VALID_JVM_FEATURES// /$'\n'}
    STACK=${JVM_FEATURES_TO_TEST// /$'\n'}
    INVALID_FEATURES=`$GREP -Fvx "${NEEDLE}" <<< "${STACK}"`
    if test "x$INVALID_FEATURES" != x; then
      AC_MSG_ERROR([Invalid JVM feature(s): $INVALID_FEATURES])
    fi
  done
])

################################################################################
# Check if gtest should be built
#
AC_DEFUN_ONCE([HOTSPOT_ENABLE_DISABLE_GTEST],
[
  AC_ARG_ENABLE([hotspot-gtest], [AS_HELP_STRING([--disable-hotspot-gtest],
      [Disables building of the Hotspot unit tests])])

  if test -e "$HOTSPOT_TOPDIR/test/native"; then
    GTEST_DIR_EXISTS="true"
  else
    GTEST_DIR_EXISTS="false"
  fi

  AC_MSG_CHECKING([if Hotspot gtest unit tests should be built])
  if test "x$enable_hotspot_gtest" = "xyes"; then
    if test "x$GTEST_DIR_EXISTS" = "xtrue"; then
      AC_MSG_RESULT([yes, forced])
      BUILD_GTEST="true"
    else
      AC_MSG_ERROR([Cannot build gtest without the test source])
    fi
  elif test "x$enable_hotspot_gtest" = "xno"; then
    AC_MSG_RESULT([no, forced])
    BUILD_GTEST="false"
  elif test "x$enable_hotspot_gtest" = "x"; then
    if test "x$GTEST_DIR_EXISTS" = "xtrue" && test "x$OPENJDK_TARGET_OS" != "xaix"; then
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
