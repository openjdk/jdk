#
# Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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

m4_include([basic_tools.m4])
m4_include([basic_windows.m4])

###############################################################################
AC_DEFUN_ONCE([BASIC_INIT],
[
  # Save the original command line. This is passed to us by the wrapper configure script.
  AC_SUBST(CONFIGURE_COMMAND_LINE)
  # We might have the original command line if the wrapper was called by some
  # other script.
  AC_SUBST(REAL_CONFIGURE_COMMAND_EXEC_SHORT)
  AC_SUBST(REAL_CONFIGURE_COMMAND_EXEC_FULL)
  AC_SUBST(REAL_CONFIGURE_COMMAND_LINE)
  # AUTOCONF might be set in the environment by the user. Preserve for "make reconfigure".
  AC_SUBST(AUTOCONF)
  # Save the path variable before it gets changed
  ORIGINAL_PATH="$PATH"
  AC_SUBST(ORIGINAL_PATH)
  DATE_WHEN_CONFIGURED=`date`
  AC_SUBST(DATE_WHEN_CONFIGURED)
  AC_MSG_NOTICE([Configuration created at $DATE_WHEN_CONFIGURED.])
])

###############################################################################
# Check that there are no unprocessed overridden variables left.
# If so, they are an incorrect argument and we will exit with an error.
AC_DEFUN([BASIC_CHECK_LEFTOVER_OVERRIDDEN],
[
  if test "x$CONFIGURE_OVERRIDDEN_VARIABLES" != x; then
    # Replace the separating ! with spaces before presenting for end user.
    unknown_variables=${CONFIGURE_OVERRIDDEN_VARIABLES//!/ }
    AC_MSG_WARN([The following variables might be unknown to configure: $unknown_variables])
  fi
])

###############################################################################
# Setup basic configuration paths, and platform-specific stuff related to PATHs.
# Make sure to only use tools set up in BASIC_SETUP_FUNDAMENTAL_TOOLS.
AC_DEFUN_ONCE([BASIC_SETUP_PATHS],
[
  # Save the current directory this script was started from
  CONFIGURE_START_DIR="$PWD"

  # We might need to rewrite ORIGINAL_PATH, if it includes "#", to quote them
  # for make. We couldn't do this when we retrieved ORIGINAL_PATH, since SED
  # was not available at that time.
  REWRITTEN_PATH=`$ECHO "$ORIGINAL_PATH" | $SED -e 's/#/\\\\#/g'`
  if test "x$REWRITTEN_PATH" != "x$ORIGINAL_PATH"; then
    ORIGINAL_PATH="$REWRITTEN_PATH"
    AC_MSG_NOTICE([Rewriting ORIGINAL_PATH to $REWRITTEN_PATH])
  fi

  if test "x$OPENJDK_TARGET_OS" = "xwindows"; then
    BASIC_SETUP_PATHS_WINDOWS
  fi

  # We get the top-level directory from the supporting wrappers.
  BASIC_WINDOWS_VERIFY_DIR($TOPDIR, source)
  UTIL_FIXUP_PATH(TOPDIR)
  AC_MSG_CHECKING([for top-level directory])
  AC_MSG_RESULT([$TOPDIR])
  AC_SUBST(TOPDIR)

  if test "x$CUSTOM_ROOT" != x; then
    BASIC_WINDOWS_VERIFY_DIR($CUSTOM_ROOT, custom root)
    UTIL_FIXUP_PATH(CUSTOM_ROOT)
    WORKSPACE_ROOT="${CUSTOM_ROOT}"
  else
    WORKSPACE_ROOT="${TOPDIR}"
  fi
  AC_SUBST(WORKSPACE_ROOT)

  UTIL_FIXUP_PATH(CONFIGURE_START_DIR)
  AC_SUBST(CONFIGURE_START_DIR)

  # Locate the directory of this script.
  AUTOCONF_DIR=$TOPDIR/make/autoconf
])

###############################################################################
# Setup what kind of build environment type we have (CI or local developer)
AC_DEFUN_ONCE([BASIC_SETUP_BUILD_ENV],
[
  if test "x$CI" = "xtrue"; then
    DEFAULT_BUILD_ENV="ci"
    AC_MSG_NOTICE([CI environment variable set to $CI])
  else
    DEFAULT_BUILD_ENV="dev"
  fi

  UTIL_ARG_WITH(NAME: build-env, TYPE: literal,
      RESULT: BUILD_ENV,
      VALID_VALUES: [auto dev ci], DEFAULT: auto,
      CHECKING_MSG: [for build environment type],
      DESC: [select build environment type (affects certain default values)],
      IF_AUTO: [
        RESULT=$DEFAULT_BUILD_ENV
      ]
  )
  AC_SUBST(BUILD_ENV)
])

###############################################################################
# Evaluates platform specific overrides for devkit variables.
# $1: Name of variable
AC_DEFUN([BASIC_EVAL_DEVKIT_VARIABLE],
[
  if test "x[$]$1" = x; then
    eval $1="\${$1_${OPENJDK_TARGET_CPU}}"
  fi
])

###############################################################################
# Evaluates platform specific overrides for build devkit variables.
# $1: Name of variable
AC_DEFUN([BASIC_EVAL_BUILD_DEVKIT_VARIABLE],
[
  if test "x[$]$1" = x; then
    eval $1="\${$1_${OPENJDK_BUILD_CPU}}"
  fi
])

###############################################################################
AC_DEFUN([BASIC_SETUP_XCODE_SYSROOT],
[
  AC_MSG_CHECKING([for sdk name])
  AC_ARG_WITH([sdk-name], [AS_HELP_STRING([--with-sdk-name],
      [use the Xcode platform SDK of the given name. @<:@macosx@:>@])],
      [SDK_NAME=$with_sdk_name]
  )
  if test "x$SDK_NAME" = x; then
    SDK_NAME=macosx
  fi
  AC_MSG_RESULT([$SDK_NAME])

  if test "x$DEVKIT_ROOT" != x; then
    # We need to use xcodebuild from the devkit, if provided
    UTIL_LOOKUP_PROGS(XCODEBUILD, xcodebuild, $DEVKIT_TOOLCHAIN_PATH)
    if test "x$XCODEBUILD" = x; then
      AC_MSG_ERROR([No xcodebuild tool found in the provided devkit])
    fi
    XCODEBUILD_OUTPUT=`"$XCODEBUILD" -version 2>&1`
    if test $? -ne 0; then
      AC_MSG_ERROR([The xcodebuild tool in the devkit reports an error: $XCODEBUILD_OUTPUT])
    fi
  elif test "x$TOOLCHAIN_PATH" != x; then
    UTIL_LOOKUP_PROGS(XCODEBUILD, xcodebuild, $TOOLCHAIN_PATH)
    if test "x$XCODEBUILD" != x; then
      XCODEBUILD_OUTPUT=`"$XCODEBUILD" -version 2>&1`
      if test $? -ne 0; then
        AC_MSG_WARN([Ignoring the located xcodebuild tool $XCODEBUILD due to an error: $XCODEBUILD_OUTPUT])
        XCODEBUILD=
      fi
    fi
  else
    UTIL_LOOKUP_PROGS(XCODEBUILD, xcodebuild)
    if test "x$XCODEBUILD" != x; then
      XCODEBUILD_OUTPUT=`"$XCODEBUILD" -version 2>&1`
      if test $? -ne 0; then
        AC_MSG_WARN([Ignoring the located xcodebuild tool $XCODEBUILD due to an error: $XCODEBUILD_OUTPUT])
        XCODEBUILD=
      fi
    fi
  fi

  if test "x$SYSROOT" != x; then
    if ! test -f "$SYSROOT/System/Library/Frameworks/Foundation.framework/Headers/Foundation.h"; then
      AC_MSG_ERROR([Invalid sysroot, framework headers not found])
    fi
    if test "x$with_sdk_name" != x; then
      AC_MSG_WARN([--with-sdk-name will be ignored since a sysroot or devkit is provided])
    fi
    AC_MSG_NOTICE([Setting sysroot from devkit or --with-sysroot])
  else
    if test "x$XCODEBUILD" != x; then
      SYSROOT=`"$XCODEBUILD" -sdk "$SDK_NAME" -version | $GREP '^Path: ' | $SED 's/Path: //'`
      if test "x$SYSROOT" = x; then
        AC_MSG_ERROR([No sysroot found for SDK $SDK_NAME from xcodebuild. Check your Xcode installation.])
      fi
      if ! test -f "$SYSROOT/System/Library/Frameworks/Foundation.framework/Headers/Foundation.h"; then
        AC_MSG_ERROR([Unable to find required framework headers, provide a path to an SDK via --with-sysroot or --with-sdk-name and be sure Xcode is installed properly])
      fi
      AC_MSG_NOTICE([Setting sysroot from xcodebuild with SDK $SDK_NAME])
    else
      UTIL_LOOKUP_PROGS(XCRUN, xcrun)
      if test "x$XCRUN" != x; then
        XCRUN_SDK_PATH=`"$XCRUN" --show-sdk-path -sdk "$SDK_NAME"`
      fi

      if test "x$XCRUN_SDK_PATH" != x && test -f "$XCRUN_SDK_PATH/System/Library/Frameworks/Foundation.framework/Headers/Foundation.h"; then
        AC_MSG_NOTICE([Setting sysroot from xcrun with SDK $SDK_NAME])
        SYSROOT="$XCRUN_SDK_PATH"
      elif test -f /System/Library/Frameworks/Foundation.framework/Headers/Foundation.h; then
        AC_MSG_WARN([No devkit provided and no xcodebuild found. Proceeding using system headers.])
        if test "x$with_sdk_name" != x; then
          AC_MSG_WARN([--with-sdk-name will be ignored since no xcodebuild could be found])
        fi
      else
        AC_MSG_NOTICE([No devkit provided, no xcodebuild tool and no system headers found in the system.])
        AC_MSG_NOTICE([Check that Xcode is properly installed, or set a devkit with --with-devkit,])
        AC_MSG_NOTICE([or override SDK selection using --with-sysroot or --with-sdk-name.])
        AC_MSG_ERROR([Cannot continue])
      fi
    fi
  fi
])

###############################################################################
AC_DEFUN_ONCE([BASIC_SETUP_DEVKIT],
[
  AC_ARG_WITH([devkit], [AS_HELP_STRING([--with-devkit],
      [use this devkit for compilers, tools and resources])])

  if test "x$with_devkit" = xyes; then
    AC_MSG_ERROR([--with-devkit must have a value])
  elif test "x$with_devkit" != x && test "x$with_devkit" != xno; then
    UTIL_FIXUP_PATH([with_devkit])
    DEVKIT_ROOT="$with_devkit"
    # Check for a meta data info file in the root of the devkit
    if test -f "$DEVKIT_ROOT/devkit.info"; then
      . $DEVKIT_ROOT/devkit.info
      # This potentially sets the following:
      # A descriptive name of the devkit
      BASIC_EVAL_DEVKIT_VARIABLE([DEVKIT_NAME])
      # Corresponds to --with-extra-path
      BASIC_EVAL_DEVKIT_VARIABLE([DEVKIT_EXTRA_PATH])
      # Corresponds to --with-toolchain-path
      BASIC_EVAL_DEVKIT_VARIABLE([DEVKIT_TOOLCHAIN_PATH])
      # Corresponds to --with-sysroot
      BASIC_EVAL_DEVKIT_VARIABLE([DEVKIT_SYSROOT])

      # Identifies the Visual Studio version in the devkit
      BASIC_EVAL_DEVKIT_VARIABLE([DEVKIT_VS_VERSION])
      # The Visual Studio include environment variable
      BASIC_EVAL_DEVKIT_VARIABLE([DEVKIT_VS_INCLUDE])
      # The Visual Studio lib environment variable
      BASIC_EVAL_DEVKIT_VARIABLE([DEVKIT_VS_LIB])
      # Corresponds to --with-msvcr-dll
      BASIC_EVAL_DEVKIT_VARIABLE([DEVKIT_MSVCR_DLL])
      # Corresponds to --with-vcruntime-1-dll
      BASIC_EVAL_DEVKIT_VARIABLE([DEVKIT_VCRUNTIME_1_DLL])
      # Corresponds to --with-msvcp-dll
      BASIC_EVAL_DEVKIT_VARIABLE([DEVKIT_MSVCP_DLL])
      # Corresponds to --with-ucrt-dll-dir
      BASIC_EVAL_DEVKIT_VARIABLE([DEVKIT_UCRT_DLL_DIR])
    fi

    AC_MSG_CHECKING([for devkit])
    if test "x$DEVKIT_NAME" != x; then
      AC_MSG_RESULT([$DEVKIT_NAME in $DEVKIT_ROOT])
    else
      AC_MSG_RESULT([$DEVKIT_ROOT])
    fi

    UTIL_PREPEND_TO_PATH([EXTRA_PATH],$DEVKIT_EXTRA_PATH)

    # Fallback default of just /bin if DEVKIT_PATH is not defined
    if test "x$DEVKIT_TOOLCHAIN_PATH" = x; then
      DEVKIT_TOOLCHAIN_PATH="$DEVKIT_ROOT/bin"
    fi
    UTIL_PREPEND_TO_PATH([TOOLCHAIN_PATH],$DEVKIT_TOOLCHAIN_PATH)

    # If DEVKIT_SYSROOT is set, use that, otherwise try a couple of known
    # places for backwards compatibility.
    if test "x$DEVKIT_SYSROOT" != x; then
      SYSROOT="$DEVKIT_SYSROOT"
    elif test -d "$DEVKIT_ROOT/$host_alias/libc"; then
      SYSROOT="$DEVKIT_ROOT/$host_alias/libc"
    elif test -d "$DEVKIT_ROOT/$host/sys-root"; then
      SYSROOT="$DEVKIT_ROOT/$host/sys-root"
    fi

    if test "x$DEVKIT_ROOT" != x; then
      DEVKIT_LIB_DIR="$DEVKIT_ROOT/lib"
      if test "x$OPENJDK_TARGET_CPU_BITS" = x64; then
        DEVKIT_LIB_DIR="$DEVKIT_ROOT/lib64"
      fi
      AC_SUBST(DEVKIT_LIB_DIR)
    fi
  fi

  # You can force the sysroot if the sysroot encoded into the compiler tools
  # is not correct.
  AC_ARG_WITH(sys-root, [AS_HELP_STRING([--with-sys-root],
      [alias for --with-sysroot for backwards compatibility])],
      [SYSROOT=$with_sys_root]
  )

  AC_ARG_WITH(sysroot, [AS_HELP_STRING([--with-sysroot],
      [use this directory as sysroot])],
      [SYSROOT=$with_sysroot]
  )

  AC_ARG_WITH([tools-dir], [AS_HELP_STRING([--with-tools-dir],
      [alias for --with-toolchain-path for backwards compatibility])],
      [UTIL_PREPEND_TO_PATH([TOOLCHAIN_PATH],$with_tools_dir)]
  )

  AC_ARG_WITH([toolchain-path], [AS_HELP_STRING([--with-toolchain-path],
      [prepend these directories when searching for toolchain binaries (compilers etc)])],
      [UTIL_PREPEND_TO_PATH([TOOLCHAIN_PATH],$with_toolchain_path)]
  )

  AC_ARG_WITH([xcode-path], [AS_HELP_STRING([--with-xcode-path],
      [set up toolchain on Mac OS using a path to an Xcode installation])])

  if test "x$with_xcode_path" != x; then
    if test "x$OPENJDK_BUILD_OS" = "xmacosx"; then
      UTIL_PREPEND_TO_PATH([TOOLCHAIN_PATH],
          $with_xcode_path/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin:$with_xcode_path/Contents/Developer/usr/bin)
    else
      AC_MSG_WARN([Option --with-xcode-path is only valid on Mac OS, ignoring.])
    fi
  fi

  AC_MSG_CHECKING([for toolchain path])
  AC_MSG_RESULT([$TOOLCHAIN_PATH])
  AC_SUBST(TOOLCHAIN_PATH)

  AC_ARG_WITH([extra-path], [AS_HELP_STRING([--with-extra-path],
      [prepend these directories to the default path])],
      [UTIL_PREPEND_TO_PATH([EXTRA_PATH],$with_extra_path)]
  )

  if test "x$OPENJDK_BUILD_OS" = "xmacosx"; then
    BASIC_SETUP_XCODE_SYSROOT
  fi

  # Prepend the extra path to the global path
  UTIL_PREPEND_TO_PATH([PATH],$EXTRA_PATH)

  UTIL_FIXUP_PATH([SYSROOT])

  AC_MSG_CHECKING([for sysroot])
  AC_MSG_RESULT([$SYSROOT])
  AC_SUBST(SYSROOT)

  AC_MSG_CHECKING([for extra path])
  AC_MSG_RESULT([$EXTRA_PATH])
])

###############################################################################
AC_DEFUN_ONCE([BASIC_SETUP_OUTPUT_DIR],
[

  AC_ARG_WITH(conf-name, [AS_HELP_STRING([--with-conf-name],
      [use this as the name of the configuration @<:@generated from important configuration options@:>@])],
      [ CONF_NAME=${with_conf_name} ])

  # Test from where we are running configure, in or outside of src root.
  AC_MSG_CHECKING([where to store configuration])
  if test "x$CONFIGURE_START_DIR" = "x$TOPDIR" \
      || test "x$CONFIGURE_START_DIR" = "x$CUSTOM_ROOT" \
      || test "x$CONFIGURE_START_DIR" = "x$TOPDIR/make/autoconf" \
      || test "x$CONFIGURE_START_DIR" = "x$TOPDIR/make" ; then
    # We are running configure from the src root.
    # Create a default ./build/target-variant-debuglevel output root.
    if test "x${CONF_NAME}" = x; then
      AC_MSG_RESULT([in default location])
      CONF_NAME="${OPENJDK_TARGET_OS}-${OPENJDK_TARGET_CPU}-${JVM_VARIANTS_WITH_AND}-${DEBUG_LEVEL}"
    else
      AC_MSG_RESULT([in build directory with custom name])
    fi

    OUTPUTDIR="${WORKSPACE_ROOT}/build/${CONF_NAME}"
    $MKDIR -p "$OUTPUTDIR"
    if test ! -d "$OUTPUTDIR"; then
      AC_MSG_ERROR([Could not create build directory $OUTPUTDIR])
    fi
  else
    # We are running configure from outside of the src dir.
    # Then use the current directory as output dir!
    # If configuration is situated in normal build directory, just use the build
    # directory name as configuration name, otherwise use the complete path.
    if test "x${CONF_NAME}" = x; then
      CONF_NAME=`$ECHO $CONFIGURE_START_DIR | $SED -e "s!^${TOPDIR}/build/!!"`
    fi
    OUTPUTDIR="$CONFIGURE_START_DIR"
    AC_MSG_RESULT([in current directory])

    # WARNING: This might be a bad thing to do. You need to be sure you want to
    # have a configuration in this directory. Do some sanity checks!

    if test ! -e "$OUTPUTDIR/spec.gmk" && test ! -e "$OUTPUTDIR/configure-support/generated-configure.sh"; then
      # If we have a spec.gmk or configure-support/generated-configure.sh,
      # we have run here before and we are OK. Otherwise, check for other files
      files_present=`$LS $OUTPUTDIR`
      # Configure has already touched config.log and confdefs.h in the current dir when this check
      # is performed.
      filtered_files=`$ECHO "$files_present" \
          | $SED -e 's/config.log//g' \
              -e 's/configure.log//g' \
              -e 's/confdefs.h//g' \
              -e 's/configure-support//g' \
              -e 's/ //g' \
          | $TR -d '\n'`
      if test "x$filtered_files" != x; then
        AC_MSG_NOTICE([Current directory is $CONFIGURE_START_DIR.])
        AC_MSG_NOTICE([Since this is not the source root, configure will output the configuration here])
        AC_MSG_NOTICE([(as opposed to creating a configuration in <src_root>/build/<conf-name>).])
        AC_MSG_NOTICE([However, this directory is not empty, additionally to some allowed files])
        AC_MSG_NOTICE([it contains $filtered_files.])
        AC_MSG_NOTICE([This is not allowed, since it could seriously mess up just about everything.])
        AC_MSG_NOTICE([Try 'cd $TOPDIR' and restart configure])
        AC_MSG_NOTICE([(or create a new empty directory and cd to it).])
        AC_MSG_ERROR([Will not continue creating configuration in $CONFIGURE_START_DIR])
      fi
    fi
  fi
  AC_MSG_CHECKING([what configuration name to use])
  AC_MSG_RESULT([$CONF_NAME])

  BASIC_WINDOWS_VERIFY_DIR($OUTPUTDIR, output)
  UTIL_FIXUP_PATH(OUTPUTDIR)

  CONFIGURESUPPORT_OUTPUTDIR="$OUTPUTDIR/configure-support"
  $MKDIR -p "$CONFIGURESUPPORT_OUTPUTDIR"

  SPEC="$OUTPUTDIR/spec.gmk"
  AC_SUBST(SPEC)
  AC_SUBST(CONF_NAME)
  AC_SUBST(OUTPUTDIR)
  AC_SUBST(CONFIGURESUPPORT_OUTPUTDIR)

  # The spec.gmk file contains all variables for the make system.
  AC_CONFIG_FILES([$OUTPUTDIR/spec.gmk:$AUTOCONF_DIR/spec.gmk.in])
  # The bootcycle-spec.gmk file contains support for boot cycle builds.
  AC_CONFIG_FILES([$OUTPUTDIR/bootcycle-spec.gmk:$AUTOCONF_DIR/bootcycle-spec.gmk.in])
  # The buildjdk-spec.gmk file contains support for building a buildjdk when cross compiling.
  AC_CONFIG_FILES([$OUTPUTDIR/buildjdk-spec.gmk:$AUTOCONF_DIR/buildjdk-spec.gmk.in])
  # The compare.sh is used to compare the build output to other builds.
  AC_CONFIG_FILES([$OUTPUTDIR/compare.sh:$AUTOCONF_DIR/compare.sh.in])
  # The generated Makefile knows where the spec.gmk is and where the source is.
  # You can run make from the OUTPUTDIR, or from the top-level Makefile
  # which will look for generated configurations
  AC_CONFIG_FILES([$OUTPUTDIR/Makefile:$AUTOCONF_DIR/Makefile.in])
])

###############################################################################
# Check if build directory is on local disk. If not possible to determine,
# we prefer to claim it's local.
# Argument 1: directory to test
# Argument 2: what to do if it is on local disk
# Argument 3: what to do otherwise (remote disk or failure)
AC_DEFUN([BASIC_CHECK_DIR_ON_LOCAL_DISK],
[
  # df -l lists only local disks; if the given directory is not found then
  # a non-zero exit code is given
  if test "x$DF" = x; then
    # No df here, say it's local
    $2
  else
    # JDK-8189619
    # df on AIX does not understand -l. On modern AIXes it understands "-T local" which
    # is the same. On older AIXes we just continue to live with a "not local build" warning.
    if test "x$OPENJDK_TARGET_OS" = xaix; then
      if $DF -T local > /dev/null 2>&1; then
        DF_LOCAL_ONLY_OPTION='-T local'
      else # AIX may use GNU-utils instead
        DF_LOCAL_ONLY_OPTION='-l'
      fi
    elif test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.wsl1"; then
      # In WSL1, we can only build on a drvfs file system (that is, a mounted real Windows drive)
      DF_LOCAL_ONLY_OPTION='-t drvfs'
    else
      DF_LOCAL_ONLY_OPTION='-l'
    fi
    if $DF $DF_LOCAL_ONLY_OPTION $1 > /dev/null 2>&1; then
      $2
    else
      $3
    fi
  fi
])

###############################################################################
# Check that source files have basic read permissions set. This might
# not be the case in cygwin in certain conditions.
AC_DEFUN_ONCE([BASIC_CHECK_SRC_PERMS],
[
  if test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.cygwin"; then
    # The choice of file here is somewhat arbitrary, it just needs to be there
    # in the source tree when configure runs
    file_to_test="$TOPDIR/Makefile"
    if test `$STAT -c '%a' "$file_to_test"` -lt 400; then
      AC_MSG_ERROR([Bad file permissions on src files. This is usually caused by cloning the repositories with non cygwin tools in a directory not created in cygwin.])
    fi
  fi
])

###############################################################################
AC_DEFUN_ONCE([BASIC_TEST_USABILITY_ISSUES],
[
  AC_MSG_CHECKING([if build directory is on local disk])
  BASIC_CHECK_DIR_ON_LOCAL_DISK($OUTPUTDIR,
      [OUTPUT_DIR_IS_LOCAL="yes"],
      [OUTPUT_DIR_IS_LOCAL="no"])
  AC_MSG_RESULT($OUTPUT_DIR_IS_LOCAL)

  BASIC_CHECK_SRC_PERMS

  # Check if the user has any old-style ALT_ variables set.
  FOUND_ALT_VARIABLES=`env | grep ^ALT_`

  # Before generating output files, test if they exist. If they do, this is a reconfigure.
  # Since we can't properly handle the dependencies for this, warn the user about the situation
  if test -e $OUTPUTDIR/spec.gmk; then
    IS_RECONFIGURE=yes
  else
    IS_RECONFIGURE=no
  fi
])

################################################################################
#
# Default make target
#
AC_DEFUN_ONCE([BASIC_SETUP_DEFAULT_MAKE_TARGET],
[
  AC_ARG_WITH(default-make-target, [AS_HELP_STRING([--with-default-make-target],
      [set the default make target @<:@exploded-image@:>@])])
  if test "x$with_default_make_target" = "x" \
      || test "x$with_default_make_target" = "xyes"; then
    DEFAULT_MAKE_TARGET="exploded-image"
  elif test "x$with_default_make_target" = "xno"; then
    AC_MSG_ERROR([--without-default-make-target is not a valid option])
  else
    DEFAULT_MAKE_TARGET="$with_default_make_target"
  fi

  AC_SUBST(DEFAULT_MAKE_TARGET)
])

###############################################################################
# Setup the default value for LOG=
#
AC_DEFUN_ONCE([BASIC_SETUP_DEFAULT_LOG],
[
  AC_ARG_WITH(log, [AS_HELP_STRING([--with-log],
      [[default value for make LOG argument [warn]]])])
  AC_MSG_CHECKING([for default LOG value])
  if test "x$with_log" = x; then
    DEFAULT_LOG=""
  else
    # Syntax for valid LOG options is a bit too complex for it to be worth
    # implementing a test for correctness in configure. Just accept it.
    DEFAULT_LOG=$with_log
  fi
  AC_MSG_RESULT([$DEFAULT_LOG])
  AC_SUBST(DEFAULT_LOG)
])

###############################################################################
# Code to run after AC_OUTPUT
AC_DEFUN_ONCE([BASIC_POST_CONFIG_OUTPUT],
[
  # Try to move config.log (generated by autoconf) to the configure-support directory.
  if test -e ./config.log; then
    $MV -f ./config.log "$CONFIGURESUPPORT_OUTPUTDIR/config.log" 2> /dev/null
  fi

  # Rotate our log file (configure.log)
  if test -e "$OUTPUTDIR/configure.log.old"; then
    $RM -f "$OUTPUTDIR/configure.log.old"
  fi
  if test -e "$OUTPUTDIR/configure.log"; then
    $MV -f "$OUTPUTDIR/configure.log" "$OUTPUTDIR/configure.log.old" 2> /dev/null
  fi

  # Move configure.log from current directory to the build output root
  if test -e ./configure.log; then
    $MV -f ./configure.log "$OUTPUTDIR/configure.log" 2> /dev/null
  fi

  # Make the compare script executable
  $CHMOD +x $OUTPUTDIR/compare.sh
])
