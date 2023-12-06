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
# Setup sleef framework
#
AC_DEFUN_ONCE([LIB_SETUP_SLEEF],
[
  AC_ARG_WITH(libsleef, [AS_HELP_STRING([--with-libsleef],
      [specify prefix directory for the libsleef library
      (expecting the libraries under PATH/lib and the headers under PATH/include)])])
  AC_ARG_WITH(libsleef-include, [AS_HELP_STRING([--with-libsleef-include],
      [specify directory for the libsleef include files
      (should be used together with --with-libsleef-lib)])])
  AC_ARG_WITH(libsleef-lib, [AS_HELP_STRING([--with-libsleef-lib],
      [specify directory for the libsleef library
      (should be used together with --with-libsleef-include)])])

  ENABLE_LIBSLEEF=false
  LIBSLEEF_CFLAGS=
  LIBSLEEF_LIBS=

  if test "x${NEEDS_LIB_SLEEF}" = xfalse; then
    if (test "x${with_libsleef}" != x && test "x${with_libsleef}" != xno) || \
       (test "x${with_libsleef_include}" != x && test "x${with_libsleef_include}" != xno) || \
       (test "x${with_libsleef_lib}" != x && test "x${with_libsleef_lib}" != xno); then
      AC_MSG_WARN([libsleef is not used, so --with-libsleef[-*] is ignored])
    fi
  elif test "x${with_libsleef}" = "xno" ||
       test "x${with_libsleef_include}" = "xno" ||
       test "x${with_libsleef_lib}" = "xno"; then
    AC_MSG_NOTICE([libsleef is disabled])
  elif test "x${with_libsleef_lib}" = "xyes" ||
       test "x${with_libsleef_include}" = "xyes"; then
    AC_MSG_ERROR([Must specify a value for --with-libsleef-*])
  elif (test "x${with_libsleef_lib}" != "x" && test "x${with_libsleef_include}" = "x") ||
       (test "x${with_libsleef_include}" != "x" && test "x${with_libsleef_lib}" = "x"); then
    AC_MSG_ERROR([--with-libsleef-lib and --with-libsleef-include should be specified together])
  else
    LIBSLEEF_FOUND=no

    # Check the specified libsleef
    if test "x${with_libsleef}" != "x" &&
       test "x${with_libsleef}" != "xyes"; then
      AC_MSG_CHECKING([for the specified LIBSLEEF])
      if test -e ${with_libsleef}/lib/libsleef.so &&
         test -e ${with_libsleef}/include/sleef.h; then
        LIBSLEEF_LIBS="-L${with_libsleef}/lib -lsleef"
        LIBSLEEF_CFLAGS="-I${with_libsleef}/include"
        AC_MSG_RESULT([yes])
      else
        AC_MSG_RESULT([no])
        AC_MSG_ERROR([Could not locate libsleef.so or sleef.h in ${with_libsleef}])
      fi
    fi
    if test "x${with_libsleef_lib}" != "x"; then
      AC_MSG_CHECKING([for the specified LIBSLEEF lib])
      if test -e ${with_libsleef_lib}/libsleef.so; then
        LIBSLEEF_LIBS="-L${with_libsleef_lib} -lsleef"
        AC_MSG_RESULT([yes])
      else
        AC_MSG_RESULT([no])
        AC_MSG_ERROR([Could not locate libsleef.so in ${with_libsleef_lib}])
      fi
    fi
    if test "x${with_libsleef_include}" != "x"; then
      AC_MSG_CHECKING([for the specified LIBSLEEF header])
      if test -e ${with_libsleef_include}/sleef.h; then
        LIBSLEEF_CFLAGS="-I${with_libsleef_include}"
        AC_MSG_RESULT([yes])
      else
        AC_MSG_RESULT([no])
        AC_MSG_ERROR([Could not locate sleef.h in ${with_libsleef_include}])
      fi
    fi

    # Check whether LIBSLEEF_CFLAGS and LIBSLEEF_LIBS are both set.
    # Set LIBSLEEF_FOUND to "yes" if both of them are set.
    if test "x${LIBSLEEF_CFLAGS}" != "x" &&
       test "x${LIBSLEEF_LIBS}" != "x"; then
      LIBSLEEF_FOUND=yes
    fi

    # Check for the libsleef under system locations
    # Do not try pkg-config if we have a sysroot set.
    if test "x$SYSROOT" = "x" && test "x${LIBSLEEF_FOUND}" = "xno"; then
      PKG_CHECK_MODULES([LIBSLEEF], [sleef], [LIBSLEEF_FOUND=yes], [LIBSLEEF_FOUND=no])
    fi
    if test "x${LIBSLEEF_FOUND}" = "xno"; then
      AC_CHECK_HEADERS([sleef.h],
          [
            LIBSLEEF_FOUND=yes
            LIBSLEEF_LIBS="-lsleef"
          ],
          []
      )
    fi

    # Print error if user runs just with "--with-libsleef",
    # but libsleef is not installed.
    if test "x${with_libsleef}" = "xyes" &&
       test "x${LIBSLEEF_FOUND}" = "xno"; then
      HELP_MSG_MISSING_DEPENDENCY([sleef])
      AC_MSG_ERROR([Could not find libsleef! $HELP_MSG])
    fi

    if test "x${LIBSLEEF_FOUND}" = "xyes"; then
      ENABLE_LIBSLEEF=true
    fi

  fi

  AC_SUBST(ENABLE_LIBSLEEF)
  AC_SUBST(LIBSLEEF_CFLAGS)
  AC_SUBST(LIBSLEEF_LIBS)
])
