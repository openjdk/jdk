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
# Check which interpreter of the JVM we want to build.
# Currently we have:
#    template: Template interpreter (the default)
#    cpp     : C++ interpreter
AC_DEFUN_ONCE([HOTSPOT_SETUP_JVM_INTERPRETER],
[
  AC_ARG_WITH([jvm-interpreter], [AS_HELP_STRING([--with-jvm-interpreter],
    [JVM interpreter to build (template, cpp) @<:@template@:>@])])

  AC_MSG_CHECKING([which interpreter of the JVM to build])
  if test "x$with_jvm_interpreter" = x; then
    JVM_INTERPRETER="template"
  else
    JVM_INTERPRETER="$with_jvm_interpreter"
  fi
  AC_MSG_RESULT([$JVM_INTERPRETER])

  if test "x$JVM_INTERPRETER" != xtemplate && test "x$JVM_INTERPRETER" != xcpp; then
    AC_MSG_ERROR([The available JVM interpreters are: template, cpp])
  fi

  AC_SUBST(JVM_INTERPRETER)
])

###############################################################################
# Check which variants of the JVM that we want to build.
# Currently we have:
#    server: normal interpreter and a C2 or tiered C1/C2 compiler
#    client: normal interpreter and C1 (no C2 compiler) (only 32-bit platforms)
#    minimal1: reduced form of client with optional VM services and features stripped out
#    zero: no machine code interpreter, no compiler
#    zeroshark: zero interpreter and shark/llvm compiler backend
#    core: interpreter only, no compiler (only works on some platforms)
AC_DEFUN_ONCE([HOTSPOT_SETUP_JVM_VARIANTS],
[
  AC_MSG_CHECKING([which variants of the JVM to build])
  AC_ARG_WITH([jvm-variants], [AS_HELP_STRING([--with-jvm-variants],
      [JVM variants (separated by commas) to build (server, client, minimal1, zero, zeroshark, core) @<:@server@:>@])])

  if test "x$with_jvm_variants" = x; then
    with_jvm_variants="server"
  fi

  JVM_VARIANTS=",$with_jvm_variants,"
  TEST_VARIANTS=`$ECHO "$JVM_VARIANTS" | $SED -e 's/server,//' -e 's/client,//'  -e 's/minimal1,//' -e 's/zero,//' -e 's/zeroshark,//' -e 's/core,//'`

  if test "x$TEST_VARIANTS" != "x,"; then
    AC_MSG_ERROR([The available JVM variants are: server, client, minimal1, zero, zeroshark, core])
  fi
  AC_MSG_RESULT([$with_jvm_variants])

  JVM_VARIANT_SERVER=`$ECHO "$JVM_VARIANTS" | $SED -e '/,server,/!s/.*/false/g' -e '/,server,/s/.*/true/g'`
  JVM_VARIANT_CLIENT=`$ECHO "$JVM_VARIANTS" | $SED -e '/,client,/!s/.*/false/g' -e '/,client,/s/.*/true/g'`
  JVM_VARIANT_MINIMAL1=`$ECHO "$JVM_VARIANTS" | $SED -e '/,minimal1,/!s/.*/false/g' -e '/,minimal1,/s/.*/true/g'`
  JVM_VARIANT_ZERO=`$ECHO "$JVM_VARIANTS" | $SED -e '/,zero,/!s/.*/false/g' -e '/,zero,/s/.*/true/g'`
  JVM_VARIANT_ZEROSHARK=`$ECHO "$JVM_VARIANTS" | $SED -e '/,zeroshark,/!s/.*/false/g' -e '/,zeroshark,/s/.*/true/g'`
  JVM_VARIANT_CORE=`$ECHO "$JVM_VARIANTS" | $SED -e '/,core,/!s/.*/false/g' -e '/,core,/s/.*/true/g'`

  if test "x$JVM_VARIANT_CLIENT" = xtrue; then
    if test "x$OPENJDK_TARGET_CPU_BITS" = x64; then
      AC_MSG_ERROR([You cannot build a client JVM for a 64-bit machine.])
    fi
  fi
  if test "x$JVM_VARIANT_MINIMAL1" = xtrue; then
    if test "x$OPENJDK_TARGET_CPU_BITS" = x64; then
      AC_MSG_ERROR([You cannot build a minimal JVM for a 64-bit machine.])
    fi
  fi

  # Replace the commas with AND for use in the build directory name.
  ANDED_JVM_VARIANTS=`$ECHO "$JVM_VARIANTS" | $SED -e 's/^,//' -e 's/,$//' -e 's/,/AND/g'`
  COUNT_VARIANTS=`$ECHO "$JVM_VARIANTS" | $SED -e 's/server,/1/' -e 's/client,/1/' -e 's/minimal1,/1/' -e 's/zero,/1/' -e 's/zeroshark,/1/' -e 's/core,/1/'`
  if test "x$COUNT_VARIANTS" != "x,1"; then
    BUILDING_MULTIPLE_JVM_VARIANTS=yes
  else
    BUILDING_MULTIPLE_JVM_VARIANTS=no
  fi

  if test "x$JVM_VARIANT_ZERO" = xtrue && test "x$BUILDING_MULTIPLE_JVM_VARIANTS" = xyes; then
    AC_MSG_ERROR([You cannot build multiple variants with zero.])
  fi

  AC_SUBST(JVM_VARIANTS)
  AC_SUBST(JVM_VARIANT_SERVER)
  AC_SUBST(JVM_VARIANT_CLIENT)
  AC_SUBST(JVM_VARIANT_MINIMAL1)
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


###############################################################################
# Setup legacy vars/targets and new vars to deal with different debug levels.
#
#    release: no debug information, all optimizations, no asserts.
#    optimized: no debug information, all optimizations, no asserts, HotSpot target is 'optimized'.
#    fastdebug: debug information (-g), all optimizations, all asserts
#    slowdebug: debug information (-g), no optimizations, all asserts
#
AC_DEFUN_ONCE([HOTSPOT_SETUP_DEBUG_LEVEL],
[
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

AC_DEFUN_ONCE([HOTSPOT_SETUP_HOTSPOT_OPTIONS],
[
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
])

AC_DEFUN_ONCE([HOTSPOT_SETUP_BUILD_TWEAKS],
[
  HOTSPOT_MAKE_ARGS="$HOTSPOT_TARGET"
  AC_SUBST(HOTSPOT_MAKE_ARGS)
])
