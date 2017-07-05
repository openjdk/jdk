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

# Major library component reside in separate files.
m4_include([lib-alsa.m4])
m4_include([lib-bundled.m4])
m4_include([lib-cups.m4])
m4_include([lib-ffi.m4])
m4_include([lib-freetype.m4])
m4_include([lib-std.m4])
m4_include([lib-x11.m4])

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
    if test "x$SUPPORT_HEADFUL" = xno; then
      # No X11 support if building headless-only
      NEEDS_LIB_X11=false
    else
      # All other instances need X11
      NEEDS_LIB_X11=true
    fi
  fi

  # Check if cups is needed
  if test "x$OPENJDK_TARGET_OS" = xwindows; then
    # Windows have a separate print system
    NEEDS_LIB_CUPS=false
  else
    NEEDS_LIB_CUPS=true
  fi

  # Check if freetype is needed
  if test "x$OPENJDK" = "xtrue"; then
    NEEDS_LIB_FREETYPE=true
  else
    NEEDS_LIB_FREETYPE=false
  fi

  # Check if alsa is needed
  if test "x$OPENJDK_TARGET_OS" = xlinux; then
    NEEDS_LIB_ALSA=true
  else
    NEEDS_LIB_ALSA=false
  fi

  # Check if ffi is needed
  if test "x$JVM_VARIANT_ZERO" = xtrue || test "x$JVM_VARIANT_ZEROSHARK" = xtrue; then
    NEEDS_LIB_FFI=true
  else
    NEEDS_LIB_FFI=false
  fi
])

################################################################################
# Parse library options, and setup needed libraries
################################################################################
AC_DEFUN_ONCE([LIB_SETUP_LIBRARIES],
[
  LIB_SETUP_STD_LIBS
  LIB_SETUP_X11
  LIB_SETUP_CUPS
  LIB_SETUP_FREETYPE
  LIB_SETUP_ALSA
  LIB_SETUP_LIBFFI
  LIB_SETUP_LLVM
  LIB_SETUP_BUNDLED_LIBS
  LIB_SETUP_MISC_LIBS
])

################################################################################
# Setup llvm (Low-Level VM)
################################################################################
AC_DEFUN_ONCE([LIB_SETUP_LLVM],
[
  if test "x$JVM_VARIANT_ZEROSHARK" = xtrue; then
    AC_CHECK_PROG([LLVM_CONFIG], [llvm-config], [llvm-config])

    if test "x$LLVM_CONFIG" != xllvm-config; then
      AC_MSG_ERROR([llvm-config not found in $PATH.])
    fi

    llvm_components="jit mcjit engine nativecodegen native"
    unset LLVM_CFLAGS
    for flag in $("$LLVM_CONFIG" --cxxflags); do
      if echo "${flag}" | grep -q '^-@<:@ID@:>@'; then
        if test "${flag}" != "-D_DEBUG" ; then
          if test "${LLVM_CFLAGS}" != "" ; then
            LLVM_CFLAGS="${LLVM_CFLAGS} "
          fi
          LLVM_CFLAGS="${LLVM_CFLAGS}${flag}"
        fi
      fi
    done
    llvm_version=$("${LLVM_CONFIG}" --version | sed 's/\.//; s/svn.*//')
    LLVM_CFLAGS="${LLVM_CFLAGS} -DSHARK_LLVM_VERSION=${llvm_version}"

    unset LLVM_LDFLAGS
    for flag in $("${LLVM_CONFIG}" --ldflags); do
      if echo "${flag}" | grep -q '^-L'; then
        if test "${LLVM_LDFLAGS}" != ""; then
          LLVM_LDFLAGS="${LLVM_LDFLAGS} "
        fi
        LLVM_LDFLAGS="${LLVM_LDFLAGS}${flag}"
      fi
    done

    unset LLVM_LIBS
    for flag in $("${LLVM_CONFIG}" --libs ${llvm_components}); do
      if echo "${flag}" | grep -q '^-l'; then
        if test "${LLVM_LIBS}" != ""; then
          LLVM_LIBS="${LLVM_LIBS} "
        fi
        LLVM_LIBS="${LLVM_LIBS}${flag}"
      fi
    done

    AC_SUBST(LLVM_CFLAGS)
    AC_SUBST(LLVM_LDFLAGS)
    AC_SUBST(LLVM_LIBS)
  fi
])

################################################################################
# Setup various libraries, typically small system libraries
################################################################################
AC_DEFUN_ONCE([LIB_SETUP_MISC_LIBS],
[
  # Setup libm (the maths library)
  AC_CHECK_LIB(m, cos, [], [
      AC_MSG_NOTICE([Maths library was not found])
  ])
  LIBM=-lm
  AC_SUBST(LIBM)

  # Setup libdl (for dynamic library loading)
  save_LIBS="$LIBS"
  LIBS=""
  AC_CHECK_LIB(dl, dlopen)
  LIBDL="$LIBS"
  AC_SUBST(LIBDL)
  LIBS="$save_LIBS"

  # Deprecated libraries, keep the flags for backwards compatibility
  if test "x$OPENJDK_TARGET_OS" = "xwindows"; then
    BASIC_DEPRECATED_ARG_WITH([dxsdk])
    BASIC_DEPRECATED_ARG_WITH([dxsdk-lib])
    BASIC_DEPRECATED_ARG_WITH([dxsdk-include])
  fi

  # Control if libzip can use mmap. Available for purposes of overriding.
  LIBZIP_CAN_USE_MMAP=true
  AC_SUBST(LIBZIP_CAN_USE_MMAP)
])
