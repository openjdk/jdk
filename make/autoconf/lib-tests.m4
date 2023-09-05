#
# Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
# Setup libraries and functionalities needed to test the JDK.
################################################################################

# Minimum supported version
JTREG_MINIMUM_VERSION=7.3.1

###############################################################################
#
# Setup and check for gtest framework source files
#
AC_DEFUN_ONCE([LIB_TESTS_SETUP_GTEST],
[
  AC_ARG_WITH(gtest, [AS_HELP_STRING([--with-gtest],
      [specify prefix directory for the gtest framework])])

  if test "x${with_gtest}" != x; then
    AC_MSG_CHECKING([for gtest])
    if test "x${with_gtest}" = xno; then
      AC_MSG_RESULT([no, disabled])
    elif test "x${with_gtest}" = xyes; then
      AC_MSG_RESULT([no, error])
      AC_MSG_ERROR([--with-gtest must have a value])
    else
      if ! test -s "${with_gtest}/googletest/include/gtest/gtest.h"; then
        AC_MSG_RESULT([no])
        AC_MSG_ERROR([Can't find 'googletest/include/gtest/gtest.h' under ${with_gtest} given with the --with-gtest option.])
      elif ! test -s "${with_gtest}/googlemock/include/gmock/gmock.h"; then
        AC_MSG_RESULT([no])
        AC_MSG_ERROR([Can't find 'googlemock/include/gmock/gmock.h' under ${with_gtest} given with the --with-gtest option.])
      else
        GTEST_FRAMEWORK_SRC=$with_gtest
        AC_MSG_RESULT([$GTEST_FRAMEWORK_SRC])
        UTIL_FIXUP_PATH([GTEST_FRAMEWORK_SRC])

        # Try to verify version. We require 1.8.1, but this can not be directly
        # determined. :-( Instead, there are different, incorrect version
        # numbers we can look for.
        GTEST_VERSION_1="`$GREP GOOGLETEST_VERSION $GTEST_FRAMEWORK_SRC/CMakeLists.txt | $SED -E -e 's/set\(GOOGLETEST_VERSION (.*)\)/\1/'`"
        if test "x$GTEST_VERSION_1" != "x1.9.0"; then
          AC_MSG_ERROR([gtest at $GTEST_FRAMEWORK_SRC does not seem to be version 1.8.1])
        fi

        # We cannot grep for "AC_IN*T" as a literal since then m4 will treat it as a macro
        # and expand it.
        # Additional [] needed to keep m4 from mangling shell constructs.
        [ GTEST_VERSION_2="`$GREP -A1 ^.C_INIT $GTEST_FRAMEWORK_SRC/configure.ac | $TAIL -n 1 | $SED -E -e 's/ +\[(.*)],/\1/'`" ]
        if test "x$GTEST_VERSION_2" != "x1.8.0"; then
          AC_MSG_ERROR([gtest at $GTEST_FRAMEWORK_SRC does not seem to be version 1.8.1 B])
        fi
      fi
    fi
  fi

  AC_SUBST(GTEST_FRAMEWORK_SRC)
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
    AC_MSG_RESULT([yes, $JMH_HOME])

    UTIL_FIXUP_PATH([JMH_HOME])

    jar_names="jmh-core jmh-generator-annprocess jopt-simple commons-math3"
    for jar in $jar_names; do
      found_jar_files=$($ECHO $(ls $JMH_HOME/$jar-*.jar 2> /dev/null))

      if test "x$found_jar_files" = x; then
        AC_MSG_ERROR([--with-jmh does not contain $jar-*.jar])
      elif ! test -e "$found_jar_files"; then
        AC_MSG_ERROR([--with-jmh contain multiple $jar-*.jar: $found_jar_files])
      fi

      found_jar_var_name=found_${jar//-/_}
      eval $found_jar_var_name='"'$found_jar_files'"'
    done

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

# Setup the JTReg Regression Test Harness.
AC_DEFUN_ONCE([LIB_TESTS_SETUP_JTREG],
[
  AC_ARG_WITH(jtreg, [AS_HELP_STRING([--with-jtreg],
      [Regression Test Harness @<:@probed@:>@])])

  if test "x$with_jtreg" = xno; then
    # jtreg disabled
    AC_MSG_CHECKING([for jtreg test harness])
    AC_MSG_RESULT([no, disabled])
  elif test "x$with_jtreg" != xyes && test "x$with_jtreg" != x; then
    if test -d "$with_jtreg"; then
      # An explicit path is specified, use it.
      JT_HOME="$with_jtreg"
    else
      case "$with_jtreg" in
        *.zip )
          JTREG_SUPPORT_DIR=$CONFIGURESUPPORT_OUTPUTDIR/jtreg
          $RM -rf $JTREG_SUPPORT_DIR
          $MKDIR -p $JTREG_SUPPORT_DIR
          $UNZIP -qq -d $JTREG_SUPPORT_DIR $with_jtreg

          # Try to find jtreg to determine JT_HOME path
          JTREG_PATH=`$FIND $JTREG_SUPPORT_DIR | $GREP "/bin/jtreg"`
          if test "x$JTREG_PATH" != x; then
            JT_HOME=$($DIRNAME $($DIRNAME $JTREG_PATH))
          fi
          ;;
        * )
          ;;
      esac
    fi
    UTIL_FIXUP_PATH([JT_HOME])
    if test ! -d "$JT_HOME"; then
      AC_MSG_ERROR([jtreg home directory from --with-jtreg=$with_jtreg does not exist])
    fi

    if test ! -e "$JT_HOME/lib/jtreg.jar"; then
      AC_MSG_ERROR([jtreg home directory from --with-jtreg=$with_jtreg is not a valid jtreg home])
    fi

    AC_MSG_CHECKING([for jtreg test harness])
    AC_MSG_RESULT([$JT_HOME])
  else
    # Try to locate jtreg using the JT_HOME environment variable
    if test "x$JT_HOME" != x; then
      # JT_HOME set in environment, use it
      if test ! -d "$JT_HOME"; then
        AC_MSG_WARN([Ignoring JT_HOME pointing to invalid directory: $JT_HOME])
        JT_HOME=
      else
        if test ! -e "$JT_HOME/lib/jtreg.jar"; then
          AC_MSG_WARN([Ignoring JT_HOME which is not a valid jtreg home: $JT_HOME])
          JT_HOME=
        else
          AC_MSG_NOTICE([Located jtreg using JT_HOME from environment])
        fi
      fi
    fi

    if test "x$JT_HOME" = x; then
      # JT_HOME is not set in environment, or was deemed invalid.
      # Try to find jtreg on path
      UTIL_LOOKUP_PROGS(JTREGEXE, jtreg)
      if test "x$JTREGEXE" != x; then
        # That's good, now try to derive JT_HOME
        JT_HOME=`(cd $($DIRNAME $JTREGEXE)/.. && pwd)`
        if test ! -e "$JT_HOME/lib/jtreg.jar"; then
          AC_MSG_WARN([Ignoring jtreg from path since a valid jtreg home cannot be found])
          JT_HOME=
        else
          AC_MSG_NOTICE([Located jtreg using jtreg executable in path])
        fi
      fi
    fi

    AC_MSG_CHECKING([for jtreg test harness])
    if test "x$JT_HOME" != x; then
      AC_MSG_RESULT([$JT_HOME])
    else
      AC_MSG_RESULT([no, not found])

      if test "x$with_jtreg" = xyes; then
        AC_MSG_ERROR([--with-jtreg was specified, but no jtreg found.])
      fi
    fi
  fi

  UTIL_FIXUP_PATH(JT_HOME)
  AC_SUBST(JT_HOME)

  # Verify jtreg version
  if test "x$JT_HOME" != x; then
    AC_MSG_CHECKING([jtreg version number])
    # jtreg -version looks like this: "jtreg 6.1+1-19"
    # Extract actual version part ("6.1" in this case)
    jtreg_version_full=`$JAVA -jar $JT_HOME/lib/jtreg.jar -version | $HEAD -n 1 | $CUT -d ' ' -f 2`
    jtreg_version=${jtreg_version_full/%+*}
    AC_MSG_RESULT([$jtreg_version])

    # This is a simplified version of TOOLCHAIN_CHECK_COMPILER_VERSION
    comparable_actual_version=`$AWK -F. '{ printf("%05d%05d%05d%05d\n", [$]1, [$]2, [$]3, [$]4) }' <<< "$jtreg_version"`
    comparable_minimum_version=`$AWK -F. '{ printf("%05d%05d%05d%05d\n", [$]1, [$]2, [$]3, [$]4) }' <<< "$JTREG_MINIMUM_VERSION"`
    if test $comparable_actual_version -lt $comparable_minimum_version ; then
      AC_MSG_ERROR([jtreg version is too old, at least version $JTREG_MINIMUM_VERSION is required])
    fi
  fi
])

# Setup the JIB dependency resolver
AC_DEFUN_ONCE([LIB_TESTS_SETUP_JIB],
[
  AC_ARG_WITH(jib, [AS_HELP_STRING([--with-jib],
      [Jib dependency management tool @<:@not used@:>@])])

  if test "x$with_jib" = xno || test "x$with_jib" = x; then
    # jib disabled
    AC_MSG_CHECKING([for jib])
    AC_MSG_RESULT(no)
  elif test "x$with_jib" = xyes; then
    AC_MSG_ERROR([Must supply a value to --with-jib])
  else
    JIB_HOME="${with_jib}"
    AC_MSG_CHECKING([for jib])
    AC_MSG_RESULT(${JIB_HOME})
    if test ! -d "${JIB_HOME}"; then
      AC_MSG_ERROR([--with-jib must be a directory])
    fi
    JIB_JAR=$(ls ${JIB_HOME}/lib/jib-*.jar)
    if test ! -f "${JIB_JAR}"; then
      AC_MSG_ERROR([Could not find jib jar file in ${JIB_HOME}])
    fi
  fi

  AC_SUBST(JIB_HOME)
])

################################################################################
#
# Check if building of the jtreg failure handler should be enabled.
#
AC_DEFUN_ONCE([LIB_TESTS_ENABLE_DISABLE_FAILURE_HANDLER],
[
  UTIL_ARG_ENABLE(NAME: jtreg-failure-handler, DEFAULT: auto,
      RESULT: BUILD_FAILURE_HANDLER,
      DESC: [enable building of the jtreg failure handler],
      DEFAULT_DESC: [enabled if jtreg is present],
      CHECKING_MSG: [if the jtreg failure handler should be built],
      CHECK_AVAILABLE: [
        AC_MSG_CHECKING([if the jtreg failure handler is available])
        if test "x$JT_HOME" != "x"; then
          AC_MSG_RESULT([yes])
        else
          AVAILABLE=false
          AC_MSG_RESULT([no (jtreg not present)])
        fi
      ])
  AC_SUBST(BUILD_FAILURE_HANDLER)
])
