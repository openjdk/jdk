#
# Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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
      [specify prefix directory for the krb5 package
      (expecting libkrb5 under PATH/lib and headers under PATH/include)])])
  AC_ARG_WITH(krb5-include, [AS_HELP_STRING([--with-krb5-include],
      [specify directory for the krb5 include files])])
  AC_ARG_WITH(krb5-lib, [AS_HELP_STRING([--with-krb5-lib],
      [specify directory for the krb5 library])])
  AC_ARG_WITH(com-err, [AS_HELP_STRING([--with-com-err],
      [specify prefix directory for the com_err package
      (expecting libcom_err under PATH/lib and headers under PATH/include). Note: com_err is often packaged separately from krb5])])
  AC_ARG_WITH(com-err-include, [AS_HELP_STRING([--with-com-err-include],
      [specify directory for the com_err include files])])
  AC_ARG_WITH(com-err-lib, [AS_HELP_STRING([--with-com-err-lib],
      [specify directory for the com_err library])])

  # Determine if krb5 should be disabled
  KRB5_DISABLED=no
  if test "x${with_krb5}" = xno || test "x${with_krb5_include}" = xno || test "x${with_krb5_lib}" = xno || test "x${with_com_err}" = xno || test "x${with_com_err_include}" = xno || test "x${with_com_err_lib}" = xno; then
    AC_MSG_NOTICE([krb5 explicitly disabled])
    KRB5_DISABLED=yes
  elif test "x$NEEDS_LIB_KRB5" = xfalse; then
    if (test "x${with_krb5}" != x && test "x${with_krb5}" != xno) || \
        (test "x${with_krb5_include}" != x && test "x${with_krb5_include}" != xno) || \
        (test "x${with_krb5_lib}" != x && test "x${with_krb5_lib}" != xno) || \
        (test "x${with_com_err}" != x && test "x${with_com_err}" != xno) || \
        (test "x${with_com_err_include}" != x && test "x${with_com_err_include}" != xno) || \
        (test "x${with_com_err_lib}" != x && test "x${with_com_err_lib}" != xno); then
      AC_MSG_WARN([[krb5 not used, so --with-krb5[-*] and --with-com-err[-*] are ignored]])
    fi
    KRB5_DISABLED=yes
  fi

  if test "x$KRB5_DISABLED" = xyes; then
    KRB5_CFLAGS=
    KRB5_LIBS=
    COM_ERR_CFLAGS=
    KRB5_INCLUDES_FOUND=no
    COM_ERR_INCLUDES_FOUND=no
    KRB5_LIBS_FOUND=no
    COM_ERR_LIBS_FOUND=no
    ENABLE_LIBLINUXKRB5=false
  else
    KRB5_INCLUDES_FOUND=no
    COM_ERR_INCLUDES_FOUND=no
    KRB5_LIBS_FOUND=no
    COM_ERR_LIBS_FOUND=no

    # Initialize library and include paths
    KRB5_LIB_PATH=""
    KRB5_CFLAGS=""
    COM_ERR_LIB_PATH=""
    COM_ERR_CFLAGS=""

    if test "x${with_krb5}" != x; then
      AC_MSG_CHECKING([for krb5 headers])
      if test -s "${with_krb5}/include/krb5/krb5.h"; then
        KRB5_CFLAGS="-I${with_krb5}/include"
        KRB5_INCLUDES_FOUND=yes
        AC_MSG_RESULT([$KRB5_INCLUDES_FOUND])

        # Check for libkrb5 in the specified path
        AC_MSG_CHECKING([for libkrb5])
        if test -f "${with_krb5}/lib/libkrb5.so"; then
          KRB5_LIB_PATH="${with_krb5}/lib"
          KRB5_LIBS_FOUND=yes
          AC_MSG_RESULT([yes])
        else
          AC_MSG_ERROR([Can't find libkrb5 under ${with_krb5}/lib given with the --with-krb5 option.])
        fi
      else
        AC_MSG_ERROR([Can't find 'include/krb5/krb5.h' under ${with_krb5} given with the --with-krb5 option.])
      fi
    fi

    if test "x${with_krb5_include}" != x; then
      AC_MSG_CHECKING([for krb5 headers])
      if test -s "${with_krb5_include}/krb5/krb5.h"; then
        KRB5_CFLAGS="-I${with_krb5_include}"
        KRB5_INCLUDES_FOUND=yes
        AC_MSG_RESULT([$KRB5_INCLUDES_FOUND])
      else
        AC_MSG_ERROR([Can't find 'krb5/krb5.h' under ${with_krb5_include} given with the --with-krb5-include option.])
      fi
    fi

    if test "x${with_krb5_lib}" != x; then
      AC_MSG_CHECKING([for libkrb5])
      if test -f "${with_krb5_lib}/libkrb5.so" || test -f "${with_krb5_lib}/libkrb5.a"; then
        KRB5_LIB_PATH="${with_krb5_lib}"
        KRB5_LIBS_FOUND=yes
        AC_MSG_RESULT([yes])
      else
        AC_MSG_ERROR([Can't find libkrb5 under ${with_krb5_lib} given with the --with-krb5-lib option.])
      fi
    fi

    if test "x${with_com_err}" != x; then
      AC_MSG_CHECKING([for com_err headers])
      if test -s "${with_com_err}/include/et/com_err.h"; then
        COM_ERR_CFLAGS="-I${with_com_err}/include"
        COM_ERR_INCLUDES_FOUND=yes
        AC_MSG_RESULT([$COM_ERR_INCLUDES_FOUND])

        # Check for libcom_err in the specified path
        AC_MSG_CHECKING([for libcom_err])
        if test -f "${with_com_err}/lib/libcom_err.so"; then
          COM_ERR_LIB_PATH="${with_com_err}/lib"
          COM_ERR_LIBS_FOUND=yes
          AC_MSG_RESULT([yes])
        else
          AC_MSG_ERROR([Can't find libcom_err under ${with_com_err}/lib given with the --with-com-err option.])
        fi
      else
        AC_MSG_ERROR([Can't find 'include/et/com_err.h' under ${with_com_err} given with the --with-com-err option.])
      fi
    fi

    if test "x${with_com_err_include}" != x; then
      AC_MSG_CHECKING([for com_err headers])
      if test -s "${with_com_err_include}/et/com_err.h"; then
        COM_ERR_CFLAGS="-I${with_com_err_include}"
        COM_ERR_INCLUDES_FOUND=yes
        AC_MSG_RESULT([$COM_ERR_INCLUDES_FOUND])
      else
        AC_MSG_ERROR([Can't find 'et/com_err.h' under ${with_com_err_include} given with the --with-com-err-include option.])
      fi
    fi

    if test "x${with_com_err_lib}" != x; then
      AC_MSG_CHECKING([for libcom_err])
      if test -f "${with_com_err_lib}/libcom_err.so" || test -f "${with_com_err_lib}/libcom_err.a"; then
        COM_ERR_LIB_PATH="${with_com_err_lib}"
        COM_ERR_LIBS_FOUND=yes
        AC_MSG_RESULT([yes])
      else
        AC_MSG_ERROR([Can't find libcom_err under ${with_com_err_lib} given with the --with-com-err-lib option.])
      fi
    fi

    # Check for krb5 headers if not already found
    if test "x$KRB5_INCLUDES_FOUND" = xno; then
      AC_CHECK_HEADERS([krb5/krb5.h],
          [
            KRB5_INCLUDES_FOUND=yes
            KRB5_CFLAGS=""
            DEFAULT_KRB5=yes
          ],
          [KRB5_INCLUDES_FOUND=no]
      )
    fi

    # Check for com_err headers if not already found
    if test "x$COM_ERR_INCLUDES_FOUND" = xno; then
      AC_CHECK_HEADERS([et/com_err.h],
          [
            COM_ERR_INCLUDES_FOUND=yes
            COM_ERR_CFLAGS=""
            DEFAULT_COM_ERR=yes
          ],
          [COM_ERR_INCLUDES_FOUND=no]
      )
    fi

    # Check for krb5 library if not already found
    if test "x$KRB5_LIBS_FOUND" = xno; then
      AC_CHECK_LIB([krb5], [krb5_init_context],
          [KRB5_LIBS_FOUND=yes],
          [KRB5_LIBS_FOUND=no]
      )
    fi

    # Check for com_err library if not already found
    if test "x$COM_ERR_LIBS_FOUND" = xno; then
      AC_CHECK_LIB([com_err], [com_err],
          [COM_ERR_LIBS_FOUND=yes],
          [COM_ERR_LIBS_FOUND=no]
      )
    fi

    # Check that we have all required components
    if test "x$KRB5_INCLUDES_FOUND" = xno || test "x$COM_ERR_INCLUDES_FOUND" = xno || test "x$KRB5_LIBS_FOUND" = xno || test "x$COM_ERR_LIBS_FOUND" = xno; then
      if test "x$KRB5_INCLUDES_FOUND" = xno; then
        AC_MSG_ERROR([Could not find krb5 headers! Please install krb5-devel or specify --with-krb5-include.])
      fi
      if test "x$COM_ERR_INCLUDES_FOUND" = xno; then
        AC_MSG_ERROR([Could not find com_err headers! Please install com_err-devel or specify --with-com-err-include.])
      fi
      if test "x$KRB5_LIBS_FOUND" = xno; then
        AC_MSG_ERROR([Could not find libkrb5! Please install krb5-libs or specify --with-krb5-lib.])
      fi
      if test "x$COM_ERR_LIBS_FOUND" = xno; then
        AC_MSG_ERROR([Could not find libcom_err! Please install com_err library or specify --with-com-err-lib.])
      fi
    fi

    # Combine CFLAGS from both krb5 and com_err
    if test "x$COM_ERR_CFLAGS" != x; then
      if test "x$KRB5_CFLAGS" != x; then
        KRB5_CFLAGS="$KRB5_CFLAGS $COM_ERR_CFLAGS"
      else
        KRB5_CFLAGS="$COM_ERR_CFLAGS"
      fi
    fi

    # Build the final KRB5_LIBS from separate library locations
    KRB5_LIBS=""
    if test "x$KRB5_LIB_PATH" != x; then
      KRB5_LIBS="$KRB5_LIBS -L$KRB5_LIB_PATH"
    fi
    KRB5_LIBS="$KRB5_LIBS -lkrb5"
    if test "x$COM_ERR_LIB_PATH" != x && test "x$COM_ERR_LIB_PATH" != "x$KRB5_LIB_PATH"; then
      KRB5_LIBS="$KRB5_LIBS -L$COM_ERR_LIB_PATH"
    fi
    KRB5_LIBS="$KRB5_LIBS -lcom_err"

    ENABLE_LIBLINUXKRB5=true
  fi

  AC_SUBST(KRB5_CFLAGS)
  AC_SUBST(KRB5_LIBS)
  AC_SUBST(ENABLE_LIBLINUXKRB5)
])
