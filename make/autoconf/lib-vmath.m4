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

  LIBSLEEF_FOUND=no
  LIBVMATH_CFLAGS=
  LIBVMATH_LIBS=

  if test "x${with_libsleef}" = xno; then
    AC_MSG_NOTICE([libvmath will not be compiled, because its dependence libsleef is disabled in command line])
  else
    # Check the specified libsleef.so
    if test "x${with_libsleef}" != x; then
      AC_MSG_CHECKING([the specified LIBSLEEF])
      if test -e ${with_libsleef}/lib/libsleef.so &&
         test -e ${with_libsleef}/include/sleef.h; then
        LIBSLEEF_FOUND=yes
        LIBVMATH_LIBS="-L${with_libsleef}/lib"
        LIBVMATH_CFLAGS="-I${with_libsleef}/include"
      else
        AC_MSG_ERROR([Could not locate libsleef.so or sleef.h in ${with_libsleef}])
      fi
      AC_MSG_RESULT([${LIBSLEEF_FOUND}])
    fi

    # Check the system locations if libsleef is not specified with option
    if test "x$SYSROOT" = x && test "x${LIBSLEEF_FOUND}" = "xno"; then
      PKG_CHECK_MODULES([LIBSLEEF], [sleef], [LIBSLEEF_FOUND=yes], [LIBSLEEF_FOUND=no])
    fi
    if test "x$LIBSLEEF_FOUND" = xno; then
      AC_CHECK_HEADERS([sleef.h],
          [LIBSLEEF_FOUND=yes],
          []
      )
    fi

    if test "x${LIBSLEEF_FOUND}" = "xyes"; then
      LIBVMATH_LIBS="${LIBVMATH_LIBS} -lsleef"

      if test "x${OPENJDK_TARGET_CPU}" = "xaarch64"; then
        # Check the ARM SVE feature
        SVE_FEATURE_SUPPORT=no
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
              SVE_FEATURE_SUPPORT=yes
              LIBVMATH_CFLAGS="${LIBVMATH_CFLAGS} ${SVE_CFLAGS}"
            ],
            []
        )
        AC_MSG_RESULT([${SVE_FEATURE_SUPPORT}])

        CFLAGS="$OLD_CFLAGS"
        AC_LANG_POP(C)
      fi

    fi

  fi

  AC_SUBST(LIBSLEEF_FOUND)
  AC_SUBST(LIBVMATH_CFLAGS)
  AC_SUBST(LIBVMATH_LIBS)
])
