#
# Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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
# Set the debug level
#    release: no debug information, all optimizations, no asserts.
#    optimized: no debug information, all optimizations, no asserts, HotSpot target is 'optimized'.
#    fastdebug: debug information (-g), all optimizations, all asserts
#    slowdebug: debug information (-g), no optimizations, all asserts
AC_DEFUN_ONCE([JDKOPT_SETUP_DEBUG_LEVEL],
[
  DEBUG_LEVEL="release"

  UTIL_ARG_ENABLE(NAME: debug, DEFAULT: false, RESULT: ENABLE_DEBUG,
      DESC: [enable debugging (shorthand for --with-debug-level=fastdebug)],
      IF_ENABLED: [ DEBUG_LEVEL="fastdebug" ])

  AC_MSG_CHECKING([which debug level to use])
  AC_ARG_WITH([debug-level], [AS_HELP_STRING([--with-debug-level],
      [set the debug level (release, fastdebug, slowdebug, optimized) @<:@release@:>@])],
      [
        DEBUG_LEVEL="${withval}"
        if test "x$ENABLE_DEBUG" = xtrue; then
          AC_MSG_ERROR([You cannot use both --enable-debug and --with-debug-level at the same time.])
        fi
      ])
  AC_MSG_RESULT([$DEBUG_LEVEL])

  if test "x$DEBUG_LEVEL" != xrelease && \
      test "x$DEBUG_LEVEL" != xoptimized && \
      test "x$DEBUG_LEVEL" != xfastdebug && \
      test "x$DEBUG_LEVEL" != xslowdebug; then
    AC_MSG_ERROR([Allowed debug levels are: release, fastdebug, slowdebug and optimized])
  fi

  # Translate DEBUG_LEVEL to debug level used by Hotspot
  HOTSPOT_DEBUG_LEVEL="$DEBUG_LEVEL"
  if test "x$DEBUG_LEVEL" = xrelease; then
    HOTSPOT_DEBUG_LEVEL="product"
  elif test "x$DEBUG_LEVEL" = xslowdebug; then
    HOTSPOT_DEBUG_LEVEL="debug"
  fi

  if test "x$DEBUG_LEVEL" = xoptimized; then
    # The debug level 'optimized' is a little special because it is currently only
    # applicable to the HotSpot build where it means to build a completely
    # optimized version of the VM without any debugging code (like for the
    # 'release' debug level which is called 'product' in the HotSpot build) but
    # with the exception that it can contain additional code which is otherwise
    # protected by '#ifndef PRODUCT' macros. These 'optimized' builds are used to
    # test new and/or experimental features which are not intended for customer
    # shipment. Because these new features need to be tested and benchmarked in
    # real world scenarios, we want to build the containing JDK at the 'release'
    # debug level.
    DEBUG_LEVEL="release"
  fi

  AC_SUBST(HOTSPOT_DEBUG_LEVEL)
  AC_SUBST(DEBUG_LEVEL)
])

###############################################################################
#
# Should we build only OpenJDK even if closed sources are present?
#
AC_DEFUN_ONCE([JDKOPT_SETUP_OPEN_OR_CUSTOM],
[
  UTIL_ARG_ENABLE(NAME: openjdk-only, DEFAULT: false,
      RESULT: SUPPRESS_CUSTOM_EXTENSIONS,
      DESC: [suppress building custom source even if present],
      CHECKING_MSG: [if custom source is suppressed (openjdk-only)])
])

AC_DEFUN_ONCE([JDKOPT_SETUP_JDK_OPTIONS],
[
  # Should we build a JDK without a graphical UI?
  UTIL_ARG_ENABLE(NAME: headless-only, DEFAULT: false,
      RESULT: ENABLE_HEADLESS_ONLY,
      DESC: [only build headless (no GUI) support],
      CHECKING_MSG: [if we should build headless-only (no GUI)])
  AC_SUBST(ENABLE_HEADLESS_ONLY)

  # should we linktime gc unused code sections in the JDK build ?
  if test "x$OPENJDK_TARGET_OS" = "xlinux" && test "x$OPENJDK_TARGET_CPU" = xs390x; then
    LINKTIME_GC_DEFAULT=true
  else
    LINKTIME_GC_DEFAULT=false
  fi

  UTIL_ARG_ENABLE(NAME: linktime-gc, DEFAULT: $LINKTIME_GC_DEFAULT,
      DEFAULT_DESC: [auto], RESULT: ENABLE_LINKTIME_GC,
      DESC: [use link time gc on unused code sections in the JDK build],
      CHECKING_MSG: [if linker should clean out unused code (linktime-gc)])
  AC_SUBST(ENABLE_LINKTIME_GC)

  # Check for full doc dependencies
  FULL_DOCS_AVAILABLE=true
  AC_MSG_CHECKING([for graphviz dot])
  if test "x$DOT" != "x"; then
    AC_MSG_RESULT([yes])
  else
    AC_MSG_RESULT([no, cannot generate full docs])
    FULL_DOCS_AVAILABLE=false
  fi

  AC_MSG_CHECKING([for pandoc])
  if test "x$ENABLE_PANDOC" = "xtrue"; then
    AC_MSG_RESULT([yes])
  else
    AC_MSG_RESULT([no, cannot generate full docs])
    FULL_DOCS_AVAILABLE=false
  fi

  # Should we build the complete docs, or just a lightweight version?
  UTIL_ARG_ENABLE(NAME: full-docs, DEFAULT: auto, RESULT: ENABLE_FULL_DOCS,
      AVAILABLE: $FULL_DOCS_AVAILABLE, DESC: [build complete documentation],
      DEFAULT_DESC: [enabled if all tools found])
  AC_SUBST(ENABLE_FULL_DOCS)

  # Choose cacerts source file
  AC_ARG_WITH(cacerts-file, [AS_HELP_STRING([--with-cacerts-file],
      [specify alternative cacerts file])])
  AC_MSG_CHECKING([for cacerts file])
  if test "x$with_cacerts_file" == x; then
    AC_MSG_RESULT([default])
  else
    CACERTS_FILE=$with_cacerts_file
    if test ! -f "$CACERTS_FILE"; then
      AC_MSG_RESULT([fail])
      AC_MSG_ERROR([Specified cacerts file "$CACERTS_FILE" does not exist])
    fi
    AC_MSG_RESULT([$CACERTS_FILE])
  fi
  AC_SUBST(CACERTS_FILE)

  # Choose cacerts source folder for user provided PEM files
  AC_ARG_WITH(cacerts-src, [AS_HELP_STRING([--with-cacerts-src],
      [specify alternative cacerts source folder containing certificates])])
  CACERTS_SRC=""
  AC_MSG_CHECKING([for cacerts source])
  if test "x$with_cacerts_src" == x; then
    AC_MSG_RESULT([default])
  else
    CACERTS_SRC=$with_cacerts_src
    if test ! -d "$CACERTS_SRC"; then
      AC_MSG_RESULT([fail])
      AC_MSG_ERROR([Specified cacerts source folder "$CACERTS_SRC" does not exist])
    fi
    AC_MSG_RESULT([$CACERTS_SRC])
  fi
  AC_SUBST(CACERTS_SRC)

  # Enable or disable unlimited crypto
  UTIL_ARG_ENABLE(NAME: unlimited-crypto, DEFAULT: true, RESULT: UNLIMITED_CRYPTO,
      DESC: [enable unlimited crypto policy])
  AC_SUBST(UNLIMITED_CRYPTO)

  # Should we build the serviceability agent (SA)?
  INCLUDE_SA=true
  if HOTSPOT_CHECK_JVM_VARIANT(zero); then
    INCLUDE_SA=false
  fi
  if test "x$OPENJDK_TARGET_OS" = xaix ; then
    INCLUDE_SA=false
  fi
  if test "x$OPENJDK_TARGET_CPU" = xs390x ; then
    INCLUDE_SA=false
  fi
  AC_SUBST(INCLUDE_SA)

  # Compress jars
  COMPRESS_JARS=false

  AC_SUBST(COMPRESS_JARS)

  # Setup default copyright year. Mostly overridden when building close to a new year.
  AC_ARG_WITH(copyright-year, [AS_HELP_STRING([--with-copyright-year],
      [Set copyright year value for build @<:@current year/source-date@:>@])])
  if test "x$with_copyright_year" = xyes; then
    AC_MSG_ERROR([Copyright year must have a value])
  elif test "x$with_copyright_year" != x; then
    COPYRIGHT_YEAR="$with_copyright_year"
  elif test "x$SOURCE_DATE" != xupdated; then
    if test "x$IS_GNU_DATE" = xyes; then
      COPYRIGHT_YEAR=`$DATE --date=@$SOURCE_DATE +%Y`
    else
      COPYRIGHT_YEAR=`$DATE -j -f %s $SOURCE_DATE +%Y`
    fi
  else
    COPYRIGHT_YEAR=`$DATE +'%Y'`
  fi
  AC_SUBST(COPYRIGHT_YEAR)

  # Override default library path
  AC_ARG_WITH([jni-libpath], [AS_HELP_STRING([--with-jni-libpath],
      [override default JNI library search path])])
  AC_MSG_CHECKING([for jni library path])
  if test "x${with_jni_libpath}" = "x" || test "x${with_jni_libpath}" = "xno"; then
    AC_MSG_RESULT([default])
  elif test "x${with_jni_libpath}" = "xyes"; then
    AC_MSG_RESULT([invalid])
    AC_MSG_ERROR([The --with-jni-libpath option requires an argument.])
  else
    HOTSPOT_OVERRIDE_LIBPATH=${with_jni_libpath}
    if test "x$OPENJDK_TARGET_OS" != "xlinux" &&
         test "x$OPENJDK_TARGET_OS" != "xbsd" &&
         test "x$OPENJDK_TARGET_OS" != "xaix"; then
      AC_MSG_RESULT([fail])
      AC_MSG_ERROR([Overriding JNI library path is supported only on Linux, BSD and AIX.])
    fi
    AC_MSG_RESULT(${HOTSPOT_OVERRIDE_LIBPATH})
  fi
  AC_SUBST(HOTSPOT_OVERRIDE_LIBPATH)

])

###############################################################################

AC_DEFUN_ONCE([JDKOPT_SETUP_DEBUG_SYMBOLS],
[
  #
  # Native debug symbols.
  # This must be done after the toolchain is setup, since we're looking at objcopy.
  #
  AC_MSG_CHECKING([what type of native debug symbols to use])
  AC_ARG_WITH([native-debug-symbols],
      [AS_HELP_STRING([--with-native-debug-symbols],
      [set the native debug symbol configuration (none, internal, external, zipped) @<:@varying@:>@])],
      [
        if test "x$OPENJDK_TARGET_OS" = xwindows; then
          if test "x$withval" = xinternal; then
            AC_MSG_ERROR([Windows does not support the parameter 'internal' for --with-native-debug-symbols])
          fi
        fi
      ],
      [
        if test "x$STATIC_BUILD" = xtrue; then
          with_native_debug_symbols="none"
        else
          with_native_debug_symbols="external"
        fi
      ])
  AC_MSG_RESULT([$with_native_debug_symbols])

  if test "x$with_native_debug_symbols" = xnone; then
    COMPILE_WITH_DEBUG_SYMBOLS=false
    COPY_DEBUG_SYMBOLS=false
    ZIP_EXTERNAL_DEBUG_SYMBOLS=false
  elif test "x$with_native_debug_symbols" = xinternal; then
    COMPILE_WITH_DEBUG_SYMBOLS=true
    COPY_DEBUG_SYMBOLS=false
    ZIP_EXTERNAL_DEBUG_SYMBOLS=false
  elif test "x$with_native_debug_symbols" = xexternal; then

    if test "x$OPENJDK_TARGET_OS" = xlinux; then
      if test "x$OBJCOPY" = x; then
        # enabling of enable-debug-symbols and can't find objcopy
        # this is an error
        AC_MSG_ERROR([Unable to find objcopy, cannot enable native debug symbols])
      fi
    fi

    COMPILE_WITH_DEBUG_SYMBOLS=true
    COPY_DEBUG_SYMBOLS=true
    ZIP_EXTERNAL_DEBUG_SYMBOLS=false
  elif test "x$with_native_debug_symbols" = xzipped; then

    if test "x$OPENJDK_TARGET_OS" = xlinux; then
      if test "x$OBJCOPY" = x; then
        # enabling of enable-debug-symbols and can't find objcopy
        # this is an error
        AC_MSG_ERROR([Unable to find objcopy, cannot enable native debug symbols])
      fi
    fi

    COMPILE_WITH_DEBUG_SYMBOLS=true
    COPY_DEBUG_SYMBOLS=true
    ZIP_EXTERNAL_DEBUG_SYMBOLS=true
  else
    AC_MSG_ERROR([Allowed native debug symbols are: none, internal, external, zipped])
  fi

  AC_SUBST(COMPILE_WITH_DEBUG_SYMBOLS)
  AC_SUBST(COPY_DEBUG_SYMBOLS)
  AC_SUBST(ZIP_EXTERNAL_DEBUG_SYMBOLS)

  # Should we add external native debug symbols to the shipped bundles?
  AC_MSG_CHECKING([if we should add external native debug symbols to the shipped bundles])
  AC_ARG_WITH([external-symbols-in-bundles],
      [AS_HELP_STRING([--with-external-symbols-in-bundles],
      [which type of external native debug symbol information shall be shipped in product bundles (none, public, full)
      (e.g. ship full/stripped pdbs on Windows) @<:@none@:>@])])

  if test "x$with_external_symbols_in_bundles" = x || test "x$with_external_symbols_in_bundles" = xnone ; then
    AC_MSG_RESULT([no])
  elif test "x$with_external_symbols_in_bundles" = xfull || test "x$with_external_symbols_in_bundles" = xpublic ; then
    if test "x$OPENJDK_TARGET_OS" != xwindows ; then
      AC_MSG_ERROR([--with-external-symbols-in-bundles currently only works on windows!])
    elif test "x$COPY_DEBUG_SYMBOLS" != xtrue ; then
      AC_MSG_ERROR([--with-external-symbols-in-bundles only works when --with-native-debug-symbols=external is used!])
    elif test "x$with_external_symbols_in_bundles" = xfull ; then
      AC_MSG_RESULT([full])
      SHIP_DEBUG_SYMBOLS=full
    else
      AC_MSG_RESULT([public])
      SHIP_DEBUG_SYMBOLS=public
    fi
  else
    AC_MSG_ERROR([$with_external_symbols_in_bundles is an unknown value for --with-external-symbols-in-bundles])
  fi

  AC_SUBST(SHIP_DEBUG_SYMBOLS)
])

################################################################################
#
# Native and Java code coverage
#
AC_DEFUN_ONCE([JDKOPT_SETUP_CODE_COVERAGE],
[
  UTIL_ARG_ENABLE(NAME: native-coverage, DEFAULT: false, RESULT: GCOV_ENABLED,
      DESC: [enable native compilation with code coverage data],
      CHECK_AVAILABLE: [
        AC_MSG_CHECKING([if native coverage is available])
        if test "x$TOOLCHAIN_TYPE" = "xgcc" ||
            test "x$TOOLCHAIN_TYPE" = "xclang"; then
          AC_MSG_RESULT([yes])
        else
          AC_MSG_RESULT([no])
          AVAILABLE=false
        fi
      ],
      IF_ENABLED: [
        GCOV_CFLAGS="-fprofile-arcs -ftest-coverage -fno-inline"
        GCOV_LDFLAGS="-fprofile-arcs"
        JVM_CFLAGS="$JVM_CFLAGS $GCOV_CFLAGS"
        JVM_LDFLAGS="$JVM_LDFLAGS $GCOV_LDFLAGS"
        CFLAGS_JDKLIB="$CFLAGS_JDKLIB $GCOV_CFLAGS"
        CFLAGS_JDKEXE="$CFLAGS_JDKEXE $GCOV_CFLAGS"
        CXXFLAGS_JDKLIB="$CXXFLAGS_JDKLIB $GCOV_CFLAGS"
        CXXFLAGS_JDKEXE="$CXXFLAGS_JDKEXE $GCOV_CFLAGS"
        LDFLAGS_JDKLIB="$LDFLAGS_JDKLIB $GCOV_LDFLAGS"
        LDFLAGS_JDKEXE="$LDFLAGS_JDKEXE $GCOV_LDFLAGS"
      ])
  AC_SUBST(GCOV_ENABLED)

  AC_ARG_WITH(jcov, [AS_HELP_STRING([--with-jcov],
      [jcov library location])])
  AC_ARG_WITH(jcov-input-jdk, [AS_HELP_STRING([--with-jcov-input-jdk],
      [jdk image to instrument])])
  AC_ARG_WITH(jcov-filters, [AS_HELP_STRING([--with-jcov-filters],
      [filters to limit code for jcov instrumentation and report generation])])
  JCOV_HOME=
  JCOV_INPUT_JDK=
  JCOV_ENABLED=
  JCOV_FILTERS=
  if test "x$with_jcov" = "x" ; then
    JCOV_ENABLED="false"
  else
    JCOV_HOME="$with_jcov"
    if test ! -f "$JCOV_HOME/lib/jcov.jar"; then
      AC_MSG_RESULT([fail])
      AC_MSG_ERROR([Invalid JCov bundle: "$JCOV_HOME/lib/jcov.jar" does not exist])
    fi
    JCOV_ENABLED="true"
    UTIL_FIXUP_PATH(JCOV_HOME)
    if test "x$with_jcov_input_jdk" != "x" ; then
      JCOV_INPUT_JDK="$with_jcov_input_jdk"
      if test ! -f "$JCOV_INPUT_JDK/bin/java" && test ! -f "$JCOV_INPUT_JDK/bin/java.exe"; then
        AC_MSG_RESULT([fail])
        AC_MSG_ERROR([Invalid JDK bundle: "$JCOV_INPUT_JDK/bin/java" does not exist])
      fi
      UTIL_FIXUP_PATH(JCOV_INPUT_JDK)
    fi
    if test "x$with_jcov_filters" != "x" ; then
      JCOV_FILTERS="$with_jcov_filters"
    fi
  fi
  AC_SUBST(JCOV_ENABLED)
  AC_SUBST(JCOV_HOME)
  AC_SUBST(JCOV_INPUT_JDK)
  AC_SUBST(JCOV_FILTERS)
])

###############################################################################
#
# AddressSanitizer
#
AC_DEFUN_ONCE([JDKOPT_SETUP_ADDRESS_SANITIZER],
[
  UTIL_ARG_ENABLE(NAME: asan, DEFAULT: false, RESULT: ASAN_ENABLED,
      DESC: [enable AddressSanitizer],
      CHECK_AVAILABLE: [
        AC_MSG_CHECKING([if AddressSanitizer (asan) is available])
        if test "x$TOOLCHAIN_TYPE" = "xgcc" ||
           test "x$TOOLCHAIN_TYPE" = "xclang" ||
           test "x$TOOLCHAIN_TYPE" = "xmicrosoft"; then
          AC_MSG_RESULT([yes])
        else
          AC_MSG_RESULT([no])
          AVAILABLE=false
        fi
      ],
      IF_ENABLED: [
        if test "x$TOOLCHAIN_TYPE" = "xgcc" ||
           test "x$TOOLCHAIN_TYPE" = "xclang"; then
          # ASan is simply incompatible with gcc -Wstringop-truncation. See
          # https://gcc.gnu.org/bugzilla/show_bug.cgi?id=85650
          # It's harmless to be suppressed in clang as well.
          ASAN_CFLAGS="-fsanitize=address -Wno-stringop-truncation -fno-omit-frame-pointer -fno-common -DADDRESS_SANITIZER"
          ASAN_LDFLAGS="-fsanitize=address"
        elif test "x$TOOLCHAIN_TYPE" = "xmicrosoft"; then
          # -Oy- is equivalent to -fno-omit-frame-pointer in GCC/Clang.
          ASAN_CFLAGS="-fsanitize=address -Oy- -DADDRESS_SANITIZER"
          # MSVC produces a warning if you pass -fsanitize=address to the linker. It also complains
          $ if -DEBUG is not passed to the linker when building with ASan.
          ASAN_LDFLAGS="-debug"
        fi
        JVM_CFLAGS="$JVM_CFLAGS $ASAN_CFLAGS"
        JVM_LDFLAGS="$JVM_LDFLAGS $ASAN_LDFLAGS"
        CFLAGS_JDKLIB="$CFLAGS_JDKLIB $ASAN_CFLAGS"
        CFLAGS_JDKEXE="$CFLAGS_JDKEXE $ASAN_CFLAGS"
        CXXFLAGS_JDKLIB="$CXXFLAGS_JDKLIB $ASAN_CFLAGS"
        CXXFLAGS_JDKEXE="$CXXFLAGS_JDKEXE $ASAN_CFLAGS"
        LDFLAGS_JDKLIB="$LDFLAGS_JDKLIB $ASAN_LDFLAGS"
        LDFLAGS_JDKEXE="$LDFLAGS_JDKEXE $ASAN_LDFLAGS"
      ])
  AC_SUBST(ASAN_ENABLED)
])

###############################################################################
#
# LeakSanitizer
#
AC_DEFUN_ONCE([JDKOPT_SETUP_LEAK_SANITIZER],
[
  UTIL_ARG_ENABLE(NAME: lsan, DEFAULT: false, RESULT: LSAN_ENABLED,
      DESC: [enable LeakSanitizer],
      CHECK_AVAILABLE: [
        AC_MSG_CHECKING([if LeakSanitizer (lsan) is available])
        if test "x$TOOLCHAIN_TYPE" = "xgcc" ||
            test "x$TOOLCHAIN_TYPE" = "xclang"; then
          AC_MSG_RESULT([yes])
        else
          AC_MSG_RESULT([no])
          AVAILABLE=false
        fi
      ],
      IF_ENABLED: [
        LSAN_CFLAGS="-fsanitize=leak -fno-omit-frame-pointer -DLEAK_SANITIZER"
        LSAN_LDFLAGS="-fsanitize=leak"
        JVM_CFLAGS="$JVM_CFLAGS $LSAN_CFLAGS"
        JVM_LDFLAGS="$JVM_LDFLAGS $LSAN_LDFLAGS"
        CFLAGS_JDKLIB="$CFLAGS_JDKLIB $LSAN_CFLAGS"
        CFLAGS_JDKEXE="$CFLAGS_JDKEXE $LSAN_CFLAGS"
        CXXFLAGS_JDKLIB="$CXXFLAGS_JDKLIB $LSAN_CFLAGS"
        CXXFLAGS_JDKEXE="$CXXFLAGS_JDKEXE $LSAN_CFLAGS"
        LDFLAGS_JDKLIB="$LDFLAGS_JDKLIB $LSAN_LDFLAGS"
        LDFLAGS_JDKEXE="$LDFLAGS_JDKEXE $LSAN_LDFLAGS"
      ])
  AC_SUBST(LSAN_ENABLED)
])

###############################################################################
#
# UndefinedBehaviorSanitizer
#
AC_DEFUN_ONCE([JDKOPT_SETUP_UNDEFINED_BEHAVIOR_SANITIZER],
[
  # GCC reports lots of likely false positives for stringop-truncation and format-overflow.
  # Silence them for now.
  UBSAN_CHECKS="-fsanitize=undefined -fsanitize=float-divide-by-zero -fno-sanitize=shift-base"
  UBSAN_CFLAGS="$UBSAN_CHECKS -Wno-stringop-truncation -Wno-format-overflow -fno-omit-frame-pointer -DUNDEFINED_BEHAVIOR_SANITIZER"
  UBSAN_LDFLAGS="$UBSAN_CHECKS"
  UTIL_ARG_ENABLE(NAME: ubsan, DEFAULT: false, RESULT: UBSAN_ENABLED,
      DESC: [enable UndefinedBehaviorSanitizer],
      CHECK_AVAILABLE: [
        AC_MSG_CHECKING([if UndefinedBehaviorSanitizer (ubsan) is available])
        if test "x$TOOLCHAIN_TYPE" = "xgcc" ||
            test "x$TOOLCHAIN_TYPE" = "xclang"; then
          AC_MSG_RESULT([yes])
        else
          AC_MSG_RESULT([no])
          AVAILABLE=false
        fi
      ],
      IF_ENABLED: [
        JVM_CFLAGS="$JVM_CFLAGS $UBSAN_CFLAGS"
        JVM_LDFLAGS="$JVM_LDFLAGS $UBSAN_LDFLAGS"
        CFLAGS_JDKLIB="$CFLAGS_JDKLIB $UBSAN_CFLAGS"
        CFLAGS_JDKEXE="$CFLAGS_JDKEXE $UBSAN_CFLAGS"
        CXXFLAGS_JDKLIB="$CXXFLAGS_JDKLIB $UBSAN_CFLAGS"
        CXXFLAGS_JDKEXE="$CXXFLAGS_JDKEXE $UBSAN_CFLAGS"
        LDFLAGS_JDKLIB="$LDFLAGS_JDKLIB $UBSAN_LDFLAGS"
        LDFLAGS_JDKEXE="$LDFLAGS_JDKEXE $UBSAN_LDFLAGS"
      ])
  if test "x$UBSAN_ENABLED" = xfalse; then
    UBSAN_CFLAGS=""
    UBSAN_LDFLAGS=""
  fi
  AC_SUBST(UBSAN_CFLAGS)
  AC_SUBST(UBSAN_LDFLAGS)
  AC_SUBST(UBSAN_ENABLED)
])

################################################################################
#
# Static build support.  When enabled will generate static
# libraries instead of shared libraries for all JDK libs.
#
AC_DEFUN_ONCE([JDKOPT_SETUP_STATIC_BUILD],
[
  UTIL_ARG_ENABLE(NAME: static-build, DEFAULT: false, RESULT: STATIC_BUILD,
      DESC: [enable static library build],
      CHECKING_MSG: [if static build is enabled],
      CHECK_AVAILABLE: [
        AC_MSG_CHECKING([if static build is available])
        if test "x$OPENJDK_TARGET_OS" = "xmacosx"; then
          AC_MSG_RESULT([yes])
        else
          AC_MSG_RESULT([no])
          AVAILABLE=false
        fi
      ],
      IF_ENABLED: [
        STATIC_BUILD_CFLAGS="-DSTATIC_BUILD=1"
        CFLAGS_JDKLIB_EXTRA="$CFLAGS_JDKLIB_EXTRA $STATIC_BUILD_CFLAGS"
        CXXFLAGS_JDKLIB_EXTRA="$CXXFLAGS_JDKLIB_EXTRA $STATIC_BUILD_CFLAGS"
      ])
  AC_SUBST(STATIC_BUILD)
])

################################################################################
#
# jmod options.
#
AC_DEFUN_ONCE([JDKOPT_SETUP_JMOD_OPTIONS],
[
  # Final JMODs are recompiled often during development, and java.base JMOD
  # includes the JVM libraries. In release mode, prefer to compress JMODs fully.
  # In debug mode, pay with a little extra space, but win a lot of CPU time back
  # with the lightest (but still some) compression.
  if test "x$DEBUG_LEVEL" = xrelease; then
    DEFAULT_JMOD_COMPRESS="zip-6"
  else
    DEFAULT_JMOD_COMPRESS="zip-1"
  fi

  UTIL_ARG_WITH(NAME: jmod-compress, TYPE: literal,
    VALID_VALUES: [zip-0 zip-1 zip-2 zip-3 zip-4 zip-5 zip-6 zip-7 zip-8 zip-9],
    DEFAULT: $DEFAULT_JMOD_COMPRESS,
    CHECKING_MSG: [for JMOD compression type],
    DESC: [specify JMOD compression type (zip-[0-9])]
  )
  AC_SUBST(JMOD_COMPRESS)
])

################################################################################
#
# jlink options.
# We always keep packaged modules in JDK image.
#
AC_DEFUN_ONCE([JDKOPT_SETUP_JLINK_OPTIONS],
[
  UTIL_ARG_ENABLE(NAME: keep-packaged-modules, DEFAULT: true,
      RESULT: JLINK_KEEP_PACKAGED_MODULES,
      DESC: [enable keeping of packaged modules in jdk image],
      CHECKING_MSG: [if packaged modules are kept])
  AC_SUBST(JLINK_KEEP_PACKAGED_MODULES)
])

################################################################################
#
# Enable or disable generation of the classlist at build time
#
AC_DEFUN_ONCE([JDKOPT_ENABLE_DISABLE_GENERATE_CLASSLIST],
[
  # In GenerateLinkOptData.gmk, DumpLoadedClassList is used to generate the
  # classlist file. It never will work if CDS is disabled, since the VM will report
  # an error for DumpLoadedClassList.
  UTIL_ARG_ENABLE(NAME: generate-classlist, DEFAULT: auto,
      RESULT: ENABLE_GENERATE_CLASSLIST, AVAILABLE: $ENABLE_CDS,
      DESC: [enable generation of a CDS classlist at build time],
      DEFAULT_DESC: [enabled if the JVM feature 'cds' is enabled for all JVM variants],
      CHECKING_MSG: [if the CDS classlist generation should be enabled])
  AC_SUBST(ENABLE_GENERATE_CLASSLIST)
])

################################################################################
#
# Optionally filter resource translations
#
AC_DEFUN([JDKOPT_EXCLUDE_TRANSLATIONS],
[
  AC_ARG_WITH([exclude-translations], [AS_HELP_STRING([--with-exclude-translations],
      [a comma separated list of locales to exclude translations for. Default is
      to include all translations present in the source.])])

  EXCLUDE_TRANSLATIONS=""
  AC_MSG_CHECKING([if any translations should be excluded])
  if test "x$with_exclude_translations" != "x"; then
    EXCLUDE_TRANSLATIONS="${with_exclude_translations//,/ }"
    AC_MSG_RESULT([yes: $EXCLUDE_TRANSLATIONS])
  else
    AC_MSG_RESULT([no])
  fi

  AC_SUBST(EXCLUDE_TRANSLATIONS)
])

################################################################################
#
# Optionally disable man pages
#
AC_DEFUN([JDKOPT_ENABLE_DISABLE_MANPAGES],
[
  UTIL_ARG_ENABLE(NAME: manpages, DEFAULT: true, RESULT: BUILD_MANPAGES,
      DESC: [enable copying of static man pages],
      CHECKING_MSG: [if static man pages should be copied])
  AC_SUBST(BUILD_MANPAGES)
])

################################################################################
#
# Disable the default CDS archive generation
#
AC_DEFUN([JDKOPT_ENABLE_DISABLE_CDS_ARCHIVE],
[
  UTIL_ARG_ENABLE(NAME: cds-archive, DEFAULT: auto, RESULT: BUILD_CDS_ARCHIVE,
      DESC: [enable generation of a default CDS archive in the product image],
      DEFAULT_DESC: [enabled if possible],
      CHECKING_MSG: [if a default CDS archive should be generated],
      CHECK_AVAILABLE: [
        AC_MSG_CHECKING([if CDS archive is available])
        if test "x$ENABLE_CDS" = "xfalse"; then
          AC_MSG_RESULT([no (CDS is disabled)])
          AVAILABLE=false
        elif test "x$COMPILE_TYPE" = "xcross"; then
          AC_MSG_RESULT([no (not possible with cross compilation)])
          AVAILABLE=false
        else
          AC_MSG_RESULT([yes])
        fi
      ])
  AC_SUBST(BUILD_CDS_ARCHIVE)
])

################################################################################
#
# Enable the alternative CDS core region alignment
#
AC_DEFUN([JDKOPT_ENABLE_DISABLE_COMPATIBLE_CDS_ALIGNMENT],
[
  UTIL_ARG_ENABLE(NAME: compatible-cds-alignment, DEFAULT: false,
      RESULT: ENABLE_COMPATIBLE_CDS_ALIGNMENT,
      DESC: [enable use alternative compatible cds core region alignment],
      DEFAULT_DESC: [disabled],
      CHECKING_MSG: [if compatible cds region alignment enabled],
      CHECK_AVAILABLE: [
        AC_MSG_CHECKING([if CDS archive is available])
        if test "x$ENABLE_CDS" = "xfalse"; then
          AVAILABLE=false
          AC_MSG_RESULT([no (CDS is disabled)])
        else
          AVAILABLE=true
          AC_MSG_RESULT([yes])
        fi
      ])
  AC_SUBST(ENABLE_COMPATIBLE_CDS_ALIGNMENT)
])

################################################################################
#
# Disallow any output from containing absolute paths from the build system.
# This setting defaults to allowed on debug builds and not allowed on release
# builds.
#
AC_DEFUN([JDKOPT_ALLOW_ABSOLUTE_PATHS_IN_OUTPUT],
[
  AC_ARG_ENABLE([absolute-paths-in-output],
      [AS_HELP_STRING([--disable-absolute-paths-in-output],
       [Set to disable to prevent any absolute paths from the build to end up in
        any of the build output. @<:@disabled in release builds, otherwise enabled@:>@])
      ])

  AC_MSG_CHECKING([if absolute paths should be allowed in the build output])
  if test "x$enable_absolute_paths_in_output" = "xno"; then
    AC_MSG_RESULT([no, forced])
    ALLOW_ABSOLUTE_PATHS_IN_OUTPUT="false"
  elif test "x$enable_absolute_paths_in_output" = "xyes"; then
    AC_MSG_RESULT([yes, forced])
    ALLOW_ABSOLUTE_PATHS_IN_OUTPUT="true"
  elif test "x$DEBUG_LEVEL" = "xrelease"; then
    AC_MSG_RESULT([no, release build])
    ALLOW_ABSOLUTE_PATHS_IN_OUTPUT="false"
  else
    AC_MSG_RESULT([yes, debug build])
    ALLOW_ABSOLUTE_PATHS_IN_OUTPUT="true"
  fi

  AC_SUBST(ALLOW_ABSOLUTE_PATHS_IN_OUTPUT)
])

################################################################################
#
# Check and set options related to reproducible builds.
#
AC_DEFUN_ONCE([JDKOPT_SETUP_REPRODUCIBLE_BUILD],
[
  AC_ARG_WITH([source-date], [AS_HELP_STRING([--with-source-date],
      [how to set SOURCE_DATE_EPOCH ('updated', 'current', 'version' a timestamp or an ISO-8601 date) @<:@current/value of SOURCE_DATE_EPOCH@:>@])],
      [with_source_date_present=true], [with_source_date_present=false])

  if test "x$SOURCE_DATE_EPOCH" != x && test "x$with_source_date" != x; then
    AC_MSG_WARN([--with-source-date will override SOURCE_DATE_EPOCH])
  fi

  AC_MSG_CHECKING([what source date to use])

  if test "x$with_source_date" = xyes; then
    AC_MSG_ERROR([--with-source-date must have a value])
  elif test "x$with_source_date" = x; then
    if test "x$SOURCE_DATE_EPOCH" != x; then
      SOURCE_DATE=$SOURCE_DATE_EPOCH
      with_source_date_present=true
      AC_MSG_RESULT([$SOURCE_DATE, from SOURCE_DATE_EPOCH])
    else
      # Tell makefiles to take the time from configure
      SOURCE_DATE=$($DATE +"%s")
      AC_MSG_RESULT([$SOURCE_DATE, from 'current' (default)])
    fi
  elif test "x$with_source_date" = xupdated; then
    SOURCE_DATE=updated
    AC_MSG_RESULT([determined at build time, from 'updated'])
  elif test "x$with_source_date" = xcurrent; then
    # Set the current time
    SOURCE_DATE=$($DATE +"%s")
    AC_MSG_RESULT([$SOURCE_DATE, from 'current'])
  elif test "x$with_source_date" = xversion; then
    # Use the date from version-numbers.conf
    UTIL_GET_EPOCH_TIMESTAMP(SOURCE_DATE, $DEFAULT_VERSION_DATE)
    if test "x$SOURCE_DATE" = x; then
      AC_MSG_RESULT([unavailable])
      AC_MSG_ERROR([Cannot convert DEFAULT_VERSION_DATE to timestamp])
    fi
    AC_MSG_RESULT([$SOURCE_DATE, from 'version'])
  else
    # It's a timestamp, an ISO-8601 date, or an invalid string
    # Additional [] needed to keep m4 from mangling shell constructs.
    if [ [[ "$with_source_date" =~ ^[0-9][0-9]*$ ]] ] ; then
      SOURCE_DATE=$with_source_date
      AC_MSG_RESULT([$SOURCE_DATE, from timestamp on command line])
    else
      UTIL_GET_EPOCH_TIMESTAMP(SOURCE_DATE, $with_source_date)
      if test "x$SOURCE_DATE" != x; then
        AC_MSG_RESULT([$SOURCE_DATE, from ISO-8601 date on command line])
      else
        AC_MSG_RESULT([unavailable])
        AC_MSG_ERROR([Cannot parse date string "$with_source_date"])
      fi
    fi
  fi

  ISO_8601_FORMAT_STRING="%Y-%m-%dT%H:%M:%SZ"
  if test "x$SOURCE_DATE" != xupdated; then
    # If we have a fixed value for SOURCE_DATE, we need to set SOURCE_DATE_EPOCH
    # for the rest of configure.
    SOURCE_DATE_EPOCH="$SOURCE_DATE"
    if test "x$IS_GNU_DATE" = xyes; then
      SOURCE_DATE_ISO_8601=`$DATE --utc --date="@$SOURCE_DATE" +"$ISO_8601_FORMAT_STRING" 2> /dev/null`
    else
      SOURCE_DATE_ISO_8601=`$DATE -u -j -f "%s" "$SOURCE_DATE" +"$ISO_8601_FORMAT_STRING" 2> /dev/null`
    fi
  fi

  AC_SUBST(SOURCE_DATE)
  AC_SUBST(ISO_8601_FORMAT_STRING)
  AC_SUBST(SOURCE_DATE_ISO_8601)

  UTIL_DEPRECATED_ARG_ENABLE(reproducible-build)
])

################################################################################
#
# Setup signing on macOS. This can either be setup to sign with a real identity
# and enabling the hardened runtime, or it can simply add the debug entitlement
# com.apple.security.get-task-allow without actually signing any binaries. The
# latter is needed to be able to debug processes and dump core files on modern
# versions of macOS. It can also be skipped completely.
#
# Check if codesign will run with the given parameters
# $1: Parameters to run with
# $2: Checking message
# Sets CODESIGN_SUCCESS=true/false
AC_DEFUN([JDKOPT_CHECK_CODESIGN_PARAMS],
[
  PARAMS="$1"
  MESSAGE="$2"
  CODESIGN_TESTFILE="$CONFIGURESUPPORT_OUTPUTDIR/codesign-testfile"
  $RM "$CODESIGN_TESTFILE"
  $TOUCH "$CODESIGN_TESTFILE"
  CODESIGN_SUCCESS=false

  $ECHO "check codesign, calling $CODESIGN $PARAMS $CODESIGN_TESTFILE" >&AS_MESSAGE_LOG_FD

  eval \"$CODESIGN\" $PARAMS \"$CODESIGN_TESTFILE\" 2>&AS_MESSAGE_LOG_FD \
      >&AS_MESSAGE_LOG_FD && CODESIGN_SUCCESS=true
  $RM "$CODESIGN_TESTFILE"
  AC_MSG_CHECKING([$MESSAGE])
  if test "x$CODESIGN_SUCCESS" = "xtrue"; then
    AC_MSG_RESULT([yes])
  else
    AC_MSG_RESULT([no])
  fi
])

AC_DEFUN([JDKOPT_CHECK_CODESIGN_HARDENED],
[
  JDKOPT_CHECK_CODESIGN_PARAMS([-s \"$MACOSX_CODESIGN_IDENTITY\" --option runtime],
      [if codesign with hardened runtime is possible])
])

AC_DEFUN([JDKOPT_CHECK_CODESIGN_DEBUG],
[
  JDKOPT_CHECK_CODESIGN_PARAMS([-s -], [if debug mode codesign is possible])
])

AC_DEFUN([JDKOPT_SETUP_MACOSX_SIGNING],
[
  ENABLE_CODESIGN=false
  if test "x$OPENJDK_TARGET_OS" = "xmacosx" && test "x$CODESIGN" != "x"; then

    UTIL_ARG_WITH(NAME: macosx-codesign, TYPE: literal, OPTIONAL: true,
        VALID_VALUES: [hardened debug auto], DEFAULT: auto,
        ENABLED_DEFAULT: true,
        CHECKING_MSG: [for macosx code signing mode],
        DESC: [set the macosx code signing mode (hardened, debug, auto)]
    )

    MACOSX_CODESIGN_MODE=disabled
    if test "x$MACOSX_CODESIGN_ENABLED" = "xtrue"; then

      # Check for user provided code signing identity.
      UTIL_ARG_WITH(NAME: macosx-codesign-identity, TYPE: string,
          DEFAULT: openjdk_codesign, CHECK_VALUE: [UTIL_CHECK_STRING_NON_EMPTY],
          DESC: [specify the macosx code signing identity],
          CHECKING_MSG: [for macosx code signing identity]
      )
      AC_SUBST(MACOSX_CODESIGN_IDENTITY)

      if test "x$MACOSX_CODESIGN" = "xauto"; then
        # Only try to default to hardened signing on release builds
        if test "x$DEBUG_LEVEL" = "xrelease"; then
          JDKOPT_CHECK_CODESIGN_HARDENED
          if test "x$CODESIGN_SUCCESS" = "xtrue"; then
            MACOSX_CODESIGN_MODE=hardened
          fi
        fi
        if test "x$MACOSX_CODESIGN_MODE" = "xdisabled"; then
          JDKOPT_CHECK_CODESIGN_DEBUG
          if test "x$CODESIGN_SUCCESS" = "xtrue"; then
            MACOSX_CODESIGN_MODE=debug
          fi
        fi
        AC_MSG_CHECKING([for macosx code signing mode])
        AC_MSG_RESULT([$MACOSX_CODESIGN_MODE])
      elif test "x$MACOSX_CODESIGN" = "xhardened"; then
        JDKOPT_CHECK_CODESIGN_HARDENED
        if test "x$CODESIGN_SUCCESS" = "xfalse"; then
          AC_MSG_ERROR([Signing with hardened runtime is not possible])
        fi
        MACOSX_CODESIGN_MODE=hardened
      elif test "x$MACOSX_CODESIGN" = "xdebug"; then
        JDKOPT_CHECK_CODESIGN_DEBUG
        if test "x$CODESIGN_SUCCESS" = "xfalse"; then
          AC_MSG_ERROR([Signing in debug mode is not possible])
        fi
        MACOSX_CODESIGN_MODE=debug
      else
        AC_MSG_ERROR([unknown value for --with-macosx-codesign: $MACOSX_CODESIGN])
      fi
    fi
    AC_SUBST(MACOSX_CODESIGN_IDENTITY)
    AC_SUBST(MACOSX_CODESIGN_MODE)
  fi
])

################################################################################
#
# fallback linker
#
AC_DEFUN_ONCE([JDKOPT_SETUP_FALLBACK_LINKER],
[
  FALLBACK_LINKER_DEFAULT=false

  if HOTSPOT_CHECK_JVM_VARIANT(zero); then
    FALLBACK_LINKER_DEFAULT=true
  fi

  UTIL_ARG_ENABLE(NAME: fallback-linker, DEFAULT: $FALLBACK_LINKER_DEFAULT,
      RESULT: ENABLE_FALLBACK_LINKER,
      DESC: [enable libffi-based fallback implementation of java.lang.foreign.Linker],
      CHECKING_MSG: [if fallback linker enabled])
  AC_SUBST(ENABLE_FALLBACK_LINKER)
])
