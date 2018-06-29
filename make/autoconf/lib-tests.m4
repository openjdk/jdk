#
# Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
# Check for graalunit libs, needed for running graalunit tests.
#
AC_DEFUN_ONCE([LIB_TESTS_SETUP_GRAALUNIT],
[
  AC_ARG_WITH(graalunit-lib, [AS_HELP_STRING([--with-graalunit-lib],
      [specify location of 3rd party libraries used by Graal unit tests])])

  GRAALUNIT_LIB=
  if test "x${with_graalunit_lib}" != x; then
    AC_MSG_CHECKING([for graalunit libs])
    if test "x${with_graalunit_lib}" = xno; then
      AC_MSG_RESULT([disabled, graalunit tests can not be run])
    elif test "x${with_graalunit_lib}" = xyes; then
      AC_MSG_RESULT([not specified])
      AC_MSG_ERROR([You must specify the path to 3rd party libraries used by Graal unit tests])
    else
      GRAALUNIT_LIB="${with_graalunit_lib}"
      if test ! -d "${GRAALUNIT_LIB}"; then
        AC_MSG_RESULT([no])
        AC_MSG_ERROR([Could not find graalunit 3rd party libraries as specified. (${with_graalunit_lib})])
      else
        AC_MSG_RESULT([$GRAALUNIT_LIB])
      fi
    fi
  fi

  BASIC_FIXUP_PATH([GRAALUNIT_LIB])
  AC_SUBST(GRAALUNIT_LIB)
])

