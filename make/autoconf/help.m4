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

AC_DEFUN_ONCE([HELP_SETUP_DEPENDENCY_HELP],
[
  UTIL_LOOKUP_PROGS(PKGHANDLER, zypper apt-get yum brew port pkgutil pkgadd pacman apk)
])

AC_DEFUN([HELP_MSG_MISSING_DEPENDENCY],
[
  # Print a helpful message on how to acquire the necessary build dependency.
  # $1 is the help tag: cups, alsa etc
  MISSING_DEPENDENCY=$1

  if test "x$MISSING_DEPENDENCY" = "xopenjdk"; then
    HELP_MSG="OpenJDK distributions are available at http://jdk.java.net/."
  elif test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.cygwin"; then
    cygwin_help $MISSING_DEPENDENCY
  else
    PKGHANDLER_COMMAND=

    case $PKGHANDLER in
      *apt-get)
        apt_help     $MISSING_DEPENDENCY ;;
      *yum)
        yum_help     $MISSING_DEPENDENCY ;;
      *brew)
        brew_help    $MISSING_DEPENDENCY ;;
      *port)
        port_help    $MISSING_DEPENDENCY ;;
      *pkgutil)
        pkgutil_help $MISSING_DEPENDENCY ;;
      *pkgadd)
        pkgadd_help  $MISSING_DEPENDENCY ;;
      *zypper)
        zypper_help  $MISSING_DEPENDENCY ;;
      *pacman)
        pacman_help  $MISSING_DEPENDENCY ;;
      *apk)
        apk_help     $MISSING_DEPENDENCY ;;
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
    i686-w64-mingw32-gcc)
      PKGHANDLER_COMMAND="( cd <location of cygwin setup.exe> && cmd /c setup -q -P gcc-core i686-w64-mingw32-gcc-core mingw64-i686-glib2.0 )"
      HELP_MSG="You might be able to fix this by running '$PKGHANDLER_COMMAND'."
      ;;
    x86_64-w64-mingw32-gcc)
      PKGHANDLER_COMMAND="( cd <location of cygwin setup.exe> && cmd /c setup -q -P gcc-core x86_64-w64-mingw32-gcc-core mingw64-x86_64-glib2.0 )"
      HELP_MSG="You might be able to fix this by running '$PKGHANDLER_COMMAND'."
      ;;
  esac
}

apt_help() {
  case $1 in
    reduced)
      PKGHANDLER_COMMAND="sudo apt-get install gcc-multilib g++-multilib" ;;
    devkit)
      PKGHANDLER_COMMAND="sudo apt-get install build-essential" ;;
    alsa)
      PKGHANDLER_COMMAND="sudo apt-get install libasound2-dev" ;;
    cups)
      PKGHANDLER_COMMAND="sudo apt-get install libcups2-dev" ;;
    fontconfig)
      PKGHANDLER_COMMAND="sudo apt-get install libfontconfig1-dev" ;;
    freetype)
      PKGHANDLER_COMMAND="sudo apt-get install libfreetype6-dev" ;;
    harfbuzz)
      PKGHANDLER_COMMAND="sudo apt-get install libharfbuzz-dev" ;;
    ffi)
      PKGHANDLER_COMMAND="sudo apt-get install libffi-dev" ;;
    x11)
      PKGHANDLER_COMMAND="sudo apt-get install libx11-dev libxext-dev libxrender-dev libxrandr-dev libxtst-dev libxt-dev" ;;
    ccache)
      PKGHANDLER_COMMAND="sudo apt-get install ccache" ;;
    dtrace)
      PKGHANDLER_COMMAND="sudo apt-get install systemtap-sdt-dev" ;;
    capstone)
      PKGHANDLER_COMMAND="sudo apt-get install libcapstone-dev" ;;
  esac
}

zypper_help() {
  case $1 in
    devkit)
      PKGHANDLER_COMMAND="sudo zypper install gcc gcc-c++" ;;
    alsa)
      PKGHANDLER_COMMAND="sudo zypper install alsa-devel" ;;
    cups)
      PKGHANDLER_COMMAND="sudo zypper install cups-devel" ;;
    fontconfig)
      PKGHANDLER_COMMAND="sudo zypper install fontconfig-devel" ;;
    freetype)
      PKGHANDLER_COMMAND="sudo zypper install freetype-devel" ;;
    harfbuzz)
      PKGHANDLER_COMMAND="sudo zypper install harfbuzz-devel" ;;
    x11)
      PKGHANDLER_COMMAND="sudo zypper install libX11-devel libXext-devel libXrender-devel libXrandr-devel libXtst-devel libXt-devel libXi-devel" ;;
    ccache)
      PKGHANDLER_COMMAND="sudo zypper install ccache" ;;
  esac
}

yum_help() {
  case $1 in
    devkit)
      PKGHANDLER_COMMAND="sudo yum groupinstall \"Development Tools\"" ;;
    alsa)
      PKGHANDLER_COMMAND="sudo yum install alsa-lib-devel" ;;
    cups)
      PKGHANDLER_COMMAND="sudo yum install cups-devel" ;;
    fontconfig)
      PKGHANDLER_COMMAND="sudo yum install fontconfig-devel" ;;
    freetype)
      PKGHANDLER_COMMAND="sudo yum install freetype-devel" ;;
    harfbuzz)
      PKGHANDLER_COMMAND="sudo yum install harfbuzz-devel" ;;
    x11)
      PKGHANDLER_COMMAND="sudo yum install libXtst-devel libXt-devel libXrender-devel libXrandr-devel libXi-devel" ;;
    ccache)
      PKGHANDLER_COMMAND="sudo yum install ccache" ;;
  esac
}

brew_help() {
  case $1 in
    freetype)
      PKGHANDLER_COMMAND="brew install freetype" ;;
    ccache)
      PKGHANDLER_COMMAND="brew install ccache" ;;
    capstone)
      PKGHANDLER_COMMAND="brew install capstone" ;;
  esac
}

pacman_help() {
  case $1 in
    unzip)
      PKGHANDLER_COMMAND="sudo pacman -S unzip" ;;
    zip)
      PKGHANDLER_COMMAND="sudo pacman -S zip" ;;
    make)
      PKGHANDLER_COMMAND="sudo pacman -S make" ;;
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

apk_help() {
  case $1 in
    devkit)
      PKGHANDLER_COMMAND="sudo apk add alpine-sdk linux-headers" ;;
    alsa)
      PKGHANDLER_COMMAND="sudo apk add alsa-lib-dev" ;;
    cups)
      PKGHANDLER_COMMAND="sudo apk add cups-dev" ;;
    fontconfig)
      PKGHANDLER_COMMAND="sudo apk add fontconfig-dev" ;;
    freetype)
      PKGHANDLER_COMMAND="sudo apk add freetype-dev" ;;
    harfbuzz)
      PKGHANDLER_COMMAND="sudo apk add harfbuzz-dev" ;;
    x11)
      PKGHANDLER_COMMAND="sudo apk add libxtst-dev libxt-dev libxrender-dev libxrandr-dev" ;;
    ccache)
      PKGHANDLER_COMMAND="sudo apk add ccache" ;;
  esac
}

# This function will check if we're called from the "configure" wrapper while
# printing --help. If so, we will print out additional information that can
# only be extracted within the autoconf script, and then exit. This must be
# called at the very beginning in configure.ac.
AC_DEFUN_ONCE([HELP_PRINT_ADDITIONAL_HELP_AND_EXIT],
[
  if test "x$CONFIGURE_PRINT_ADDITIONAL_HELP" != x; then

    # Print available toolchains
    $ECHO "The following toolchains are valid as arguments to --with-toolchain-type."
    $ECHO "Which are available to use depends on the build platform."
    for toolchain in $VALID_TOOLCHAINS_all; do
      # Use indirect variable referencing
      toolchain_var_name=TOOLCHAIN_DESCRIPTION_$toolchain
      TOOLCHAIN_DESCRIPTION=${!toolchain_var_name}
      $PRINTF "  %-22s  %s\n" $toolchain "$TOOLCHAIN_DESCRIPTION"
    done
    $ECHO ""

    # Print available JVM features
    $ECHO "The following JVM features are valid as arguments to --with-jvm-features."
    $ECHO "Which are available to use depends on the environment and JVM variant."
    m4_foreach(FEATURE, m4_split(jvm_features_valid), [
      # Create an m4 variable containing the description for FEATURE.
      m4_define(FEATURE_DESCRIPTION, [jvm_feature_desc_]m4_translit(FEATURE, -, _))
      $PRINTF "  %-22s  %s\n" FEATURE "FEATURE_DESCRIPTION"
      m4_undefine([FEATURE_DESCRIPTION])
    ])

    # And now exit directly
    exit 0
  fi
])

AC_DEFUN_ONCE([HELP_PRINT_SUMMARY_AND_WARNINGS],
[
  # Finally output some useful information to the user

  $ECHO ""
  $ECHO "===================================================="
  if test "x$no_create" != "xyes"; then
    if test "x$IS_RECONFIGURE" != "xyes"; then
      $ECHO "A new configuration has been successfully created in"
      $ECHO "$OUTPUTDIR"
    else
      $ECHO "The existing configuration has been successfully updated in"
      $ECHO "$OUTPUTDIR"
    fi
  else
    if test "x$IS_RECONFIGURE" != "xyes"; then
      $ECHO "A configuration has been successfully checked but not created"
    else
      $ECHO "The existing configuration has been successfully checked in"
      $ECHO "$OUTPUTDIR"
    fi
  fi
  if test "x$CONFIGURE_COMMAND_LINE" != x; then
    $ECHO "using configure arguments '$CONFIGURE_COMMAND_LINE'."
  else
    $ECHO "using default settings."
  fi

  if test "x$REAL_CONFIGURE_COMMAND_EXEC_FULL" != x; then
    $ECHO ""
    $ECHO "The original configure invocation was '$REAL_CONFIGURE_COMMAND_EXEC_SHORT $REAL_CONFIGURE_COMMAND_LINE'."
  fi

  $ECHO ""
  $ECHO "Configuration summary:"
  $ECHO "* Name:           $CONF_NAME"
  $ECHO "* Debug level:    $DEBUG_LEVEL"
  $ECHO "* HS debug level: $HOTSPOT_DEBUG_LEVEL"
  $ECHO "* JVM variants:   $JVM_VARIANTS"
  $PRINTF "* JVM features:   "

  for variant in $JVM_VARIANTS; do
    features_var_name=JVM_FEATURES_$variant
    JVM_FEATURES_FOR_VARIANT=${!features_var_name}
    $PRINTF "%s: \'%s\' " "$variant" "$JVM_FEATURES_FOR_VARIANT"
  done
  $ECHO ""

  $ECHO "* OpenJDK target: OS: $OPENJDK_TARGET_OS, CPU architecture: $OPENJDK_TARGET_CPU_ARCH, address length: $OPENJDK_TARGET_CPU_BITS"
  $ECHO "* Version string: $VERSION_STRING ($VERSION_SHORT)"

  if test "x$SOURCE_DATE" != xupdated; then
    source_date_info="$SOURCE_DATE ($SOURCE_DATE_ISO_8601)"
  else
    source_date_info="Determined at build time"
  fi
  $ECHO "* Source date:    $source_date_info"

  $ECHO ""
  $ECHO "Tools summary:"
  if test "x$OPENJDK_BUILD_OS" = "xwindows"; then
    $ECHO "* Environment:    $WINENV_VENDOR version $WINENV_VERSION; windows version $WINDOWS_VERSION; prefix \"$WINENV_PREFIX\"; root \"$WINENV_ROOT\""
  fi
  $ECHO "* Boot JDK:       $BOOT_JDK_VERSION (at $BOOT_JDK)"
  $ECHO "* Toolchain:      $TOOLCHAIN_TYPE ($TOOLCHAIN_DESCRIPTION)"
  if test "x$DEVKIT_NAME" != x; then
    $ECHO "* Devkit:         $DEVKIT_NAME ($DEVKIT_ROOT)"
  elif test "x$DEVKIT_ROOT" != x; then
    $ECHO "* Devkit:         $DEVKIT_ROOT"
  elif test "x$SYSROOT" != x; then
    $ECHO "* Sysroot:        $SYSROOT"
  fi
  $ECHO "* C Compiler:     Version $CC_VERSION_NUMBER (at ${CC#"$FIXPATH "})"
  $ECHO "* C++ Compiler:   Version $CXX_VERSION_NUMBER (at ${CXX#"$FIXPATH "})"

  $ECHO ""
  $ECHO "Build performance summary:"
  $ECHO "* Build jobs:     $JOBS"
  $ECHO "* Memory limit:   $MEMORY_SIZE MB"
  if test "x$CCACHE_STATUS" != "x"; then
    $ECHO "* ccache status:  $CCACHE_STATUS"
  fi
  $ECHO ""

  if test "x$BUILDING_MULTIPLE_JVM_VARIANTS" = "xtrue"; then
    $ECHO "NOTE: You have requested to build more than one version of the JVM, which"
    $ECHO "will result in longer build times."
    $ECHO ""
  fi

  if test "x$OUTPUT_DIR_IS_LOCAL" != "xyes"; then
    $ECHO "WARNING: Your build output directory is not on a local disk."
    $ECHO "This will severely degrade build performance!"
    $ECHO "It is recommended that you create an output directory on a local disk,"
    $ECHO "and run the configure script again from that directory."
    $ECHO ""
  fi

  if test "x$IS_RECONFIGURE" = "xyes" && test "x$no_create" != "xyes"; then
    $ECHO "WARNING: The result of this configuration has overridden an older"
    $ECHO "configuration. You *should* run 'make clean' to make sure you get a"
    $ECHO "proper build. Failure to do so might result in strange build problems."
    $ECHO ""
  fi

  if test "x$IS_RECONFIGURE" != "xyes" && test "x$no_create" = "xyes"; then
    $ECHO "WARNING: The result of this configuration was not saved."
    $ECHO "You should run without '--no-create | -n' to create the configuration."
    $ECHO ""
  fi

  if test "x$UNSUPPORTED_TOOLCHAIN_VERSION" = "xyes"; then
    $ECHO "WARNING: The toolchain version used is known to have issues. Please"
    $ECHO "consider using a supported version unless you know what you are doing."
    $ECHO ""
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
      $ECHO "The following warnings were produced. Repeated here for convenience:"
      # We must quote sed expression (using []) to stop m4 from eating the [].
      $GREP '^configure:.*: WARNING:' "$CONFIG_LOG_PATH/config.log" | $SED -e [ 's/^configure:[0-9]*: //' ]
      $ECHO ""
    fi
  fi
])
