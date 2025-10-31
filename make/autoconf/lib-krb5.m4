#
# Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
# Setup krb5 (Kerberos 5)
################################################################################
AC_DEFUN_ONCE([LIB_SETUP_KRB5],
[
  AC_ARG_WITH(krb5, [AS_HELP_STRING([--with-krb5],
      [specify prefix directory for the krb5 package on Linux, or use "yes/no/auto" (default=auto)])])
  AC_ARG_WITH(krb5-include, [AS_HELP_STRING([--with-krb5-include],
      [specify directory for the krb5 include files on Linux])])
  AC_ARG_WITH(krb5-lib, [AS_HELP_STRING([--with-krb5-lib],
      [specify directory for the krb5 library on Linux])])

  KRB5_CFLAGS=
  KRB5_LIBS=
  ENABLE_LIBKRB5_LINUX=false

  if test "x$OPENJDK_TARGET_OS" != "xlinux" && test "x${with_krb5}" = "xyes"; then
    AC_MSG_ERROR([krb5 support is only available on Linux])
  else
    KRB5_FOUND=no

    if test "x${with_krb5}" != "x" && test "x${with_krb5}" != "xyes" && test "x${with_krb5}" != "xauto"; then
      # if a path was provided, use it
      if test "x${with_krb5}" != "x"; then
        AC_MSG_CHECKING([for krb5])
        KRB5_LIBS="-L${with_krb5}/lib -lkrb5 -lcom_err"
        KRB5_CFLAGS="-I${with_krb5}/include"
        KRB5_FOUND=yes
        AC_MSG_RESULT([${with_krb5}])
      fi
    fi

    if test "x${with_krb5_include}" != "x"; then
      AC_MSG_CHECKING([for krb5 includes])
      KRB5_CFLAGS="-I${with_krb5_include}"
      KRB5_FOUND=yes
      AC_MSG_RESULT([${with_krb5_include}])
    fi

    if test "x${with_krb5_lib}" != "x"; then
      AC_MSG_CHECKING([for krb5 libs])
      KRB5_LIBS="-L${with_krb5_lib} -lkrb5 -lcom_err"
      KRB5_FOUND=yes
      AC_MSG_RESULT([${with_krb5_lib}])
    fi

    if test "x$KRB5_FOUND" = "xno"; then
      if test "x$SYSROOT" != "x"; then
        AC_MSG_CHECKING([for krb5 ($SYSROOT)])
        # Cross-compilation with SYSROOT - look at known locations in SYSROOT.
        KRB5_LIB_PATH=""
        COM_ERR_LIB_PATH=""

        # Look for libkrb5/libcom_err
        if test -f "$SYSROOT/usr/lib64/libkrb5.so" && test "x$OPENJDK_TARGET_CPU_BITS" = x64; then
          KRB5_LIB_PATH="$SYSROOT/usr/lib64"
        elif test -f "$SYSROOT/usr/lib/libkrb5.so"; then
          KRB5_LIB_PATH="$SYSROOT/usr/lib"
        elif test -f "$SYSROOT/usr/lib/$OPENJDK_TARGET_CPU-$OPENJDK_TARGET_OS-$OPENJDK_TARGET_ABI/libkrb5.so"; then
          KRB5_LIB_PATH="$SYSROOT/usr/lib/$OPENJDK_TARGET_CPU-$OPENJDK_TARGET_OS-$OPENJDK_TARGET_ABI"
        elif test -f "$SYSROOT/usr/lib/$OPENJDK_TARGET_CPU_AUTOCONF-$OPENJDK_TARGET_OS-$OPENJDK_TARGET_ABI/libkrb5.so"; then
          KRB5_LIB_PATH="$SYSROOT/usr/lib/$OPENJDK_TARGET_CPU_AUTOCONF-$OPENJDK_TARGET_OS-$OPENJDK_TARGET_ABI"
        fi

        if test -f "$KRB5_LIB_PATH/libcom_err.so"; then
          COM_ERR_LIB_PATH="$KRB5_LIB_PATH"
        elif test -f "$SYSROOT/usr/lib64/libcom_err.so" && test "x$OPENJDK_TARGET_CPU_BITS" = x64; then
          COM_ERR_LIB_PATH="$SYSROOT/usr/lib64"
        elif test -f "$SYSROOT/usr/lib/libcom_err.so"; then
          COM_ERR_LIB_PATH="$SYSROOT/usr/lib"
        elif test -f "$SYSROOT/usr/lib/$OPENJDK_TARGET_CPU-$OPENJDK_TARGET_OS-$OPENJDK_TARGET_ABI/libcom_err.so"; then
          COM_ERR_LIB_PATH="$SYSROOT/usr/lib/$OPENJDK_TARGET_CPU-$OPENJDK_TARGET_OS-$OPENJDK_TARGET_ABI"
        elif test -f "$SYSROOT/usr/lib/$OPENJDK_TARGET_CPU_AUTOCONF-$OPENJDK_TARGET_OS-$OPENJDK_TARGET_ABI/libcom_err.so"; then
          COM_ERR_LIB_PATH="$SYSROOT/usr/lib/$OPENJDK_TARGET_CPU_AUTOCONF-$OPENJDK_TARGET_OS-$OPENJDK_TARGET_ABI"
        fi

        # Check for matching include files
        KRB5_INCLUDE_PATH=""
        COM_ERR_INCLUDE_PATH=""

        if test -f "$SYSROOT/usr/include/krb5/krb5.h"; then
          KRB5_INCLUDE_PATH="$SYSROOT/usr/include"
        fi

        if test -f "$SYSROOT/usr/include/com_err.h"; then
          COM_ERR_INCLUDE_PATH="$SYSROOT/usr/include"
        fi

        # Check everything was found and merge paths
        if test "x$KRB5_LIB_PATH" != "x" && test "x$COM_ERR_LIB_PATH" != "x" && \
             test "x$KRB5_INCLUDE_PATH" != "x" && test "x$COM_ERR_INCLUDE_PATH" != "x"; then
          KRB5_LIBS="-L$KRB5_LIB_PATH -lkrb5"
          if test "x$COM_ERR_LIB_PATH" != "x" && test "x$COM_ERR_LIB_PATH" != "x$KRB5_LIB_PATH"; then
            KRB5_LIBS="$KRB5_LIBS -L$COM_ERR_LIB_PATH"
          fi
          KRB5_LIBS="$KRB5_LIBS -lcom_err"

          KRB5_CFLAGS="-I$KRB5_INCLUDE_PATH"
          if test "x$COM_ERR_INCLUDE_PATH" != "x" && test "x$COM_ERR_INCLUDE_PATH" != "x$KRB5_INCLUDE_PATH"; then
            KRB5_CFLAGS="$KRB5_CFLAGS -I$COM_ERR_INCLUDE_PATH"
          fi

          KRB5_FOUND=yes
        fi
        AC_MSG_RESULT([$KRB5_FOUND])
      else
        PKG_CHECK_MODULES(KRB5, krb5, [KRB5_FOUND=yes], [KRB5_FOUND=no])
        if test "x$KRB5_FOUND" = "xno"; then
          UTIL_LOOKUP_PROGS(KRB5CONF, krb5-config)
          if test "x$KRB5CONF" != "x"; then
            AC_MSG_CHECKING([for krb5 using krb5-config])
            KRB5_CFLAGS="`$KRB5CONF --cflags`"
            KRB5_LIBS="`$KRB5CONF --libs`"
            KRB5_FOUND=yes
            AC_MSG_RESULT([$KRB5_FOUND])
          fi
        fi
      fi
    fi

    # No sysconfig/pkg-config/krb5-config, so auto-detect
    if test "x$KRB5_FOUND" = "xno"; then
      AC_CHECK_HEADERS([krb5.h], [
        AC_CHECK_HEADERS([com_err.h], [
          AC_CHECK_LIB([krb5], [krb5_init_context], [
            KRB5_CFLAGS=""
            KRB5_LIBS="-lkrb5"
            AC_CHECK_LIB([com_err], [com_err], [
              KRB5_LIBS="$KRB5_LIBS -lcom_err"
            ])
            KRB5_FOUND=yes
          ])
        ])
      ])
    fi

    if test "x$KRB5_FOUND" = "xno"; then
      if test "x${with_krb5}" = "xyes"; then
        AC_MSG_ERROR([krb5 was required but could not be found])
      fi
      KRB5_CFLAGS=
      KRB5_LIBS=
      ENABLE_LIBKRB5_LINUX=false
    else
      ENABLE_LIBKRB5_LINUX=true
    fi
  fi

  AC_SUBST(KRB5_CFLAGS)
  AC_SUBST(KRB5_LIBS)
  AC_SUBST(ENABLE_LIBKRB5_LINUX)
])