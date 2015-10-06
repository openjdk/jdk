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

AC_DEFUN_ONCE([HELP_SETUP_DEPENDENCY_HELP],
[
  AC_CHECK_PROGS(PKGHANDLER, apt-get yum port pkgutil pkgadd)
])

AC_DEFUN([HELP_MSG_MISSING_DEPENDENCY],
[
  # Print a helpful message on how to acquire the necessary build dependency.
  # $1 is the help tag: freetype, cups, pulse, alsa etc
  MISSING_DEPENDENCY=$1

  if test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.cygwin"; then
    cygwin_help $MISSING_DEPENDENCY
  elif test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.msys"; then
    msys_help $MISSING_DEPENDENCY
  else
    PKGHANDLER_COMMAND=

    case $PKGHANDLER in
      apt-get)
        apt_help     $MISSING_DEPENDENCY ;;
      yum)
        yum_help     $MISSING_DEPENDENCY ;;
      port)
        port_help    $MISSING_DEPENDENCY ;;
      pkgutil)
        pkgutil_help $MISSING_DEPENDENCY ;;
      pkgadd)
        pkgadd_help  $MISSING_DEPENDENCY ;;
    esac

    if test "x$PKGHANDLER_COMMAND" != x; then
      HELP_MSG="You might be able to fix this by running '$PKGHANDLER_COMMAND'."
    fi
  fi
])

cygwin_help() {
  case $1 in
    unzip)
      PKGHANDLER_COMMAND="( cd <location of cygwin setup.exe> && cmd /c setup -q -P unzip )"
      HELP_MSG="You might be able to fix this by running '$PKGHANDLER_COMMAND'."
      ;;
    zip)
      PKGHANDLER_COMMAND="( cd <location of cygwin setup.exe> && cmd /c setup -q -P zip )"
      HELP_MSG="You might be able to fix this by running '$PKGHANDLER_COMMAND'."
      ;;
    make)
      PKGHANDLER_COMMAND="( cd <location of cygwin setup.exe> && cmd /c setup -q -P make )"
      HELP_MSG="You might be able to fix this by running '$PKGHANDLER_COMMAND'."
      ;;
    freetype)
      HELP_MSG="
The freetype library can now be build during the configure process.
Download the freetype sources and unpack them into an arbitrary directory:

wget http://download.savannah.gnu.org/releases/freetype/freetype-2.5.3.tar.gz
tar -xzf freetype-2.5.3.tar.gz

Then run configure with '--with-freetype-src=<freetype_src>'. This will
automatically build the freetype library into '<freetype_src>/lib64' for 64-bit
builds or into '<freetype_src>/lib32' for 32-bit builds.
Afterwards you can always use '--with-freetype-include=<freetype_src>/include'
and '--with-freetype-lib=<freetype_src>/lib[32|64]' for other builds."
      ;;
  esac
}

msys_help() {
  PKGHANDLER_COMMAND=""
}

apt_help() {
  case $1 in
    devkit)
      PKGHANDLER_COMMAND="sudo apt-get install build-essential" ;;
    openjdk)
      PKGHANDLER_COMMAND="sudo apt-get install openjdk-7-jdk" ;;
    alsa)
      PKGHANDLER_COMMAND="sudo apt-get install libasound2-dev" ;;
    cups)
      PKGHANDLER_COMMAND="sudo apt-get install libcups2-dev" ;;
    freetype)
      PKGHANDLER_COMMAND="sudo apt-get install libfreetype6-dev" ;;
    pulse)
      PKGHANDLER_COMMAND="sudo apt-get install libpulse-dev" ;;
    x11)
      PKGHANDLER_COMMAND="sudo apt-get install libX11-dev libxext-dev libxrender-dev libxtst-dev libxt-dev" ;;
    ccache)
      PKGHANDLER_COMMAND="sudo apt-get install ccache" ;;
  esac
}

yum_help() {
  case $1 in
    devkit)
      PKGHANDLER_COMMAND="sudo yum groupinstall \"Development Tools\"" ;;
    openjdk)
      PKGHANDLER_COMMAND="sudo yum install java-1.7.0-openjdk" ;;
    alsa)
      PKGHANDLER_COMMAND="sudo yum install alsa-lib-devel" ;;
    cups)
      PKGHANDLER_COMMAND="sudo yum install cups-devel" ;;
    freetype)
      PKGHANDLER_COMMAND="sudo yum install freetype-devel" ;;
    pulse)
      PKGHANDLER_COMMAND="sudo yum install pulseaudio-libs-devel" ;;
    x11)
      PKGHANDLER_COMMAND="sudo yum install libXtst-devel libXt-devel libXrender-devel libXi-devel" ;;
    ccache)
      PKGHANDLER_COMMAND="sudo yum install ccache" ;;
  esac
}

port_help() {
  PKGHANDLER_COMMAND=""
}

pkgutil_help() {
  PKGHANDLER_COMMAND=""
}

pkgadd_help() {
  PKGHANDLER_COMMAND=""
}

# This function will check if we're called from the "configure" wrapper while
# printing --help. If so, we will print out additional information that can
# only be extracted within the autoconf script, and then exit. This must be
# called at the very beginning in configure.ac.
AC_DEFUN_ONCE([HELP_PRINT_ADDITIONAL_HELP_AND_EXIT],
[
  if test "x$CONFIGURE_PRINT_TOOLCHAIN_LIST" != x; then
    $PRINTF "The following toolchains are available as arguments to --with-toolchain-type.\n"
    $PRINTF "Which are valid to use depends on the build platform.\n"
    for toolchain in $VALID_TOOLCHAINS_all; do
      # Use indirect variable referencing
      toolchain_var_name=TOOLCHAIN_DESCRIPTION_$toolchain
      TOOLCHAIN_DESCRIPTION=${!toolchain_var_name}
      $PRINTF "  %-10s  %s\n" $toolchain "$TOOLCHAIN_DESCRIPTION"
    done

    # And now exit directly
    exit 0
  fi
])

AC_DEFUN_ONCE([HELP_PRINT_SUMMARY_AND_WARNINGS],
[
  # Finally output some useful information to the user

  printf "\n"
  printf "====================================================\n"
  if test "x$no_create" != "xyes"; then
    if test "x$IS_RECONFIGURE" != "xyes"; then
      printf "A new configuration has been successfully created in\n%s\n" "$OUTPUT_ROOT"
    else
      printf "The existing configuration has been successfully updated in\n%s\n" "$OUTPUT_ROOT"
    fi
  else
    if test "x$IS_RECONFIGURE" != "xyes"; then
      printf "A configuration has been successfully checked but not created\n"
    else
      printf "The existing configuration has been successfully checked in\n%s\n" "$OUTPUT_ROOT"
    fi
  fi
  if test "x$CONFIGURE_COMMAND_LINE" != x; then
    printf "using configure arguments '$CONFIGURE_COMMAND_LINE'.\n"
  else
    printf "using default settings.\n"
  fi

  printf "\n"
  printf "Configuration summary:\n"
  printf "* Debug level:    $DEBUG_LEVEL\n"
  printf "* HS debug level: $HOTSPOT_DEBUG_LEVEL\n"
  printf "* JDK variant:    $JDK_VARIANT\n"
  printf "* JVM variants:   $with_jvm_variants\n"
  printf "* OpenJDK target: OS: $OPENJDK_TARGET_OS, CPU architecture: $OPENJDK_TARGET_CPU_ARCH, address length: $OPENJDK_TARGET_CPU_BITS\n"
  printf "* Version string: $VERSION_STRING ($VERSION_SHORT)\n"

  printf "\n"
  printf "Tools summary:\n"
  if test "x$OPENJDK_BUILD_OS" = "xwindows"; then
    printf "* Environment:    $WINDOWS_ENV_VENDOR version $WINDOWS_ENV_VERSION (root at $WINDOWS_ENV_ROOT_PATH)\n"
  fi
  printf "* Boot JDK:       $BOOT_JDK_VERSION (at $BOOT_JDK)\n"
  if test "x$TOOLCHAIN_VERSION" != "x"; then
    print_version=" $TOOLCHAIN_VERSION"
  fi
  printf "* Toolchain:      $TOOLCHAIN_TYPE ($TOOLCHAIN_DESCRIPTION$print_version)\n"
  printf "* C Compiler:     Version $CC_VERSION_NUMBER (at $CC)\n"
  printf "* C++ Compiler:   Version $CXX_VERSION_NUMBER (at $CXX)\n"

  printf "\n"
  printf "Build performance summary:\n"
  printf "* Cores to use:   $JOBS\n"
  printf "* Memory limit:   $MEMORY_SIZE MB\n"
  if test "x$CCACHE_STATUS" != "x"; then
    printf "* ccache status:  $CCACHE_STATUS\n"
  fi
  printf "\n"

  if test "x$BUILDING_MULTIPLE_JVM_VARIANTS" = "xyes"; then
    printf "NOTE: You have requested to build more than one version of the JVM, which\n"
    printf "will result in longer build times.\n"
    printf "\n"
  fi

  if test "x$FOUND_ALT_VARIABLES" != "x"; then
    printf "WARNING: You have old-style ALT_ environment variables set.\n"
    printf "These are not respected, and will be ignored. It is recommended\n"
    printf "that you clean your environment. The following variables are set:\n"
    printf "$FOUND_ALT_VARIABLES\n"
    printf "\n"
  fi

  if test "x$OUTPUT_DIR_IS_LOCAL" != "xyes"; then
    printf "WARNING: Your build output directory is not on a local disk.\n"
    printf "This will severely degrade build performance!\n"
    printf "It is recommended that you create an output directory on a local disk,\n"
    printf "and run the configure script again from that directory.\n"
    printf "\n"
  fi

  if test "x$IS_RECONFIGURE" = "xyes" && test "x$no_create" != "xyes"; then
    printf "WARNING: The result of this configuration has overridden an older\n"
    printf "configuration. You *should* run 'make clean' to make sure you get a\n"
    printf "proper build. Failure to do so might result in strange build problems.\n"
    printf "\n"
  fi

  if test "x$IS_RECONFIGURE" != "xyes" && test "x$no_create" = "xyes"; then
    printf "WARNING: The result of this configuration was not saved.\n"
    printf "You should run without '--no-create | -n' to create the configuration.\n"
    printf "\n"
  fi
])

AC_DEFUN_ONCE([HELP_REPEAT_WARNINGS],
[
  # Locate config.log.
  if test -e "$CONFIGURESUPPORT_OUTPUTDIR/config.log"; then
    CONFIG_LOG_PATH="$CONFIGURESUPPORT_OUTPUTDIR"
  elif test -e "./config.log"; then
    CONFIG_LOG_PATH="."
  fi

  if test -e "$CONFIG_LOG_PATH/config.log"; then
    $GREP '^configure:.*: WARNING:' "$CONFIG_LOG_PATH/config.log" > /dev/null 2>&1
    if test $? -eq 0; then
      printf "The following warnings were produced. Repeated here for convenience:\n"
      # We must quote sed expression (using []) to stop m4 from eating the [].
      $GREP '^configure:.*: WARNING:' "$CONFIG_LOG_PATH/config.log" | $SED -e [ 's/^configure:[0-9]*: //' ]
      printf "\n"
    fi
  fi
])
