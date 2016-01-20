#
# Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
# Check which variant of the JDK that we want to build.
# Currently we have:
#    normal:   standard edition
# but the custom make system may add other variants
#
# Effectively the JDK variant gives a name to a specific set of
# modules to compile into the JDK.
AC_DEFUN_ONCE([JDKOPT_SETUP_JDK_VARIANT],
[
  AC_MSG_CHECKING([which variant of the JDK to build])
  AC_ARG_WITH([jdk-variant], [AS_HELP_STRING([--with-jdk-variant],
      [JDK variant to build (normal) @<:@normal@:>@])])

  if test "x$with_jdk_variant" = xnormal || test "x$with_jdk_variant" = x; then
    JDK_VARIANT="normal"
  else
    AC_MSG_ERROR([The available JDK variants are: normal])
  fi

  AC_SUBST(JDK_VARIANT)

  AC_MSG_RESULT([$JDK_VARIANT])
])

###############################################################################
# Set the debug level
#    release: no debug information, all optimizations, no asserts.
#    optimized: no debug information, all optimizations, no asserts, HotSpot target is 'optimized'.
#    fastdebug: debug information (-g), all optimizations, all asserts
#    slowdebug: debug information (-g), no optimizations, all asserts
AC_DEFUN_ONCE([JDKOPT_SETUP_DEBUG_LEVEL],
[
  DEBUG_LEVEL="release"
  AC_MSG_CHECKING([which debug level to use])
  AC_ARG_ENABLE([debug], [AS_HELP_STRING([--enable-debug],
      [set the debug level to fastdebug (shorthand for --with-debug-level=fastdebug) @<:@disabled@:>@])],
      [
        ENABLE_DEBUG="${enableval}"
        DEBUG_LEVEL="fastdebug"
      ], [ENABLE_DEBUG="no"])

  AC_ARG_WITH([debug-level], [AS_HELP_STRING([--with-debug-level],
      [set the debug level (release, fastdebug, slowdebug, optimized (HotSpot build only)) @<:@release@:>@])],
      [
        DEBUG_LEVEL="${withval}"
        if test "x$ENABLE_DEBUG" = xyes; then
          AC_MSG_ERROR([You cannot use both --enable-debug and --with-debug-level at the same time.])
        fi
      ])
  AC_MSG_RESULT([$DEBUG_LEVEL])

  if test "x$DEBUG_LEVEL" != xrelease && \
      test "x$DEBUG_LEVEL" != xoptimized && \
      test "x$DEBUG_LEVEL" != xfastdebug && \
      test "x$DEBUG_LEVEL" != xslowdebug; then
    AC_MSG_ERROR([Allowed debug levels are: release, fastdebug and slowdebug])
  fi
])

###############################################################################
#
# Should we build only OpenJDK even if closed sources are present?
#
AC_DEFUN_ONCE([JDKOPT_SETUP_OPEN_OR_CUSTOM],
[
  AC_ARG_ENABLE([openjdk-only], [AS_HELP_STRING([--enable-openjdk-only],
      [suppress building custom source even if present @<:@disabled@:>@])],,[enable_openjdk_only="no"])

  AC_MSG_CHECKING([for presence of closed sources])
  if test -d "$SRC_ROOT/jdk/src/closed"; then
    CLOSED_SOURCE_PRESENT=yes
  else
    CLOSED_SOURCE_PRESENT=no
  fi
  AC_MSG_RESULT([$CLOSED_SOURCE_PRESENT])

  AC_MSG_CHECKING([if closed source is suppressed (openjdk-only)])
  SUPPRESS_CLOSED_SOURCE="$enable_openjdk_only"
  AC_MSG_RESULT([$SUPPRESS_CLOSED_SOURCE])

  if test "x$CLOSED_SOURCE_PRESENT" = xno; then
    OPENJDK=true
    if test "x$SUPPRESS_CLOSED_SOURCE" = "xyes"; then
      AC_MSG_WARN([No closed source present, --enable-openjdk-only makes no sense])
    fi
  else
    if test "x$SUPPRESS_CLOSED_SOURCE" = "xyes"; then
      OPENJDK=true
    else
      OPENJDK=false
    fi
  fi

  if test "x$OPENJDK" = "xtrue"; then
    SET_OPENJDK="OPENJDK=true"
  fi

  AC_SUBST(SET_OPENJDK)

  # custom-make-dir is deprecated. Please use your custom-hook.m4 to override
  # the IncludeCustomExtension macro.
  BASIC_DEPRECATED_ARG_WITH(custom-make-dir)
])

AC_DEFUN_ONCE([JDKOPT_SETUP_JDK_OPTIONS],
[
  # Should we build a JDK/JVM with headful support (ie a graphical ui)?
  # We always build headless support.
  AC_MSG_CHECKING([headful support])
  AC_ARG_ENABLE([headful], [AS_HELP_STRING([--disable-headful],
      [disable building headful support (graphical UI support) @<:@enabled@:>@])],
      [SUPPORT_HEADFUL=${enable_headful}], [SUPPORT_HEADFUL=yes])

  SUPPORT_HEADLESS=yes
  BUILD_HEADLESS="BUILD_HEADLESS:=true"

  if test "x$SUPPORT_HEADFUL" = xyes; then
    # We are building both headful and headless.
    headful_msg="include support for both headful and headless"
  fi

  if test "x$SUPPORT_HEADFUL" = xno; then
    # Thus we are building headless only.
    BUILD_HEADLESS="BUILD_HEADLESS:=true"
    headful_msg="headless only"
  fi

  AC_MSG_RESULT([$headful_msg])

  AC_SUBST(SUPPORT_HEADLESS)
  AC_SUBST(SUPPORT_HEADFUL)
  AC_SUBST(BUILD_HEADLESS)

  # Choose cacerts source file
  AC_ARG_WITH(cacerts-file, [AS_HELP_STRING([--with-cacerts-file],
      [specify alternative cacerts file])])
  if test "x$with_cacerts_file" != x; then
    CACERTS_FILE=$with_cacerts_file
  fi
  AC_SUBST(CACERTS_FILE)

  # Enable or disable unlimited crypto
  AC_ARG_ENABLE(unlimited-crypto, [AS_HELP_STRING([--enable-unlimited-crypto],
      [Enable unlimited crypto policy @<:@disabled@:>@])],,
      [enable_unlimited_crypto=no])
  if test "x$enable_unlimited_crypto" = "xyes"; then
    UNLIMITED_CRYPTO=true
  else
    UNLIMITED_CRYPTO=false
  fi
  AC_SUBST(UNLIMITED_CRYPTO)

  # Compress jars
  COMPRESS_JARS=false

  AC_SUBST(COMPRESS_JARS)

  # Setup default copyright year. Mostly overridden when building close to a new year.
  AC_ARG_WITH(copyright-year, [AS_HELP_STRING([--with-copyright-year],
      [Set copyright year value for build @<:@current year@:>@])])
  if test "x$with_copyright_year" = xyes; then
    AC_MSG_ERROR([Copyright year must have a value])
  elif test "x$with_copyright_year" != x; then
    COPYRIGHT_YEAR="$with_copyright_year"
  else
    COPYRIGHT_YEAR=`date +'%Y'`
  fi
  AC_SUBST(COPYRIGHT_YEAR)
])

###############################################################################
#
# Enable or disable the elliptic curve crypto implementation
#
AC_DEFUN_ONCE([JDKOPT_DETECT_INTREE_EC],
[
  AC_MSG_CHECKING([if elliptic curve crypto implementation is present])

  if test -d "${SRC_ROOT}/jdk/src/jdk.crypto.ec/share/native/libsunec/impl"; then
    ENABLE_INTREE_EC=yes
    AC_MSG_RESULT([yes])
  else
    ENABLE_INTREE_EC=no
    AC_MSG_RESULT([no])
  fi

  AC_SUBST(ENABLE_INTREE_EC)
])

AC_DEFUN_ONCE([JDKOPT_SETUP_DEBUG_SYMBOLS],
[
  #
  # NATIVE_DEBUG_SYMBOLS
  # This must be done after the toolchain is setup, since we're looking at objcopy.
  #
  AC_MSG_CHECKING([what type of native debug symbols to use])
  AC_ARG_WITH([native-debug-symbols],
      [AS_HELP_STRING([--with-native-debug-symbols],
      [set the native debug symbol configuration (none, internal, external, zipped) @<:@zipped@:>@])],
      [
        if test "x$OPENJDK_TARGET_OS" = xaix; then
          if test "x$withval" = xexternal || test "x$withval" = xzipped; then
            AC_MSG_ERROR([AIX only supports the parameters 'none' and 'internal' for --with-native-debug-symbols])
          fi
        fi
      ],
      [
        if test "x$OPENJDK_TARGET_OS" = xaix; then
          # AIX doesn't support 'zipped' so use 'internal' as default
          with_native_debug_symbols="internal"
        else
          with_native_debug_symbols="zipped"
        fi
      ])
  NATIVE_DEBUG_SYMBOLS=$with_native_debug_symbols
  AC_MSG_RESULT([$NATIVE_DEBUG_SYMBOLS])

  if test "x$NATIVE_DEBUG_SYMBOLS" = xzipped; then

    if test "x$OPENJDK_TARGET_OS" = xsolaris || test "x$OPENJDK_TARGET_OS" = xlinux; then
      if test "x$OBJCOPY" = x; then
        # enabling of enable-debug-symbols and can't find objcopy
        # this is an error
        AC_MSG_ERROR([Unable to find objcopy, cannot enable native debug symbols])
      fi
    fi

    COMPILE_WITH_DEBUG_SYMBOLS=true
    COPY_DEBUG_SYMBOLS=true
    ZIP_EXTERNAL_DEBUG_SYMBOLS=true

    # Hotspot legacy support, not relevant with COPY_DEBUG_SYMBOLS=true
    DEBUG_BINARIES=false
    STRIP_POLICY=min_strip
    
  elif test "x$NATIVE_DEBUG_SYMBOLS" = xnone; then
    COMPILE_WITH_DEBUG_SYMBOLS=false
    COPY_DEBUG_SYMBOLS=false
    ZIP_EXTERNAL_DEBUG_SYMBOLS=false

    DEBUG_BINARIES=false
    STRIP_POLICY=no_strip
  elif test "x$NATIVE_DEBUG_SYMBOLS" = xinternal; then
    COMPILE_WITH_DEBUG_SYMBOLS=true
    COPY_DEBUG_SYMBOLS=false
    ZIP_EXTERNAL_DEBUG_SYMBOLS=false

    # Hotspot legacy support, will turn on -g when COPY_DEBUG_SYMBOLS=false
    DEBUG_BINARIES=true
    STRIP_POLICY=no_strip
    STRIP=""
    
  elif test "x$NATIVE_DEBUG_SYMBOLS" = xexternal; then

    if test "x$OPENJDK_TARGET_OS" = xsolaris || test "x$OPENJDK_TARGET_OS" = xlinux; then
      if test "x$OBJCOPY" = x; then
        # enabling of enable-debug-symbols and can't find objcopy
        # this is an error
        AC_MSG_ERROR([Unable to find objcopy, cannot enable native debug symbols])
      fi
    fi

    COMPILE_WITH_DEBUG_SYMBOLS=true
    COPY_DEBUG_SYMBOLS=true
    ZIP_EXTERNAL_DEBUG_SYMBOLS=false

    # Hotspot legacy support, not relevant with COPY_DEBUG_SYMBOLS=true
    DEBUG_BINARIES=false
    STRIP_POLICY=min_strip
  else
    AC_MSG_ERROR([Allowed native debug symbols are: none, internal, external, zipped])
  fi

  # --enable-debug-symbols is deprecated.
  # Please use --with-native-debug-symbols=[internal,external,zipped] .
  BASIC_DEPRECATED_ARG_ENABLE(debug-symbols, debug_symbols,
        [Please use --with-native-debug-symbols=[[internal,external,zipped]] .])

  # --enable-zip-debug-info is deprecated.
  # Please use --with-native-debug-symbols=zipped .
  BASIC_DEPRECATED_ARG_ENABLE(zip-debug-info, zip_debug_info,
                              [Please use --with-native-debug-symbols=zipped .])

  AC_SUBST(COMPILE_WITH_DEBUG_SYMBOLS)
  AC_SUBST(COPY_DEBUG_SYMBOLS)
  AC_SUBST(ZIP_EXTERNAL_DEBUG_SYMBOLS)

  # Legacy values
  AC_SUBST(DEBUG_BINARIES)
  AC_SUBST(STRIP_POLICY)
])

################################################################################
#
# Gcov coverage data for hotspot
#
AC_DEFUN_ONCE([JDKOPT_SETUP_CODE_COVERAGE],
[
  AC_ARG_ENABLE(native-coverage, [AS_HELP_STRING([--enable-native-coverage],
      [enable native compilation with code coverage data@<:@disabled@:>@])])
  GCOV_ENABLED="false"
  if test "x$enable_native_coverage" = "xyes"; then
    if test "x$TOOLCHAIN_TYPE" = "xgcc"; then
      AC_MSG_CHECKING([if native coverage is enabled])
      AC_MSG_RESULT([yes])
      GCOV_CFLAGS="-fprofile-arcs -ftest-coverage -fno-inline"
      GCOV_LDFLAGS="-fprofile-arcs"
      LEGACY_EXTRA_CFLAGS="$LEGACY_EXTRA_CFLAGS $GCOV_CFLAGS"
      LEGACY_EXTRA_CXXFLAGS="$LEGACY_EXTRA_CXXFLAGS $GCOV_CFLAGS"
      LEGACY_EXTRA_LDFLAGS="$LEGACY_EXTRA_LDFLAGS $GCOV_LDFLAGS"
      CFLAGS_JDKLIB="$CFLAGS_JDKLIB $GCOV_CFLAGS"
      CFLAGS_JDKEXE="$CFLAGS_JDKEXE $GCOV_CFLAGS"
      CXXFLAGS_JDKLIB="$CXXFLAGS_JDKLIB $GCOV_CFLAGS"
      CXXFLAGS_JDKEXE="$CXXFLAGS_JDKEXE $GCOV_CFLAGS"
      LDFLAGS_JDKLIB="$LDFLAGS_JDKLIB $GCOV_LDFLAGS"
      LDFLAGS_JDKEXE="$LDFLAGS_JDKEXE $GCOV_LDFLAGS"
      GCOV_ENABLED="true"
    else
      AC_MSG_ERROR([--enable-native-coverage only works with toolchain type gcc])
    fi
  elif test "x$enable_native_coverage" = "xno"; then
    AC_MSG_CHECKING([if native coverage is enabled])
    AC_MSG_RESULT([no])
  elif test "x$enable_native_coverage" != "x"; then
    AC_MSG_ERROR([--enable-native-coverage can only be assigned "yes" or "no"])
  fi

  AC_SUBST(GCOV_ENABLED)
])

################################################################################
#
# Static build support.  When enabled will generate static 
# libraries instead of shared libraries for all JDK libs.
#
AC_DEFUN_ONCE([JDKOPT_SETUP_STATIC_BUILD],
[
  AC_ARG_ENABLE([static-build], [AS_HELP_STRING([--enable-static-build],
    [enable static library build @<:@disabled@:>@])])
  STATIC_BUILD=false
  if test "x$enable_static_build" = "xyes"; then
    AC_MSG_CHECKING([if static build is enabled])
    AC_MSG_RESULT([yes])
    if test "x$OPENJDK_TARGET_OS" != "xmacosx"; then
      AC_MSG_ERROR([--enable-static-build is only supported for macosx builds])
    fi
    STATIC_BUILD_CFLAGS="-DSTATIC_BUILD=1"
    LEGACY_EXTRA_CFLAGS="$LEGACY_EXTRA_CFLAGS $STATIC_BUILD_CFLAGS"
    LEGACY_EXTRA_CXXFLAGS="$LEGACY_EXTRA_CXXFLAGS $STATIC_BUILD_CFLAGS"
    CFLAGS_JDKLIB_EXTRA="$CFLAGS_JDKLIB_EXTRA $STATIC_BUILD_CFLAGS"
    CXXFLAGS_JDKLIB_EXTRA="$CXXFLAGS_JDKLIB_EXTRA $STATIC_BUILD_CFLAGS"
    STATIC_BUILD=true
  elif test "x$enable_static_build" = "xno"; then
    AC_MSG_CHECKING([if static build is enabled])
    AC_MSG_RESULT([no])
  elif test "x$enable_static_build" != "x"; then
    AC_MSG_ERROR([--enable-static-build can only be assigned "yes" or "no"])
  fi

  AC_SUBST(STATIC_BUILD)
])
