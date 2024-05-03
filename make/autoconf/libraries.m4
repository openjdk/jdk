#
# Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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

# Major library component reside in separate files.
m4_include([lib-alsa.m4])
m4_include([lib-bundled.m4])
m4_include([lib-cups.m4])
m4_include([lib-ffi.m4])
m4_include([lib-fontconfig.m4])
m4_include([lib-freetype.m4])
m4_include([lib-hsdis.m4])
m4_include([lib-std.m4])
m4_include([lib-x11.m4])

m4_include([lib-tests.m4])

################################################################################
# Determine which libraries are needed for this configuration
################################################################################
AC_DEFUN_ONCE([LIB_DETERMINE_DEPENDENCIES],
[
  # Check if X11 is needed
  if test "x$OPENJDK_TARGET_OS" = xwindows || test "x$OPENJDK_TARGET_OS" = xmacosx; then
    # No X11 support on windows or macosx
    NEEDS_LIB_X11=false
  else
    # All other instances need X11, even if building headless only, libawt still
    # needs X11 headers.
    NEEDS_LIB_X11=true
  fi

  # Check if fontconfig is needed
  if test "x$OPENJDK_TARGET_OS" = xwindows || test "x$OPENJDK_TARGET_OS" = xmacosx; then
    # No fontconfig support on windows or macosx
    NEEDS_LIB_FONTCONFIG=false
  else
    # All other instances need fontconfig, even if building headless only,
    # libawt still needs fontconfig headers.
    NEEDS_LIB_FONTCONFIG=true
  fi

  # Check if cups is needed
  if test "x$OPENJDK_TARGET_OS" = xwindows; then
    # Windows have a separate print system
    NEEDS_LIB_CUPS=false
  else
    NEEDS_LIB_CUPS=true
  fi

  # A custom hook may have set this already
  if test "x$NEEDS_LIB_FREETYPE" = "x"; then
    NEEDS_LIB_FREETYPE=true
  fi

  # Check if alsa is needed
  if test "x$OPENJDK_TARGET_OS" = xlinux; then
    NEEDS_LIB_ALSA=true
  else
    NEEDS_LIB_ALSA=false
  fi

  # Check if ffi is needed
  if HOTSPOT_CHECK_JVM_VARIANT(zero) || test "x$ENABLE_FALLBACK_LINKER" = "xtrue"; then
    NEEDS_LIB_FFI=true
  else
    NEEDS_LIB_FFI=false
  fi
])

################################################################################
# Setup BASIC_JVM_LIBS that can be different depending on build/target platform
################################################################################
AC_DEFUN([LIB_SETUP_JVM_LIBS],
[
  # Atomic library
  # 32-bit platforms needs fallback library for 8-byte atomic ops on Zero
  if HOTSPOT_CHECK_JVM_VARIANT(zero); then
    if test "x$OPENJDK_$1_OS" = xlinux &&
        (test "x$OPENJDK_$1_CPU" = xarm ||
         test "x$OPENJDK_$1_CPU" = xm68k ||
         test "x$OPENJDK_$1_CPU" = xmips ||
         test "x$OPENJDK_$1_CPU" = xmipsel ||
         test "x$OPENJDK_$1_CPU" = xppc ||
         test "x$OPENJDK_$1_CPU" = xsh ||
         test "x$OPENJDK_$1_CPU" = xriscv32); then
      BASIC_JVM_LIBS_$1="$BASIC_JVM_LIBS_$1 -latomic"
    fi
  fi
])

################################################################################
# Parse library options, and setup needed libraries
################################################################################
AC_DEFUN_ONCE([LIB_SETUP_LIBRARIES],
[
  LIB_SETUP_STD_LIBS

  LIB_SETUP_ALSA
  LIB_SETUP_BUNDLED_LIBS
  LIB_SETUP_CUPS
  LIB_SETUP_FONTCONFIG
  LIB_SETUP_FREETYPE
  LIB_SETUP_HSDIS
  LIB_SETUP_LIBFFI
  LIB_SETUP_MISC_LIBS
  LIB_SETUP_X11

  LIB_TESTS_SETUP_GTEST

  # Math library
  BASIC_JVM_LIBS="$LIBM"

  # Dynamic loading library
  if test "x$OPENJDK_TARGET_OS" = xlinux || test "x$OPENJDK_TARGET_OS" = xaix; then
    BASIC_JVM_LIBS="$BASIC_JVM_LIBS $LIBDL"
  fi

  # Threading library
  if test "x$OPENJDK_TARGET_OS" = xlinux || test "x$OPENJDK_TARGET_OS" = xaix; then
    BASIC_JVM_LIBS="$BASIC_JVM_LIBS -lpthread"
  fi

  # librt for legacy clock_gettime
  if test "x$OPENJDK_TARGET_OS" = xlinux; then
    # Hotspot needs to link librt to get the clock_* functions.
    # But once our supported minimum build and runtime platform
    # has glibc 2.17, this can be removed as the functions are
    # in libc.
    BASIC_JVM_LIBS="$BASIC_JVM_LIBS -lrt"
  fi

  # perfstat lib
  if test "x$OPENJDK_TARGET_OS" = xaix; then
    BASIC_JVM_LIBS="$BASIC_JVM_LIBS -lperfstat"
  fi

  if test "x$OPENJDK_TARGET_OS" = xwindows; then
    BASIC_JVM_LIBS="$BASIC_JVM_LIBS kernel32.lib user32.lib gdi32.lib winspool.lib \
        comdlg32.lib advapi32.lib shell32.lib ole32.lib oleaut32.lib powrprof.lib uuid.lib \
        ws2_32.lib winmm.lib version.lib psapi.lib"
  fi
  LIB_SETUP_JVM_LIBS(BUILD)
  LIB_SETUP_JVM_LIBS(TARGET)

  JVM_LIBS="$BASIC_JVM_LIBS $BASIC_JVM_LIBS_TARGET"
  OPENJDK_BUILD_JDKLIB_LIBS=""
  OPENJDK_BUILD_JVM_LIBS="$BASIC_JVM_LIBS $BASIC_JVM_LIBS_BUILD"

  AC_SUBST(JVM_LIBS)
  AC_SUBST(OPENJDK_BUILD_JDKLIB_LIBS)
  AC_SUBST(OPENJDK_BUILD_JVM_LIBS)
])

################################################################################
# Setup various libraries, typically small system libraries
################################################################################
AC_DEFUN_ONCE([LIB_SETUP_MISC_LIBS],
[
  # Setup libm (the maths library)
  if test "x$OPENJDK_TARGET_OS" != "xwindows"; then
    AC_CHECK_LIB(m, cos, [], [
        AC_MSG_NOTICE([Maths library was not found])
    ])
    LIBM="-lm"
  else
    LIBM=""
  fi
  AC_SUBST(LIBM)

  # Setup libdl (for dynamic library loading)
  save_LIBS="$LIBS"
  LIBS=""
  AC_CHECK_LIB(dl, dlopen)
  LIBDL="$LIBS"
  AC_SUBST(LIBDL)
  LIBS="$save_LIBS"

  # Control if libzip can use mmap. Available for purposes of overriding.
  LIBZIP_CAN_USE_MMAP=true
  AC_SUBST(LIBZIP_CAN_USE_MMAP)
])
