#
# Copyright (c) 2023, Arm Limited. All rights reserved.
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
#
# Setup vmath framework and check its dependences
#
AC_DEFUN_ONCE([LIB_SETUP_VMATH],
[
  AC_ARG_WITH(libsleef, [AS_HELP_STRING([--with-libsleef],
      [specify prefix directory for the libsleef library
      (expecting the libraries under PATH/lib and the headers under PATH/include)])])

  ENABLE_LIBSLEEF=false
  LIBVMATH_CFLAGS=
  LIBVMATH_LIBS=

  if test "x$NEEDS_LIB_SLEEF" = xfalse; then
    if test "x${with_libsleef}" != "x" &&
       test "x${with_libsleef}" != "xno"; then
      AC_MSG_WARN([[libsleef is not used, so --with-libsleef is ignored]])
    fi
  else
    LIBSLEEF_FOUND=no

    if test "x${with_libsleef}" = "xno"; then
      AC_MSG_NOTICE([libvmath will not be built, because its dependence libsleef is disabled])
    else
      if test "x${with_libsleef}" != "x" &&
         test "x${with_libsleef}" != "xyes"; then
        # Check the specified libsleef
        AC_MSG_CHECKING([for the specified LIBSLEEF])
        if test -e ${with_libsleef}/lib/libsleef.so &&
           test -e ${with_libsleef}/include/sleef.h; then
          LIBSLEEF_FOUND=yes
          LIBVMATH_LIBS="-L${with_libsleef}/lib"
          LIBVMATH_CFLAGS="-I${with_libsleef}/include"
          AC_MSG_RESULT([yes])
        else
          AC_MSG_RESULT([no])
          AC_MSG_ERROR([Could not locate libsleef.so or sleef.h in ${with_libsleef}])
        fi
      else
        # Check for the libsleef under system locations
        # Do not try pkg-config if we have a sysroot set.
        if test "x$SYSROOT" = "x" &&
           test "x${LIBSLEEF_FOUND}" = "xno"; then
          PKG_CHECK_MODULES([LIBSLEEF], [sleef], [LIBSLEEF_FOUND=yes], [LIBSLEEF_FOUND=no])
        fi
        if test "x${LIBSLEEF_FOUND}" = "xno"; then
          AC_CHECK_HEADERS([sleef.h],
              [LIBSLEEF_FOUND=yes],
              []
          )
        fi

        # Print error if user runs just with "--with-libsleef", but libsleef is not installed
        if test "x${with_libsleef}" = "xyes" &&
           test "x${LIBSLEEF_FOUND}" = "xno"; then
          HELP_MSG_MISSING_DEPENDENCY([sleef])
          AC_MSG_ERROR([Could not find libsleef! $HELP_MSG])
        fi
      fi

      if test "x${LIBSLEEF_FOUND}" = "xyes"; then
        ENABLE_LIBSLEEF=true
        LIBVMATH_LIBS="${LIBVMATH_LIBS} -lsleef"

        if test "x${OPENJDK_TARGET_CPU}" = "xaarch64"; then
          # Check the ARM SVE feature
          SVE_CFLAGS="-march=armv8-a+sve"

          AC_LANG_PUSH(C)
          OLD_CFLAGS="$CFLAGS"
          CFLAGS="$CFLAGS $SVE_CFLAGS"

          AC_MSG_CHECKING([if ARM SVE feature is supported])
          AC_COMPILE_IFELSE([AC_LANG_PROGRAM([#include <arm_sve.h>],
              [
                svint32_t r = svdup_n_s32(1);
                return 0;
              ])],
              [
                AC_MSG_RESULT([yes])
                LIBVMATH_CFLAGS="${LIBVMATH_CFLAGS} ${SVE_CFLAGS}"
              ],
              [ AC_MSG_RESULT([no]) ]
          )

          CFLAGS="$OLD_CFLAGS"
          AC_LANG_POP(C)
        fi

      fi

    fi

  fi

  AC_SUBST(ENABLE_LIBSLEEF)
  AC_SUBST(LIBVMATH_CFLAGS)
  AC_SUBST(LIBVMATH_LIBS)
])
