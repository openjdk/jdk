#
# Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

AC_DEFUN([BPERF_CHECK_CORES],
[
  AC_MSG_CHECKING([for number of cores])
  NUM_CORES=1
  FOUND_CORES=no

  if test -f /proc/cpuinfo; then
    # Looks like a Linux (or cygwin) system
    NUM_CORES=`cat /proc/cpuinfo  | grep -c processor`
    FOUND_CORES=yes
  elif test -x /usr/sbin/psrinfo; then
    # Looks like a Solaris system
    NUM_CORES=`LC_MESSAGES=C /usr/sbin/psrinfo -v | grep -c on-line`
    FOUND_CORES=yes
  elif test -x /usr/sbin/system_profiler; then
    # Looks like a MacOSX system
    NUM_CORES=`/usr/sbin/system_profiler -detailLevel full SPHardwareDataType | grep 'Cores' | awk  '{print [$]5}'`
    FOUND_CORES=yes
  elif test "x$OPENJDK_BUILD_OS" = xaix ; then
    NUM_CORES=`/usr/sbin/prtconf | grep "^Number Of Processors" | awk '{ print [$]4 }'`
    FOUND_CORES=yes
  elif test -n "$NUMBER_OF_PROCESSORS"; then
    # On windows, look in the env
    NUM_CORES=$NUMBER_OF_PROCESSORS
    FOUND_CORES=yes
  fi

  if test "x$FOUND_CORES" = xyes; then
    AC_MSG_RESULT([$NUM_CORES])
  else
    AC_MSG_RESULT([could not detect number of cores, defaulting to 1])
    AC_MSG_WARN([This will disable all parallelism from build!])
  fi
])

AC_DEFUN([BPERF_CHECK_MEMORY_SIZE],
[
  AC_MSG_CHECKING([for memory size])
  # Default to 1024 MB
  MEMORY_SIZE=1024
  FOUND_MEM=no

  if test -f /proc/meminfo; then
    # Looks like a Linux (or cygwin) system
    MEMORY_SIZE=`cat /proc/meminfo | grep MemTotal | awk '{print [$]2}'`
    MEMORY_SIZE=`expr $MEMORY_SIZE / 1024`
    FOUND_MEM=yes
  elif test -x /usr/sbin/prtconf; then
    # Looks like a Solaris or AIX system
    MEMORY_SIZE=`/usr/sbin/prtconf | grep "^Memory [[Ss]]ize" | awk '{ print [$]3 }'`
    FOUND_MEM=yes
  elif test -x /usr/sbin/system_profiler; then
    # Looks like a MacOSX system
    MEMORY_SIZE=`/usr/sbin/system_profiler -detailLevel full SPHardwareDataType | grep 'Memory' | awk  '{print [$]2}'`
    MEMORY_SIZE=`expr $MEMORY_SIZE \* 1024`
    FOUND_MEM=yes
  elif test "x$OPENJDK_BUILD_OS" = xwindows; then
    # Windows, but without cygwin
    MEMORY_SIZE=`wmic computersystem get totalphysicalmemory -value | grep = | cut -d "=" -f 2-`
    MEMORY_SIZE=`expr $MEMORY_SIZE / 1024 / 1024`
    FOUND_MEM=yes
  fi

  if test "x$FOUND_MEM" = xyes; then
    AC_MSG_RESULT([$MEMORY_SIZE MB])
  else
    AC_MSG_RESULT([could not detect memory size, defaulting to $MEMORY_SIZE MB])
    AC_MSG_WARN([This might seriously impact build performance!])
  fi
])

AC_DEFUN_ONCE([BPERF_SETUP_BUILD_CORES],
[
  # How many cores do we have on this build system?
  AC_ARG_WITH(num-cores, [AS_HELP_STRING([--with-num-cores],
      [number of cores in the build system, e.g. --with-num-cores=8 @<:@probed@:>@])])
  if test "x$with_num_cores" = x; then
    # The number of cores were not specified, try to probe them.
    BPERF_CHECK_CORES
  else
    NUM_CORES=$with_num_cores
  fi
  AC_SUBST(NUM_CORES)
])

AC_DEFUN_ONCE([BPERF_SETUP_BUILD_MEMORY],
[
  # How much memory do we have on this build system?
  AC_ARG_WITH(memory-size, [AS_HELP_STRING([--with-memory-size],
      [memory (in MB) available in the build system, e.g. --with-memory-size=1024 @<:@probed@:>@])])
  if test "x$with_memory_size" = x; then
    # The memory size was not specified, try to probe it.
    BPERF_CHECK_MEMORY_SIZE
  else
    MEMORY_SIZE=$with_memory_size
  fi
  AC_SUBST(MEMORY_SIZE)
])

AC_DEFUN_ONCE([BPERF_SETUP_BUILD_JOBS],
[
  # Provide a decent default number of parallel jobs for make depending on
  # number of cores, amount of memory and machine architecture.
  AC_ARG_WITH(jobs, [AS_HELP_STRING([--with-jobs],
      [number of parallel jobs to let make run @<:@calculated based on cores and memory@:>@])])
  if test "x$with_jobs" = x; then
    # Number of jobs was not specified, calculate.
    AC_MSG_CHECKING([for appropriate number of jobs to run in parallel])
    # Approximate memory in GB.
    memory_gb=`expr $MEMORY_SIZE / 1024`
    # Pick the lowest of memory in gb and number of cores.
    if test "$memory_gb" -lt "$NUM_CORES"; then
      JOBS="$memory_gb"
    else
      JOBS="$NUM_CORES"
    fi
    if test "$JOBS" -eq "0"; then
      JOBS=1
    fi
    AC_MSG_RESULT([$JOBS])
  else
    JOBS=$with_jobs
  fi
  AC_SUBST(JOBS)
])

AC_DEFUN_ONCE([BPERF_SETUP_TEST_JOBS],
[
  # The number of test jobs will be chosen automatically if TEST_JOBS is 0
  AC_ARG_WITH(test-jobs, [AS_HELP_STRING([--with-test-jobs],
      [number of parallel tests jobs to run @<:@based on build jobs@:>@])])
  if test "x$with_test_jobs" = x; then
      TEST_JOBS=0
  else
      TEST_JOBS=$with_test_jobs
  fi
  AC_SUBST(TEST_JOBS)
])

AC_DEFUN([BPERF_SETUP_CCACHE],
[
  AC_ARG_ENABLE([ccache],
      [AS_HELP_STRING([--enable-ccache],
      [enable using ccache to speed up recompilations @<:@disabled@:>@])])

  CCACHE=
  CCACHE_STATUS=
  AC_MSG_CHECKING([is ccache enabled])
  if test "x$enable_ccache" = xyes; then
    if test "x$TOOLCHAIN_TYPE" = "xgcc" -o "x$TOOLCHAIN_TYPE" = "xclang"; then
      AC_MSG_RESULT([yes])
      OLD_PATH="$PATH"
      if test "x$TOOLCHAIN_PATH" != x; then
        PATH=$TOOLCHAIN_PATH:$PATH
      fi
      BASIC_REQUIRE_PROGS(CCACHE, ccache)
      PATH="$OLD_PATH"
      CCACHE_VERSION=[`$CCACHE --version | head -n1 | $SED 's/[A-Za-z ]*//'`]
      CCACHE_STATUS="Active ($CCACHE_VERSION)"
    else
      AC_MSG_RESULT([no])
      AC_MSG_WARN([ccache is not supported with toolchain type $TOOLCHAIN_TYPE])
    fi
  elif test "x$enable_ccache" = xno; then
    AC_MSG_RESULT([no, explicitly disabled])
    CCACHE_STATUS="Disabled"
  elif test "x$enable_ccache" = x; then
    AC_MSG_RESULT([no])
  else
    AC_MSG_RESULT([unknown])
    AC_MSG_ERROR([--enable-ccache does not accept any parameters])
  fi
  AC_SUBST(CCACHE)

  AC_ARG_WITH([ccache-dir],
      [AS_HELP_STRING([--with-ccache-dir],
      [where to store ccache files @<:@~/.ccache@:>@])])

  if test "x$with_ccache_dir" != x; then
    # When using a non home ccache directory, assume the use is to share ccache files
    # with other users. Thus change the umask.
    SET_CCACHE_DIR="CCACHE_DIR=$with_ccache_dir CCACHE_UMASK=002"
    if test "x$CCACHE" = x; then
      AC_MSG_WARN([--with-ccache-dir has no meaning when ccache is not enabled])
    fi
  fi

  if test "x$CCACHE" != x; then
    BPERF_SETUP_CCACHE_USAGE
  fi
])

AC_DEFUN([BPERF_SETUP_CCACHE_USAGE],
[
  if test "x$CCACHE" != x; then
    if test "x$USE_PRECOMPILED_HEADER" = "x1"; then
      HAS_BAD_CCACHE=[`$ECHO $CCACHE_VERSION | \
          $GREP -e '^1.*' -e '^2.*' -e '^3\.0.*' -e '^3\.1\.[0123]$'`]
      if test "x$HAS_BAD_CCACHE" != "x"; then
        AC_MSG_ERROR([Precompiled headers requires ccache 3.1.4 or later, found $CCACHE_VERSION])
      fi
      AC_MSG_CHECKING([if C-compiler supports ccache precompiled headers])
      CCACHE_PRECOMP_FLAG="-fpch-preprocess"
      PUSHED_FLAGS="$CXXFLAGS"
      CXXFLAGS="$CCACHE_PRECOMP_FLAG $CXXFLAGS"
      AC_COMPILE_IFELSE([AC_LANG_PROGRAM([], [])], [CC_KNOWS_CCACHE_TRICK=yes], [CC_KNOWS_CCACHE_TRICK=no])
      CXXFLAGS="$PUSHED_FLAGS"
      if test "x$CC_KNOWS_CCACHE_TRICK" = xyes; then
        AC_MSG_RESULT([yes])
        CFLAGS_CCACHE="$CCACHE_PRECOMP_FLAG"
        AC_SUBST(CFLAGS_CCACHE)
        CCACHE_SLOPPINESS=pch_defines,time_macros
      else
        AC_MSG_RESULT([no])
        AC_MSG_ERROR([Cannot use ccache with precompiled headers without compiler support for $CCACHE_PRECOMP_FLAG])
      fi
    fi

    CCACHE="CCACHE_COMPRESS=1 $SET_CCACHE_DIR \
        CCACHE_SLOPPINESS=$CCACHE_SLOPPINESS CCACHE_BASEDIR=$TOPDIR $CCACHE"

    if test "x$SET_CCACHE_DIR" != x; then
      mkdir -p $CCACHE_DIR > /dev/null 2>&1
      chmod a+rwxs $CCACHE_DIR > /dev/null 2>&1
    fi
  fi
])

################################################################################
#
# Runs icecc-create-env once and prints the error if it fails
#
# $1: arguments to icecc-create-env
# $2: log file
#
AC_DEFUN([BPERF_RUN_ICECC_CREATE_ENV],
[
  cd ${CONFIGURESUPPORT_OUTPUTDIR}/icecc \
      && ${ICECC_CREATE_ENV} $1 > $2 2>&1
  if test "$?" != "0"; then
    AC_MSG_NOTICE([icecc-create-env output:])
    cat $2
    AC_MSG_ERROR([Failed to create icecc compiler environment])
  fi
])

################################################################################
#
# Optionally enable distributed compilation of native code using icecc/icecream
#
AC_DEFUN([BPERF_SETUP_ICECC],
[
  AC_ARG_ENABLE([icecc], [AS_HELP_STRING([--enable-icecc],
      [enable distribted compilation of native code using icecc/icecream @<:@disabled@:>@])])

  if test "x${enable_icecc}" = "xyes"; then
    BASIC_REQUIRE_PROGS(ICECC_CMD, icecc)
    old_path="$PATH"

    # Look for icecc-create-env in some known places
    PATH="$PATH:/usr/lib/icecc:/usr/lib64/icecc"
    BASIC_REQUIRE_PROGS(ICECC_CREATE_ENV, icecc-create-env)
    # Use icecc-create-env to create a minimal compilation environment that can
    # be sent to the other hosts in the icecream cluster.
    icecc_create_env_log="${CONFIGURESUPPORT_OUTPUTDIR}/icecc/icecc_create_env.log"
    ${MKDIR} -p ${CONFIGURESUPPORT_OUTPUTDIR}/icecc
    # Older versions of icecc does not have the --gcc parameter
    if ${ICECC_CREATE_ENV} | $GREP -q -e --gcc; then
      icecc_gcc_arg="--gcc"
    fi
    if test "x${TOOLCHAIN_TYPE}" = "xgcc"; then
      BPERF_RUN_ICECC_CREATE_ENV([${icecc_gcc_arg} ${CC} ${CXX}], \
          ${icecc_create_env_log})
    elif test "x$TOOLCHAIN_TYPE" = "xclang"; then
      # For clang, the icecc compilerwrapper is needed. It usually resides next
      # to icecc-create-env.
      BASIC_REQUIRE_PROGS(ICECC_WRAPPER, compilerwrapper)
      BPERF_RUN_ICECC_CREATE_ENV([--clang ${CC} ${ICECC_WRAPPER}], ${icecc_create_env_log})
    else
      AC_MSG_ERROR([Can only create icecc compiler packages for toolchain types gcc and clang])
    fi
    PATH="$old_path"
    # The bundle with the compiler gets a name based on checksums. Parse log file
    # to find it.
    ICECC_ENV_BUNDLE_BASENAME="`${SED} -n '/^creating/s/creating //p' ${icecc_create_env_log}`"
    ICECC_ENV_BUNDLE="${CONFIGURESUPPORT_OUTPUTDIR}/icecc/${ICECC_ENV_BUNDLE_BASENAME}"
    if test ! -f ${ICECC_ENV_BUNDLE}; then
      AC_MSG_ERROR([icecc-create-env did not produce an environment ${ICECC_ENV_BUNDLE}])
    fi
    AC_MSG_CHECKING([for icecc build environment for target compiler])
    AC_MSG_RESULT([${ICECC_ENV_BUNDLE}])
    ICECC="ICECC_VERSION=${ICECC_ENV_BUNDLE} ICECC_CC=${CC} ICECC_CXX=${CXX} ${ICECC_CMD}"

    if test "x${COMPILE_TYPE}" = "xcross"; then
      # If cross compiling, create a separate env package for the build compiler
      # Assume "gcc" or "cc" is gcc and "clang" is clang. Otherwise bail.
      icecc_create_env_log_build="${CONFIGURESUPPORT_OUTPUTDIR}/icecc/icecc_create_env_build.log"
      if test "x${BUILD_CC##*/}" = "xgcc" ||  test "x${BUILD_CC##*/}" = "xcc"; then
        BPERF_RUN_ICECC_CREATE_ENV([${icecc_gcc_arg} ${BUILD_CC} ${BUILD_CXX}], \
            ${icecc_create_env_log_build})
      elif test "x${BUILD_CC##*/}" = "xclang"; then
        BPERF_RUN_ICECC_CREATE_ENV([--clang ${BUILD_CC} ${ICECC_WRAPPER}], ${icecc_create_env_log_build})
      else
        AC_MSG_ERROR([Cannot create icecc compiler package for ${BUILD_CC}])
      fi
      ICECC_ENV_BUNDLE_BASENAME="`${SED} -n '/^creating/s/creating //p' ${icecc_create_env_log_build}`"
      ICECC_ENV_BUNDLE="${CONFIGURESUPPORT_OUTPUTDIR}/icecc/${ICECC_ENV_BUNDLE_BASENAME}"
      if test ! -f ${ICECC_ENV_BUNDLE}; then
        AC_MSG_ERROR([icecc-create-env did not produce an environment ${ICECC_ENV_BUNDLE}])
      fi
      AC_MSG_CHECKING([for icecc build environment for build compiler])
      AC_MSG_RESULT([${ICECC_ENV_BUNDLE}])
      BUILD_ICECC="ICECC_VERSION=${ICECC_ENV_BUNDLE} ICECC_CC=${BUILD_CC} \
          ICECC_CXX=${BUILD_CXX} ${ICECC_CMD}"
    else
      BUILD_ICECC="${ICECC}"
    fi
    AC_SUBST(ICECC)
    AC_SUBST(BUILD_ICECC)
  fi
])

AC_DEFUN_ONCE([BPERF_SETUP_PRECOMPILED_HEADERS],
[

  ###############################################################################
  #
  # Can the C/C++ compiler use precompiled headers?
  #
  AC_ARG_ENABLE([precompiled-headers], [AS_HELP_STRING([--disable-precompiled-headers],
      [disable using precompiled headers when compiling C++ @<:@enabled@:>@])],
      [ENABLE_PRECOMPH=${enable_precompiled_headers}], [ENABLE_PRECOMPH=yes])

  USE_PRECOMPILED_HEADER=1
  AC_MSG_CHECKING([If precompiled header is enabled])
  if test "x$ENABLE_PRECOMPH" = xno; then
    AC_MSG_RESULT([no, forced])
    USE_PRECOMPILED_HEADER=0
  elif test "x$ICECC" != "x"; then
    AC_MSG_RESULT([no, does not work effectively with icecc])
    USE_PRECOMPILED_HEADER=0
  else
    AC_MSG_RESULT([yes])
  fi

  if test "x$ENABLE_PRECOMPH" = xyes; then
    # Check that the compiler actually supports precomp headers.
    if test "x$TOOLCHAIN_TYPE" = xgcc; then
      AC_MSG_CHECKING([that precompiled headers work])
      echo "int alfa();" > conftest.h
      $CXX -x c++-header conftest.h -o conftest.hpp.gch 2>&AS_MESSAGE_LOG_FD >&AS_MESSAGE_LOG_FD
      if test ! -f conftest.hpp.gch; then
        USE_PRECOMPILED_HEADER=0
        AC_MSG_RESULT([no])
      else
        AC_MSG_RESULT([yes])
      fi
      rm -f conftest.h conftest.hpp.gch
    fi
  fi

  AC_SUBST(USE_PRECOMPILED_HEADER)
])


AC_DEFUN_ONCE([BPERF_SETUP_SMART_JAVAC],
[
  AC_ARG_WITH(sjavac-server-java, [AS_HELP_STRING([--with-sjavac-server-java],
      [use this java binary for running the sjavac background server @<:@Boot JDK java@:>@])])

  if test "x$with_sjavac_server_java" != x; then
    SJAVAC_SERVER_JAVA="$with_sjavac_server_java"
    FOUND_VERSION=`$SJAVAC_SERVER_JAVA -version 2>&1 | grep " version \""`
    if test "x$FOUND_VERSION" = x; then
      AC_MSG_ERROR([Could not execute server java: $SJAVAC_SERVER_JAVA])
    fi
  else
    SJAVAC_SERVER_JAVA="$JAVA"
  fi
  AC_SUBST(SJAVAC_SERVER_JAVA)

  if test "$MEMORY_SIZE" -gt "3000"; then
    ADD_JVM_ARG_IF_OK([-d64],SJAVAC_SERVER_JAVA_FLAGS,[$SJAVAC_SERVER_JAVA])
    if test "$JVM_ARG_OK" = true; then
      JVM_64BIT=true
      JVM_ARG_OK=false
    fi
  fi

  MX_VALUE=`expr $MEMORY_SIZE / 2`
  if test "$JVM_64BIT" = true; then
    # Set ms lower than mx since more than one instance of the server might
    # get launched at the same time before they figure out which instance won.
    MS_VALUE=512
    if test "$MX_VALUE" -gt "2048"; then
      MX_VALUE=2048
    fi
  else
    MS_VALUE=256
    if test "$MX_VALUE" -gt "1500"; then
      MX_VALUE=1500
    fi
  fi
  if test "$MX_VALUE" -lt "512"; then
    MX_VALUE=512
  fi
  ADD_JVM_ARG_IF_OK([-Xms${MS_VALUE}M -Xmx${MX_VALUE}M],SJAVAC_SERVER_JAVA_FLAGS,[$SJAVAC_SERVER_JAVA])
  AC_SUBST(SJAVAC_SERVER_JAVA_FLAGS)

  AC_ARG_ENABLE([sjavac], [AS_HELP_STRING([--enable-sjavac],
      [use sjavac to do fast incremental compiles @<:@disabled@:>@])],
      [ENABLE_SJAVAC="${enableval}"], [ENABLE_SJAVAC="no"])
  if test "x$JVM_ARG_OK" = "xfalse"; then
    AC_MSG_WARN([Could not set -Xms${MS_VALUE}M -Xmx${MX_VALUE}M, disabling sjavac])
    ENABLE_SJAVAC="no"
  fi
  AC_MSG_CHECKING([whether to use sjavac])
  AC_MSG_RESULT([$ENABLE_SJAVAC])
  AC_SUBST(ENABLE_SJAVAC)

  AC_ARG_ENABLE([javac-server], [AS_HELP_STRING([--disable-javac-server],
      [disable javac server @<:@enabled@:>@])],
      [ENABLE_JAVAC_SERVER="${enableval}"], [ENABLE_JAVAC_SERVER="yes"])
  if test "x$JVM_ARG_OK" = "xfalse"; then
    AC_MSG_WARN([Could not set -Xms${MS_VALUE}M -Xmx${MX_VALUE}M, disabling javac server])
    ENABLE_JAVAC_SERVER="no"
  fi
  AC_MSG_CHECKING([whether to use javac server])
  AC_MSG_RESULT([$ENABLE_JAVAC_SERVER])
  AC_SUBST(ENABLE_JAVAC_SERVER)

  if test "x$ENABLE_JAVAC_SERVER" = "xyes" || "x$ENABLE_SJAVAC" = "xyes"; then
    # When using a server javac, the small client instances do not need much
    # resources.
    JAVA_FLAGS_JAVAC="$JAVA_FLAGS_SMALL"
  fi
])
