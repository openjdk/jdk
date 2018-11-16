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

###############################################################################
#
# Setup and check the Java Microbenchmark Harness
#
AC_DEFUN_ONCE([LIB_TESTS_SETUP_JMH],
[
  AC_ARG_WITH(jmh, [AS_HELP_STRING([--with-jmh],
      [Java Microbenchmark Harness for building the OpenJDK Microbenchmark Suite])])

  AC_MSG_CHECKING([for jmh (Java Microbenchmark Harness)])
  if test "x$with_jmh" = xno || test "x$with_jmh" = x; then
    AC_MSG_RESULT([no, disabled])
  elif test "x$with_jmh" = xyes; then
    AC_MSG_RESULT([no, error])
    AC_MSG_ERROR([--with-jmh requires a directory containing all jars needed by JMH])
  else
    # Path specified
    JMH_HOME="$with_jmh"
    if test ! -d [$JMH_HOME]; then
      AC_MSG_RESULT([no, error])
      AC_MSG_ERROR([$JMH_HOME does not exist or is not a directory])
    fi
    BASIC_FIXUP_PATH([JMH_HOME])

    jar_names="jmh-core jmh-generator-annprocess jopt-simple commons-math3"
    for jar in $jar_names; do
      found_jar_files=$($ECHO $(ls $JMH_HOME/$jar-*.jar 2> /dev/null))

      if test "x$found_jar_files" = x; then
        AC_MSG_RESULT([no])
        AC_MSG_ERROR([--with-jmh does not contain $jar-*.jar])
      elif ! test -e "$found_jar_files"; then
        AC_MSG_RESULT([no])
        AC_MSG_ERROR([--with-jmh contain multiple $jar-*.jar: $found_jar_files])
      fi

      found_jar_var_name=found_${jar//-/_}
      eval $found_jar_var_name='"'$found_jar_files'"'
    done
    AC_MSG_RESULT([yes])

    JMH_CORE_JAR=$found_jmh_core
    JMH_GENERATOR_JAR=$found_jmh_generator_annprocess
    JMH_JOPT_SIMPLE_JAR=$found_jopt_simple
    JMH_COMMONS_MATH_JAR=$found_commons_math3


    if [ [[ "$JMH_CORE_JAR" =~ jmh-core-(.*)\.jar$ ]] ] ; then
      JMH_VERSION=${BASH_REMATCH[[1]]}
    else
      JMH_VERSION=unknown
    fi

    AC_MSG_NOTICE([JMH core version: $JMH_VERSION])
  fi

  AC_SUBST(JMH_CORE_JAR)
  AC_SUBST(JMH_GENERATOR_JAR)
  AC_SUBST(JMH_JOPT_SIMPLE_JAR)
  AC_SUBST(JMH_COMMONS_MATH_JAR)
  AC_SUBST(JMH_VERSION)
])
