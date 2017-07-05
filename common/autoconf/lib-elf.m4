#
# Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
# Setup libelf (ELF library)
################################################################################
AC_DEFUN_ONCE([LIB_SETUP_LIBELF],
[
  AC_ARG_WITH(libelf, [AS_HELP_STRING([--with-libelf],
      [specify prefix directory for the libelf package
      (expecting the libraries under PATH/lib and the headers under PATH/include)])])
  AC_ARG_WITH(libelf-include, [AS_HELP_STRING([--with-libelf-include],
      [specify directory for the libelf include files])])
  AC_ARG_WITH(libelf-lib, [AS_HELP_STRING([--with-libelf-lib],
      [specify directory for the libelf library])])

  if test "x$ENABLE_AOT" = xfalse; then
    if (test "x${with_libelf}" != x && test "x${with_libelf}" != xno) || \
        (test "x${with_libelf_include}" != x && test "x${with_libelf_include}" != xno) || \
        (test "x${with_libelf_lib}" != x && test "x${with_libelf_lib}" != xno); then
      AC_MSG_WARN([[libelf is not used, so --with-libelf[-*] is ignored]])
    fi
    LIBELF_CFLAGS=
    LIBELF_LIBS=
  else
    LIBELF_FOUND=no

    if test "x${with_libelf}" = xno || test "x${with_libelf_include}" = xno || test "x${with_libelf_lib}" = xno; then
      ENABLE_AOT="false"
      if test "x${enable_aot}" = xyes; then
        AC_MSG_ERROR([libelf is explicitly disabled, cannot build AOT. Enable libelf or remove --enable-aot to disable AOT.])
      fi
    else
      if test "x${with_libelf}" != x; then
        ELF_LIBS="-L${with_libelf}/lib -lelf"
        ELF_CFLAGS="-I${with_libelf}/include"
        LIBELF_FOUND=yes
      fi
      if test "x${with_libelf_include}" != x; then
        ELF_CFLAGS="-I${with_libelf_include}"
        LIBELF_FOUND=yes
      fi
      if test "x${with_libelf_lib}" != x; then
        ELF_LIBS="-L${with_libelf_lib} -lelf"
        LIBELF_FOUND=yes
      fi
      # Do not try pkg-config if we have a sysroot set.
      if test "x$SYSROOT" = x; then
        if test "x$LIBELF_FOUND" = xno; then
          # Figure out ELF_CFLAGS and ELF_LIBS
          PKG_CHECK_MODULES([ELF], [libelf], [LIBELF_FOUND=yes], [LIBELF_FOUND=no])
        fi
      fi
      if test "x$LIBELF_FOUND" = xno; then
        AC_CHECK_HEADERS([libelf.h],
            [
              LIBELF_FOUND=yes
              ELF_CFLAGS=
              ELF_LIBS=-lelf
            ],
            [LIBELF_FOUND=no]
        )
      fi
      if test "x$LIBELF_FOUND" = xno; then
        ENABLE_AOT="false"
        HELP_MSG_MISSING_DEPENDENCY([elf])
        if test "x${enable_aot}" = xyes; then
          AC_MSG_ERROR([libelf not found, cannot build AOT. Remove --enable-aot to disable AOT or: $HELP_MSG])
        else
          AC_MSG_WARN([libelf not found, cannot build AOT. $HELP_MSG])
        fi
      else
        AC_MSG_CHECKING([if libelf works])
        AC_LANG_PUSH(C)
        OLD_CFLAGS="$CFLAGS"
        CFLAGS="$CFLAGS $ELF_CFLAGS"
        OLD_LIBS="$LIBS"
        LIBS="$LIBS $ELF_LIBS"
        AC_LINK_IFELSE([AC_LANG_PROGRAM([#include <libelf.h>],
            [
              elf_version(0);
              return 0;
            ])],
            [LIBELF_WORKS=yes],
            [LIBELF_WORKS=no]
        )
        CFLAGS="$OLD_CFLAGS"
        LIBS="$OLD_LIBS"
        AC_LANG_POP(C)
        AC_MSG_RESULT([$LIBELF_WORKS])

        if test "x$LIBELF_WORKS" = xno; then
          ENABLE_AOT="false"
          HELP_MSG_MISSING_DEPENDENCY([elf])
          if test "x$enable_aot" = "xyes"; then
            AC_MSG_ERROR([Found libelf but could not link and compile with it. Remove --enable-aot to disable AOT or: $HELP_MSG])
          else
            AC_MSG_WARN([Found libelf but could not link and compile with it. $HELP_MSG])
          fi
        fi
      fi
    fi
  fi

  AC_SUBST(ELF_CFLAGS)
  AC_SUBST(ELF_LIBS)
])
