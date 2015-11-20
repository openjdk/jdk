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

AC_DEFUN_ONCE([JDKOPT_SETUP_JDK_VARIANT],
[
  ###############################################################################
  #
  # Check which variant of the JDK that we want to build.
  # Currently we have:
  #    normal:   standard edition
  # but the custom make system may add other variants
  #
  # Effectively the JDK variant gives a name to a specific set of
  # modules to compile into the JDK. In the future, these modules
  # might even be Jigsaw modules.
  #
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

AC_DEFUN_ONCE([JDKOPT_SETUP_JVM_INTERPRETER],
[
###############################################################################
#
# Check which interpreter of the JVM we want to build.
# Currently we have:
#    template: Template interpreter (the default)
#    cpp     : C++ interpreter
AC_MSG_CHECKING([which interpreter of the JVM to build])
AC_ARG_WITH([jvm-interpreter], [AS_HELP_STRING([--with-jvm-interpreter],
	[JVM interpreter to build (template, cpp) @<:@template@:>@])])

if test "x$with_jvm_interpreter" = x; then
     with_jvm_interpreter="template"
fi

JVM_INTERPRETER="$with_jvm_interpreter"

if test "x$JVM_INTERPRETER" != xtemplate && test "x$JVM_INTERPRETER" != xcpp; then
   AC_MSG_ERROR([The available JVM interpreters are: template, cpp])
fi

AC_SUBST(JVM_INTERPRETER)

AC_MSG_RESULT([$with_jvm_interpreter])
])

AC_DEFUN_ONCE([JDKOPT_SETUP_JVM_VARIANTS],
[

  ###############################################################################
  #
  # Check which variants of the JVM that we want to build.
  # Currently we have:
  #    server: normal interpreter and a tiered C1/C2 compiler
  #    client: normal interpreter and C1 (no C2 compiler) (only 32-bit platforms)
  #    minimal1: reduced form of client with optional VM services and features stripped out
  #    kernel: kernel footprint JVM that passes the TCK without major performance problems,
  #             ie normal interpreter and C1, only the serial GC, kernel jvmti etc
  #    zero: no machine code interpreter, no compiler
  #    zeroshark: zero interpreter and shark/llvm compiler backend
#    core: interpreter only, no compiler (only works on some platforms)
  AC_MSG_CHECKING([which variants of the JVM to build])
  AC_ARG_WITH([jvm-variants], [AS_HELP_STRING([--with-jvm-variants],
	[JVM variants (separated by commas) to build (server, client, minimal1, kernel, zero, zeroshark, core) @<:@server@:>@])])

  if test "x$with_jvm_variants" = x; then
    with_jvm_variants="server"
  fi

  JVM_VARIANTS=",$with_jvm_variants,"
  TEST_VARIANTS=`$ECHO "$JVM_VARIANTS" | $SED -e 's/server,//' -e 's/client,//'  -e 's/minimal1,//' -e 's/kernel,//' -e 's/zero,//' -e 's/zeroshark,//' -e 's/core,//'`

  if test "x$TEST_VARIANTS" != "x,"; then
     AC_MSG_ERROR([The available JVM variants are: server, client, minimal1, kernel, zero, zeroshark, core])
  fi
  AC_MSG_RESULT([$with_jvm_variants])

  JVM_VARIANT_SERVER=`$ECHO "$JVM_VARIANTS" | $SED -e '/,server,/!s/.*/false/g' -e '/,server,/s/.*/true/g'`
  JVM_VARIANT_CLIENT=`$ECHO "$JVM_VARIANTS" | $SED -e '/,client,/!s/.*/false/g' -e '/,client,/s/.*/true/g'`
  JVM_VARIANT_MINIMAL1=`$ECHO "$JVM_VARIANTS" | $SED -e '/,minimal1,/!s/.*/false/g' -e '/,minimal1,/s/.*/true/g'`
  JVM_VARIANT_KERNEL=`$ECHO "$JVM_VARIANTS" | $SED -e '/,kernel,/!s/.*/false/g' -e '/,kernel,/s/.*/true/g'`
  JVM_VARIANT_ZERO=`$ECHO "$JVM_VARIANTS" | $SED -e '/,zero,/!s/.*/false/g' -e '/,zero,/s/.*/true/g'`
  JVM_VARIANT_ZEROSHARK=`$ECHO "$JVM_VARIANTS" | $SED -e '/,zeroshark,/!s/.*/false/g' -e '/,zeroshark,/s/.*/true/g'`
  JVM_VARIANT_CORE=`$ECHO "$JVM_VARIANTS" | $SED -e '/,core,/!s/.*/false/g' -e '/,core,/s/.*/true/g'`

  if test "x$JVM_VARIANT_CLIENT" = xtrue; then
    if test "x$OPENJDK_TARGET_CPU_BITS" = x64; then
      AC_MSG_ERROR([You cannot build a client JVM for a 64-bit machine.])
    fi
  fi
  if test "x$JVM_VARIANT_KERNEL" = xtrue; then
    if test "x$OPENJDK_TARGET_CPU_BITS" = x64; then
      AC_MSG_ERROR([You cannot build a kernel JVM for a 64-bit machine.])
    fi
  fi
  if test "x$JVM_VARIANT_MINIMAL1" = xtrue; then
    if test "x$OPENJDK_TARGET_CPU_BITS" = x64; then
      AC_MSG_ERROR([You cannot build a minimal JVM for a 64-bit machine.])
    fi
  fi

  # Replace the commas with AND for use in the build directory name.
  ANDED_JVM_VARIANTS=`$ECHO "$JVM_VARIANTS" | $SED -e 's/^,//' -e 's/,$//' -e 's/,/AND/g'`
  COUNT_VARIANTS=`$ECHO "$JVM_VARIANTS" | $SED -e 's/server,/1/' -e 's/client,/1/' -e 's/minimal1,/1/' -e 's/kernel,/1/' -e 's/zero,/1/' -e 's/zeroshark,/1/' -e 's/core,/1/'`
  if test "x$COUNT_VARIANTS" != "x,1"; then
    BUILDING_MULTIPLE_JVM_VARIANTS=yes
  else
    BUILDING_MULTIPLE_JVM_VARIANTS=no
  fi

  AC_SUBST(JVM_VARIANTS)
  AC_SUBST(JVM_VARIANT_SERVER)
  AC_SUBST(JVM_VARIANT_CLIENT)
  AC_SUBST(JVM_VARIANT_MINIMAL1)
  AC_SUBST(JVM_VARIANT_KERNEL)
  AC_SUBST(JVM_VARIANT_ZERO)
  AC_SUBST(JVM_VARIANT_ZEROSHARK)
  AC_SUBST(JVM_VARIANT_CORE)

  INCLUDE_SA=true
  if test "x$JVM_VARIANT_ZERO" = xtrue ; then
    INCLUDE_SA=false
  fi
  if test "x$JVM_VARIANT_ZEROSHARK" = xtrue ; then
    INCLUDE_SA=false
  fi
  if test "x$OPENJDK_TARGET_OS" = xaix ; then
    INCLUDE_SA=false
  fi
  if test "x$OPENJDK_TARGET_CPU" = xaarch64; then
    INCLUDE_SA=false
  fi
  AC_SUBST(INCLUDE_SA)

  if test "x$OPENJDK_TARGET_OS" = "xmacosx"; then
    MACOSX_UNIVERSAL="true"
  fi

  AC_SUBST(MACOSX_UNIVERSAL)
])

AC_DEFUN_ONCE([JDKOPT_SETUP_DEBUG_LEVEL],
[
  ###############################################################################
  #
  # Set the debug level
  #    release: no debug information, all optimizations, no asserts.
  #    optimized: no debug information, all optimizations, no asserts, HotSpot target is 'optimized'.
  #    fastdebug: debug information (-g), all optimizations, all asserts
  #    slowdebug: debug information (-g), no optimizations, all asserts
  #
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


  ###############################################################################
  #
  # Setup legacy vars/targets and new vars to deal with different debug levels.
  #

  case $DEBUG_LEVEL in
    release )
      VARIANT="OPT"
      FASTDEBUG="false"
      DEBUG_CLASSFILES="false"
      BUILD_VARIANT_RELEASE=""
      HOTSPOT_DEBUG_LEVEL="product"
      HOTSPOT_EXPORT="product"
      ;;
    fastdebug )
      VARIANT="DBG"
      FASTDEBUG="true"
      DEBUG_CLASSFILES="true"
      BUILD_VARIANT_RELEASE="-fastdebug"
      HOTSPOT_DEBUG_LEVEL="fastdebug"
      HOTSPOT_EXPORT="fastdebug"
      ;;
    slowdebug )
      VARIANT="DBG"
      FASTDEBUG="false"
      DEBUG_CLASSFILES="true"
      BUILD_VARIANT_RELEASE="-debug"
      HOTSPOT_DEBUG_LEVEL="debug"
      HOTSPOT_EXPORT="debug"
      ;;
    optimized )
      VARIANT="OPT"
      FASTDEBUG="false"
      DEBUG_CLASSFILES="false"
      BUILD_VARIANT_RELEASE="-optimized"
      HOTSPOT_DEBUG_LEVEL="optimized"
      HOTSPOT_EXPORT="optimized"
      ;;
  esac

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
  if test "x$DEBUG_LEVEL" = xoptimized; then
    DEBUG_LEVEL="release"
  fi

  #####
  # Generate the legacy makefile targets for hotspot.
  # The hotspot api for selecting the build artifacts, really, needs to be improved.
  # JDK-7195896 will fix this on the hotspot side by using the JVM_VARIANT_* variables to
  # determine what needs to be built. All we will need to set here is all_product, all_fastdebug etc
  # But until then ...
  HOTSPOT_TARGET=""

  if test "x$JVM_VARIANT_SERVER" = xtrue; then
    HOTSPOT_TARGET="$HOTSPOT_TARGET${HOTSPOT_DEBUG_LEVEL} "
  fi

  if test "x$JVM_VARIANT_CLIENT" = xtrue; then
    HOTSPOT_TARGET="$HOTSPOT_TARGET${HOTSPOT_DEBUG_LEVEL}1 "
  fi

  if test "x$JVM_VARIANT_MINIMAL1" = xtrue; then
    HOTSPOT_TARGET="$HOTSPOT_TARGET${HOTSPOT_DEBUG_LEVEL}minimal1 "
  fi

  if test "x$JVM_VARIANT_KERNEL" = xtrue; then
    HOTSPOT_TARGET="$HOTSPOT_TARGET${HOTSPOT_DEBUG_LEVEL}kernel "
  fi

  if test "x$JVM_VARIANT_ZERO" = xtrue; then
    HOTSPOT_TARGET="$HOTSPOT_TARGET${HOTSPOT_DEBUG_LEVEL}zero "
  fi

  if test "x$JVM_VARIANT_ZEROSHARK" = xtrue; then
    HOTSPOT_TARGET="$HOTSPOT_TARGET${HOTSPOT_DEBUG_LEVEL}shark "
  fi

  if test "x$JVM_VARIANT_CORE" = xtrue; then
    HOTSPOT_TARGET="$HOTSPOT_TARGET${HOTSPOT_DEBUG_LEVEL}core "
  fi

  HOTSPOT_TARGET="$HOTSPOT_TARGET docs export_$HOTSPOT_EXPORT"

  # On Macosx universal binaries are produced, but they only contain
  # 64 bit intel. This invalidates control of which jvms are built
  # from configure, but only server is valid anyway. Fix this
  # when hotspot makefiles are rewritten.
  if test "x$MACOSX_UNIVERSAL" = xtrue; then
    HOTSPOT_TARGET=universal_${HOTSPOT_EXPORT}
  fi

  #####

  AC_SUBST(DEBUG_LEVEL)
  AC_SUBST(VARIANT)
  AC_SUBST(FASTDEBUG)
  AC_SUBST(DEBUG_CLASSFILES)
  AC_SUBST(BUILD_VARIANT_RELEASE)
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

  ###############################################################################
  #
  # Should we build a JDK/JVM with headful support (ie a graphical ui)?
  # We always build headless support.
  #
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

  # Control wether Hotspot runs Queens test after build.
  AC_ARG_ENABLE([hotspot-test-in-build], [AS_HELP_STRING([--enable-hotspot-test-in-build],
      [run the Queens test after Hotspot build @<:@disabled@:>@])],,
      [enable_hotspot_test_in_build=no])
  if test "x$enable_hotspot_test_in_build" = "xyes"; then
    TEST_IN_BUILD=true
  else
    TEST_IN_BUILD=false
  fi
  AC_SUBST(TEST_IN_BUILD)

  ###############################################################################
  #
  # Choose cacerts source file
  #
  AC_ARG_WITH(cacerts-file, [AS_HELP_STRING([--with-cacerts-file],
      [specify alternative cacerts file])])
  if test "x$with_cacerts_file" != x; then
    CACERTS_FILE=$with_cacerts_file
  fi
  AC_SUBST(CACERTS_FILE)

  ###############################################################################
  #
  # Enable or disable unlimited crypto
  #
  AC_ARG_ENABLE(unlimited-crypto, [AS_HELP_STRING([--enable-unlimited-crypto],
      [Enable unlimited crypto policy @<:@disabled@:>@])],,
      [enable_unlimited_crypto=no])
  if test "x$enable_unlimited_crypto" = "xyes"; then
    UNLIMITED_CRYPTO=true
  else
    UNLIMITED_CRYPTO=false
  fi
  AC_SUBST(UNLIMITED_CRYPTO)

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

  ###############################################################################
  #
  # Compress jars
  #
  COMPRESS_JARS=false

  AC_SUBST(COMPRESS_JARS)
])

###############################################################################
#
# Setup version numbers
#
AC_DEFUN_ONCE([JDKOPT_SETUP_JDK_VERSION_NUMBERS],
[
  # Source the version numbers
  . $AUTOCONF_DIR/version-numbers

  # Get the settings from parameters
  AC_ARG_WITH(milestone, [AS_HELP_STRING([--with-milestone],
      [Set milestone value for build @<:@internal@:>@])])
  if test "x$with_milestone" = xyes; then
    AC_MSG_ERROR([Milestone must have a value])
  elif test "x$with_milestone" != x; then
    MILESTONE="$with_milestone"
  fi
  if test "x$MILESTONE" = x; then
    MILESTONE=internal
  fi

  AC_ARG_WITH(update-version, [AS_HELP_STRING([--with-update-version],
      [Set update version value for build @<:@b00@:>@])])
  if test "x$with_update_version" = xyes; then
    AC_MSG_ERROR([Update version must have a value])
  elif test "x$with_update_version" != x; then
    JDK_UPDATE_VERSION="$with_update_version"
    # On macosx 10.7, it's not possible to set --with-update-version=0X due
    # to a bug in expr (which reduces it to just X). To work around this, we
    # always add a 0 to one digit update versions.
    if test "${#JDK_UPDATE_VERSION}" = "1"; then
      JDK_UPDATE_VERSION="0${JDK_UPDATE_VERSION}"
    fi
  fi

  AC_ARG_WITH(user-release-suffix, [AS_HELP_STRING([--with-user-release-suffix],
      [Add a custom string to the version string if build number is not set.@<:@username_builddateb00@:>@])])
  if test "x$with_user_release_suffix" = xyes; then
    AC_MSG_ERROR([Release suffix must have a value])
  elif test "x$with_user_release_suffix" != x; then
    USER_RELEASE_SUFFIX="$with_user_release_suffix"
  fi

  AC_ARG_WITH(build-number, [AS_HELP_STRING([--with-build-number],
      [Set build number value for build @<:@b00@:>@])])
  if test "x$with_build_number" = xyes; then
    AC_MSG_ERROR([Build number must have a value])
  elif test "x$with_build_number" != x; then
    JDK_BUILD_NUMBER="$with_build_number"
  fi
  # Define default USER_RELEASE_SUFFIX if BUILD_NUMBER and USER_RELEASE_SUFFIX are not set
  if test "x$JDK_BUILD_NUMBER" = x; then
    JDK_BUILD_NUMBER=b00
    if test "x$USER_RELEASE_SUFFIX" = x; then
      BUILD_DATE=`date '+%Y_%m_%d_%H_%M'`
      # Avoid [:alnum:] since it depends on the locale.
      CLEAN_USERNAME=`echo "$USER" | $TR -d -c 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'`
      USER_RELEASE_SUFFIX=`echo "${CLEAN_USERNAME}_${BUILD_DATE}" | $TR 'ABCDEFGHIJKLMNOPQRSTUVWXYZ' 'abcdefghijklmnopqrstuvwxyz'`
    fi
  fi

  # Now set the JDK version, milestone, build number etc.
  AC_SUBST(USER_RELEASE_SUFFIX)
  AC_SUBST(JDK_MAJOR_VERSION)
  AC_SUBST(JDK_MINOR_VERSION)
  AC_SUBST(JDK_MICRO_VERSION)
  AC_SUBST(JDK_UPDATE_VERSION)
  AC_SUBST(JDK_BUILD_NUMBER)
  AC_SUBST(MILESTONE)
  AC_SUBST(LAUNCHER_NAME)
  AC_SUBST(PRODUCT_NAME)
  AC_SUBST(PRODUCT_SUFFIX)
  AC_SUBST(JDK_RC_PLATFORM_NAME)
  AC_SUBST(COMPANY_NAME)
  AC_SUBST(MACOSX_BUNDLE_NAME_BASE)
  AC_SUBST(MACOSX_BUNDLE_ID_BASE)

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

  if test "x$JDK_UPDATE_VERSION" != x; then
    JDK_VERSION="${JDK_MAJOR_VERSION}.${JDK_MINOR_VERSION}.${JDK_MICRO_VERSION}_${JDK_UPDATE_VERSION}"
  else
    JDK_VERSION="${JDK_MAJOR_VERSION}.${JDK_MINOR_VERSION}.${JDK_MICRO_VERSION}"
  fi
  AC_SUBST(JDK_VERSION)

  COOKED_BUILD_NUMBER=`$ECHO $JDK_BUILD_NUMBER | $SED -e 's/^b//' -e 's/^0//'`
  AC_SUBST(COOKED_BUILD_NUMBER)
])

AC_DEFUN_ONCE([JDKOPT_SETUP_BUILD_TWEAKS],
[
  HOTSPOT_MAKE_ARGS="$HOTSPOT_TARGET"
  AC_SUBST(HOTSPOT_MAKE_ARGS)

  # The name of the Service Agent jar.
  SALIB_NAME="${LIBRARY_PREFIX}saproc${SHARED_LIBRARY_SUFFIX}"
  if test "x$OPENJDK_TARGET_OS" = "xwindows"; then
    SALIB_NAME="${LIBRARY_PREFIX}sawindbg${SHARED_LIBRARY_SUFFIX}"
  fi
  AC_SUBST(SALIB_NAME)
])

AC_DEFUN_ONCE([JDKOPT_SETUP_DEBUG_SYMBOLS],
[
  #
  # ENABLE_DEBUG_SYMBOLS
  # This must be done after the toolchain is setup, since we're looking at objcopy.
  #
  AC_ARG_ENABLE([debug-symbols],
      [AS_HELP_STRING([--disable-debug-symbols],[disable generation of debug symbols @<:@enabled@:>@])])

  AC_MSG_CHECKING([if we should generate debug symbols])

  if test "x$enable_debug_symbols" = "xyes" && test "x$OBJCOPY" = x; then
    # explicit enabling of enable-debug-symbols and can't find objcopy
    #   this is an error
    AC_MSG_ERROR([Unable to find objcopy, cannot enable debug-symbols])
  fi

  if test "x$enable_debug_symbols" = "xyes"; then
    ENABLE_DEBUG_SYMBOLS=true
  elif test "x$enable_debug_symbols" = "xno"; then
    ENABLE_DEBUG_SYMBOLS=false
  else
    # Default is on if objcopy is found
    if test "x$OBJCOPY" != x; then
      ENABLE_DEBUG_SYMBOLS=true
    # MacOS X and Windows don't use objcopy but default is on for those OSes
    elif test "x$OPENJDK_TARGET_OS" = xmacosx || test "x$OPENJDK_TARGET_OS" = xwindows; then
      ENABLE_DEBUG_SYMBOLS=true
    else
      ENABLE_DEBUG_SYMBOLS=false
    fi
  fi

  AC_MSG_RESULT([$ENABLE_DEBUG_SYMBOLS])

  #
  # ZIP_DEBUGINFO_FILES
  #
  AC_MSG_CHECKING([if we should zip debug-info files])
  AC_ARG_ENABLE([zip-debug-info],
      [AS_HELP_STRING([--disable-zip-debug-info],[disable zipping of debug-info files @<:@enabled@:>@])],
      [enable_zip_debug_info="${enableval}"], [enable_zip_debug_info="yes"])
  AC_MSG_RESULT([${enable_zip_debug_info}])

  if test "x${enable_zip_debug_info}" = "xno"; then
    ZIP_DEBUGINFO_FILES=false
  else
    ZIP_DEBUGINFO_FILES=true
  fi

  AC_SUBST(ENABLE_DEBUG_SYMBOLS)
  AC_SUBST(ZIP_DEBUGINFO_FILES)
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


