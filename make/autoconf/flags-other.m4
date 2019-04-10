#
# Copyright (c) 2011, 2019, Oracle and/or its affiliates. All rights reserved.
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
  elif test "x$OPENJDK_TARGET_OS" = xwindows; then
    # lib.exe is used as AR to create static libraries.
    ARFLAGS="-nologo -NODEFAULTLIB:MSVCRT"
  else
    ARFLAGS=""
  fi

  AC_SUBST(ARFLAGS)
])

AC_DEFUN([FLAGS_SETUP_STRIPFLAGS],
[
  ## Setup strip.
  # FIXME: should this really be per platform, or should it be per toolchain type?
  # strip is not provided by clang or solstudio; so guessing platform makes most sense.
  # FIXME: we should really only export STRIPFLAGS from here, not POST_STRIP_CMD.
  if test "x$OPENJDK_TARGET_OS" = xlinux; then
    STRIPFLAGS="-g"
  elif test "x$OPENJDK_TARGET_OS" = xsolaris; then
    STRIPFLAGS="-x"
  elif test "x$OPENJDK_TARGET_OS" = xmacosx; then
    STRIPFLAGS="-S"
  elif test "x$OPENJDK_TARGET_OS" = xaix; then
    STRIPFLAGS="-X32_64"
  fi

  AC_SUBST(STRIPFLAGS)
])

AC_DEFUN([FLAGS_SETUP_RCFLAGS],
[
  # On Windows, we need to set RC flags.
  if test "x$TOOLCHAIN_TYPE" = xmicrosoft; then
    RC_FLAGS="-nologo -l0x409"
    JVM_RCFLAGS="-nologo"
    if test "x$DEBUG_LEVEL" = xrelease; then
      RC_FLAGS="$RC_FLAGS -DNDEBUG"
      JVM_RCFLAGS="$JVM_RCFLAGS -DNDEBUG"
    fi

    # The version variables used to create RC_FLAGS may be overridden
    # in a custom configure script, or possibly the command line.
    # Let those variables be expanded at make time in spec.gmk.
    # The \$ are escaped to the shell, and the $(...) variables
    # are evaluated by make.
    RC_FLAGS="$RC_FLAGS \
        -D\"JDK_VERSION_STRING=\$(VERSION_STRING)\" \
        -D\"JDK_COMPANY=\$(COMPANY_NAME)\" \
        -D\"JDK_COMPONENT=\$(JDK_RC_NAME) binary\" \
        -D\"JDK_VER=\$(VERSION_NUMBER)\" \
        -D\"JDK_COPYRIGHT=Copyright \xA9 $COPYRIGHT_YEAR\" \
        -D\"JDK_NAME=\$(JDK_RC_NAME) \$(VERSION_FEATURE)\" \
        -D\"JDK_FVER=\$(subst .,\$(COMMA),\$(VERSION_NUMBER_FOUR_POSITIONS))\""

    JVM_RCFLAGS="$JVM_RCFLAGS \
        -D\"HS_BUILD_ID=\$(VERSION_STRING)\" \
        -D\"HS_COMPANY=\$(COMPANY_NAME)\" \
        -D\"JDK_DOTVER=\$(VERSION_NUMBER_FOUR_POSITIONS)\" \
        -D\"HS_COPYRIGHT=Copyright $COPYRIGHT_YEAR\" \
        -D\"HS_NAME=\$(PRODUCT_NAME) \$(VERSION_SHORT)\" \
        -D\"JDK_VER=\$(subst .,\$(COMMA),\$(VERSION_NUMBER_FOUR_POSITIONS))\" \
        -D\"HS_FNAME=jvm.dll\" \
        -D\"HS_INTERNAL_NAME=jvm\""
  fi
  AC_SUBST(RC_FLAGS)
  AC_SUBST(JVM_RCFLAGS)
])

################################################################################
# platform independent
AC_DEFUN([FLAGS_SETUP_ASFLAGS],
[
  if test "x$OPENJDK_TARGET_OS" = xmacosx; then
    JVM_BASIC_ASFLAGS="-x assembler-with-cpp -mno-omit-leaf-frame-pointer -mstack-alignment=16"
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

