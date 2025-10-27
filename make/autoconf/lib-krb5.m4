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
      [enable krb5 support (default=yes), or "no" to disable])])

  # Determine if krb5 should be disabled
  KRB5_DISABLED=no
  if test "x${with_krb5}" = xno; then
    AC_MSG_NOTICE([krb5 explicitly disabled])
    KRB5_DISABLED=yes
  elif test "x$NEEDS_LIB_KRB5" = xfalse; then
    if test "x${with_krb5}" != x && test "x${with_krb5}" != xno; then
      AC_MSG_WARN([[krb5 not used, so --with-krb5 is ignored]])
    fi
    KRB5_DISABLED=yes
  fi

  if test "x$KRB5_DISABLED" = xyes; then
    KRB5_CFLAGS=
    KRB5_LIBS=
    ENABLE_LIBLINUXKRB5=false
  else
    # First try pkg-config (most modern approach)
    AC_PATH_TOOL([PKG_CONFIG], [pkg-config], [no])
    use_pkgconfig_for_krb5=no

    if test "x$PKG_CONFIG" != "xno"; then
      AC_MSG_CHECKING([if pkg-config knows about krb5])
      if $PKG_CONFIG --exists krb5; then
        AC_MSG_RESULT([yes])
        use_pkgconfig_for_krb5=yes
      else
        AC_MSG_RESULT([no])
      fi
    fi

    if test "x$use_pkgconfig_for_krb5" = "xyes"; then
      # Use pkg-config to get compiler and linker flags
      AC_MSG_CHECKING([for krb5 cflags via pkg-config])
      KRB5_CFLAGS="`$PKG_CONFIG --cflags krb5`"
      AC_MSG_RESULT([$KRB5_CFLAGS])

      AC_MSG_CHECKING([for krb5 libs via pkg-config])
      KRB5_LIBS="`$PKG_CONFIG --libs krb5`"
      AC_MSG_RESULT([$KRB5_LIBS])

      ENABLE_LIBLINUXKRB5=true
    else
      # Fallback: try krb5-config
      AC_PATH_TOOL([KRB5CONF], [krb5-config], [no])

      if test "x$KRB5CONF" != "xno"; then
        # Use krb5-config to get compiler and linker flags
        AC_MSG_CHECKING([for krb5 cflags via krb5-config])
        KRB5_CFLAGS="`$KRB5CONF --cflags`"
        AC_MSG_RESULT([$KRB5_CFLAGS])

        AC_MSG_CHECKING([for krb5 libs via krb5-config])
        KRB5_LIBS="`$KRB5CONF --libs`"
        AC_MSG_RESULT([$KRB5_LIBS])

        ENABLE_LIBLINUXKRB5=true
      else
        # Final fallback: try manual detection in system locations
        AC_CHECK_HEADERS([krb5.h], [
          AC_CHECK_LIB([krb5], [krb5_init_context], [
            KRB5_CFLAGS=""
            KRB5_LIBS="-lkrb5"
            # Check for com_err header and library which are often required
            AC_CHECK_HEADERS([com_err.h], [
              AC_CHECK_LIB([com_err], [com_err], [
                KRB5_LIBS="$KRB5_LIBS -lcom_err"
              ])
            ])
            ENABLE_LIBLINUXKRB5=true
          ], [ENABLE_LIBLINUXKRB5=false])
        ], [ENABLE_LIBLINUXKRB5=false])
      fi
    fi
  fi

  AC_SUBST(KRB5_CFLAGS)
  AC_SUBST(KRB5_LIBS)
  AC_SUBST(ENABLE_LIBLINUXKRB5)
])
