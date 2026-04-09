#
# Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

# Minimum supported versions
JTREG_MINIMUM_VERSION=8.2.1
GTEST_MINIMUM_VERSION=1.14.0

################################################################################
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

        # Verify that the version is the required one.
        # This is a simplified version of TOOLCHAIN_CHECK_COMPILER_VERSION
        gtest_version="`$GREP GOOGLETEST_VERSION $GTEST_FRAMEWORK_SRC/CMakeLists.txt | $SED -e 's/set(GOOGLETEST_VERSION \(.*\))/\1/'`"
        comparable_actual_version=`$AWK -F. '{ printf("%05d%05d%05d%05d\n", [$]1, [$]2, [$]3, [$]4) }' <<< "$gtest_version"`
        comparable_minimum_version=`$AWK -F. '{ printf("%05d%05d%05d%05d\n", [$]1, [$]2, [$]3, [$]4) }' <<< "$GTEST_MINIMUM_VERSION"`
        if test $comparable_actual_version -lt $comparable_minimum_version ; then
          AC_MSG_ERROR([gtest version is too old, at least version $GTEST_MINIMUM_VERSION is required])
        fi
      fi
    fi
  fi

  AC_SUBST(GTEST_FRAMEWORK_SRC)
])

################################################################################
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

  # Specify a JDK for running jtreg. Defaults to the BOOT_JDK.
  AC_ARG_WITH(jtreg-jdk, [AS_HELP_STRING([--with-jdk],
    [path to JDK for running jtreg @<:@BOOT_JDK@:>@])])

  AC_MSG_CHECKING([for jtreg jdk])
  if test "x${with_jtreg_jdk}" != x; then
    if test "x${with_jtreg_jdk}" = xno; then
      AC_MSG_RESULT([no, jtreg jdk not specified])
    elif test "x${with_jtreg_jdk}" = xyes; then
      AC_MSG_RESULT([not specified])
      AC_MSG_ERROR([--with-jtreg-jdk needs a value])
    else
      JTREG_JDK="${with_jtreg_jdk}"
      AC_MSG_RESULT([$JTREG_JDK])
      UTIL_FIXUP_PATH(JTREG_JDK)
      if test ! -f "$JTREG_JDK/bin/java"; then
        AC_MSG_ERROR([Could not find jtreg java at $JTREG_JDK/bin/java])
      fi
    fi
  else
    JTREG_JDK="${BOOT_JDK}"
    AC_MSG_RESULT([no, using BOOT_JDK])
  fi

  UTIL_FIXUP_PATH(JTREG_JDK)
  AC_SUBST([JTREG_JDK])
  # For use in the configure script
  JTREG_JAVA="$FIXPATH $JTREG_JDK/bin/java"

  # Verify jtreg version
  if test "x$JT_HOME" != x; then
    AC_MSG_CHECKING([jtreg jar existence])
    if test ! -f "$JT_HOME/lib/jtreg.jar"; then
      AC_MSG_ERROR([Could not find jtreg jar at $JT_HOME/lib/jtreg.jar])
    fi

    AC_MSG_CHECKING([jtreg version number])
    # jtreg -version looks like this: "jtreg 6.1+1-19"
    # Extract actual version part ("6.1" in this case)
    jtreg_version_full=$($JTREG_JAVA -jar $JT_HOME/lib/jtreg.jar -version | $HEAD -n 1 | $CUT -d ' ' -f 2)

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

# Setup the tidy html checker
AC_DEFUN_ONCE([LIB_TESTS_SETUP_TIDY],
[
  UTIL_LOOKUP_PROGS(TIDY, tidy)

  if test "x$TIDY" != x; then
    AC_MSG_CHECKING([if tidy is working properly])
    tidy_output=`$TIDY --version 2>&1`
    if ! $ECHO "$tidy_output" | $GREP -q "HTML Tidy" 2>&1 > /dev/null; then
      AC_MSG_RESULT([no])
      AC_MSG_NOTICE([$TIDY is not a valid tidy executable and will be ignored. Output from --version: $tidy_output])
      TIDY=
    elif ! $ECHO "$tidy_output" | $GREP -q "version" 2>&1 > /dev/null; then
      AC_MSG_RESULT([no])
      AC_MSG_NOTICE([$TIDY is missing a proper version number and will be ignored. Output from --version: $tidy_output])
      TIDY=
    else
      AC_MSG_RESULT([yes])
      AC_MSG_CHECKING([for tidy version])
      tidy_version=`$ECHO $tidy_output | $SED -e 's/.*version //g'`
      AC_MSG_RESULT([$tidy_version])
    fi
  fi
  AC_SUBST(TIDY)
])

################################################################################
#
# Check if building of the jtreg failure handler should be enabled.
#
AC_DEFUN_ONCE([LIB_TESTS_ENABLE_DISABLE_FAILURE_HANDLER],
[
  if test "x$BUILD_ENV" = "xci"; then
    BUILD_FAILURE_HANDLER_DEFAULT=auto
  else
    BUILD_FAILURE_HANDLER_DEFAULT=false
  fi

  UTIL_ARG_ENABLE(NAME: jtreg-failure-handler, DEFAULT: $BUILD_FAILURE_HANDLER_DEFAULT,
      RESULT: BUILD_FAILURE_HANDLER,
      DESC: [enable building of the jtreg failure handler],
      DEFAULT_DESC: [enabled if jtreg is present and build env is CI],
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

AC_DEFUN_ONCE([LIB_TESTS_ENABLE_DISABLE_JTREG_TEST_THREAD_FACTORY],
[
  UTIL_ARG_ENABLE(NAME: jtreg-test-thread-factory, DEFAULT: auto,
      RESULT: BUILD_JTREG_TEST_THREAD_FACTORY,
      DESC: [enable building of the jtreg test thread factory],
      DEFAULT_DESC: [enabled if jtreg is present],
      CHECKING_MSG: [if the jtreg test thread factory should be built],
      CHECK_AVAILABLE: [
        AC_MSG_CHECKING([if the jtreg test thread factory is available])
        if test "x$JT_HOME" != "x"; then
          AC_MSG_RESULT([yes])
        else
          AVAILABLE=false
          AC_MSG_RESULT([no (jtreg not present)])
        fi
      ])
  AC_SUBST(BUILD_JTREG_TEST_THREAD_FACTORY)
])
