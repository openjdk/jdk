#
# Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
      if test "x$OPENJDK_TARGET_CPU_BITS" = x32; then
        HELP_MSG="To install freetype, run:
wget \"http://gnuwin32.sourceforge.net/downlinks/freetype.php\" -O /tmp/freetype-setup.exe
chmod +x /tmp/freetype-setup.exe
/tmp/freetype-setup.exe
Follow GUI prompts, and install to default directory \"C:\Program Files (x86)\GnuWin32\".
After installation, locate lib/libfreetype.dll.a and make a copy with the name freetype.dll."
      else
        HELP_MSG="You need to build a 64-bit version of freetype.
This is not readily available.
You can find source code and build instructions on
http://www.freetype.org/
If you put the resulting build in \"C:\Program Files\GnuWin32\", it will be found automatically."
      fi
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
      PKGHANDLER_COMMAND="sudo yum install libXtst-devel libXt-devel libXrender-devel" ;;
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

AC_DEFUN_ONCE([HELP_PRINT_SUMMARY_AND_WARNINGS],
[
  # Finally output some useful information to the user

  if test "x$CCACHE_FOUND" != x; then
    if  test "x$HAS_GOOD_CCACHE" = x; then
      CCACHE_STATUS="installed, but disabled (version older than 3.1.4)"
      CCACHE_HELP_MSG="You have ccache installed, but it is a version prior to 3.1.4. Try upgrading."
    else
      CCACHE_STATUS="installed and in use"
    fi
  else
    if test "x$GCC" = xyes; then
      CCACHE_STATUS="not installed (consider installing)"
      CCACHE_HELP_MSG="You do not have ccache installed. Try installing it."
    else
      CCACHE_STATUS="not available for your system"
    fi
  fi

  printf "\n"
  printf "====================================================\n"
  printf "A new configuration has been successfully created in\n"
  printf "$OUTPUT_ROOT\n"
  if test "x$CONFIGURE_COMMAND_LINE" != x; then
    printf "using configure arguments '$CONFIGURE_COMMAND_LINE'.\n"
  else
    printf "using default settings.\n"
  fi

  printf "\n"
  printf "Configuration summary:\n"
  printf "* Debug level:    $DEBUG_LEVEL\n"
  printf "* JDK variant:    $JDK_VARIANT\n"
  printf "* JVM variants:   $with_jvm_variants\n"
  printf "* OpenJDK target: OS: $OPENJDK_TARGET_OS, CPU architecture: $OPENJDK_TARGET_CPU_ARCH, address length: $OPENJDK_TARGET_CPU_BITS\n"

  printf "\n"
  printf "Tools summary:\n"
  if test "x$OPENJDK_BUILD_OS" = "xwindows"; then
    printf "* Environment:    $WINDOWS_ENV_VENDOR version $WINDOWS_ENV_VERSION (root at $WINDOWS_ENV_ROOT_PATH)\n"
  fi
  printf "* Boot JDK:       $BOOT_JDK_VERSION (at $BOOT_JDK)\n"
  printf "* C Compiler:     $CC_VENDOR version $CC_VERSION (at $CC)\n"
  printf "* C++ Compiler:   $CXX_VENDOR version $CXX_VERSION (at $CXX)\n"

  printf "\n"
  printf "Build performance summary:\n"
  printf "* Cores to use:   $JOBS\n"
  printf "* Memory limit:   $MEMORY_SIZE MB\n"
  printf "* ccache status:  $CCACHE_STATUS\n"
  printf "\n"

  if test "x$CCACHE_HELP_MSG" != x && test "x$HIDE_PERFORMANCE_HINTS" = "xno"; then
    printf "Build performance tip: ccache gives a tremendous speedup for C++ recompilations.\n"
    printf "$CCACHE_HELP_MSG\n"
    HELP_MSG_MISSING_DEPENDENCY([ccache])
    printf "$HELP_MSG\n"
    printf "\n"
  fi

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

  if test "x$IS_RECONFIGURE" = "xyes"; then
    printf "WARNING: The result of this configuration has overridden an older\n"
    printf "configuration. You *should* run 'make clean' to make sure you get a\n"
    printf "proper build. Failure to do so might result in strange build problems.\n"
    printf "\n"
  fi
])
