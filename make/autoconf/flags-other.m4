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

################################################################################
#
# Setup flags for other tools than C/C++ compiler
#

AC_DEFUN([FLAGS_SETUP_ARFLAGS],
[
  # FIXME: figure out if we should select AR flags depending on OS or toolchain.
  if test "x$OPENJDK_TARGET_OS" = xaix; then
    ARFLAGS="-X64"
  else
    ARFLAGS=""
  fi

  AC_SUBST(ARFLAGS)
])

AC_DEFUN([FLAGS_SETUP_LIBFLAGS],
[
  # LIB is used to create static libraries on Windows
  if test "x$OPENJDK_TARGET_OS" = xwindows; then
    LIBFLAGS="-nodefaultlib:msvcrt"
  else
    LIBFLAGS=""
  fi

  AC_SUBST(LIBFLAGS)
])

AC_DEFUN([FLAGS_SETUP_STRIPFLAGS],
[
  ## Setup strip.
  if test "x$STRIP" != x; then
    AC_MSG_CHECKING([how to run strip])

    # Easy cheat: Check strip variant by passing --version as an argument.
    # Different types of strip have varying command line syntaxes for querying their
    # version string, and all noisily fail if the provided version option is not
    # recognised.
    #
    # The actual version string or failure to execute strip are hidden by redirection
    # to config.log with 2>&AS_MESSAGE_LOG_FD >&AS_MESSAGE_LOG_FD

    if $STRIP "--version" 2>&AS_MESSAGE_LOG_FD >&AS_MESSAGE_LOG_FD; then
      # strip that comes from the GNU family uses --version
      # This variant of strip is usually found accompanying gcc and clang
      STRIPFLAGS="--strip-debug"
    elif $STRIP "-V" 2>&AS_MESSAGE_LOG_FD >&AS_MESSAGE_LOG_FD; then
      # IBM strip that works with AIX binaries only supports -V
      STRIPFLAGS="-X32_64"
    else
      # The only strip variant left is MacOS/Xcode strip, which does not have any
      # way whatsoever to be identified (lacking even basic help or version options),
      # so we leave it as the last fallback when all other tests have failed.
      STRIPFLAGS="-S"
    fi
    AC_MSG_RESULT($STRIPFLAGS)
  fi

  AC_SUBST(STRIPFLAGS)
])

AC_DEFUN([FLAGS_SETUP_RCFLAGS],
[
  # On Windows, we need to set RC flags.
  if test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    RCFLAGS="-nologo"
    if test "x$DEBUG_LEVEL" = xrelease; then
      RCFLAGS="$RCFLAGS -DNDEBUG"
    fi
  elif test "x$TOOLCHAIN_TYPE" = xgcc && test "x$OPENJDK_TARGET_OS" = xwindows; then
    if test "x$DEBUG_LEVEL" = xrelease; then
      RCFLAGS="$RCFLAGS -DNDEBUG"
    fi
  fi
  AC_SUBST(RCFLAGS)
])

AC_DEFUN([FLAGS_SETUP_NMFLAGS],
[
  # On AIX, we need to set NM flag -X64 for processing 64bit object files
  if test "x$OPENJDK_TARGET_OS" = xaix; then
    NMFLAGS="-X64"
  fi

  AC_SUBST(NMFLAGS)
])

################################################################################
# platform independent
AC_DEFUN([FLAGS_SETUP_ASFLAGS],
[
  if test "x$TOOLCHAIN_TYPE" = xgcc || test "x$TOOLCHAIN_TYPE" = xclang; then
    # Force preprocessor to run, just to make sure
    BASIC_ASFLAGS="-x assembler-with-cpp"
  elif test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    BASIC_ASFLAGS="-nologo -c"
  fi
  AC_SUBST(BASIC_ASFLAGS)

  if test "x$OPENJDK_TARGET_OS" = xmacosx; then
    JVM_BASIC_ASFLAGS="-mno-omit-leaf-frame-pointer -mstack-alignment=16"

    # Fix linker warning.
    # Code taken from make/autoconf/flags-cflags.m4 and adapted.
    JVM_BASIC_ASFLAGS="$JVM_BASIC_ASFLAGS \
        -DMAC_OS_X_VERSION_MIN_REQUIRED=$MACOSX_VERSION_MIN_NODOTS \
        -mmacosx-version-min=$MACOSX_VERSION_MIN"

    if test -n "$MACOSX_VERSION_MAX"; then
        JVM_BASIC_ASFLAGS="$JVM_BASIC_ASFLAGS $OS_CFLAGS \
            -DMAC_OS_X_VERSION_MAX_ALLOWED=$MACOSX_VERSION_MAX_NODOTS"
    fi
  fi
])

################################################################################
# $1 - Either BUILD or TARGET to pick the correct OS/CPU variables to check
#      conditionals against.
# $2 - Optional prefix for each variable defined.
AC_DEFUN([FLAGS_SETUP_ASFLAGS_CPU_DEP],
[
  # Misuse EXTRA_CFLAGS to mimic old behavior
  $2JVM_ASFLAGS="$JVM_BASIC_ASFLAGS ${$2EXTRA_CFLAGS}"

  if test "x$1" = "xTARGET" && \
      test "x$TOOLCHAIN_TYPE" = xgcc && \
      test "x$OPENJDK_TARGET_CPU" = xarm; then
    $2JVM_ASFLAGS="${$2JVM_ASFLAGS} $ARM_ARCH_TYPE_ASFLAGS $ARM_FLOAT_TYPE_ASFLAGS"
  fi

  AC_SUBST($2JVM_ASFLAGS)
])
