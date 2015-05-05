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

################################################################################
# The order of these defines the priority by which we try to find them.
VALID_VS_VERSIONS="2013 2012 2010"

VS_DESCRIPTION_2010="Microsoft Visual Studio 2010"
VS_VERSION_INTERNAL_2010=100
VS_MSVCR_2010=msvcr100.dll
# We don't use msvcp on Visual Studio 2010
#VS_MSVCP_2010=msvcp100.dll
VS_ENVVAR_2010="VS100COMNTOOLS"
VS_VS_INSTALLDIR_2010="Microsoft Visual Studio 10.0"
VS_SDK_INSTALLDIR_2010="Microsoft SDKs/Windows/v7.1"
VS_VS_PLATFORM_NAME_2010="v100"
VS_SDK_PLATFORM_NAME_2010="Windows7.1SDK"

VS_DESCRIPTION_2012="Microsoft Visual Studio 2012"
VS_VERSION_INTERNAL_2012=110
VS_MSVCR_2012=msvcr110.dll
VS_MSVCP_2012=msvcp110.dll
VS_ENVVAR_2012="VS110COMNTOOLS"
VS_VS_INSTALLDIR_2012="Microsoft Visual Studio 11.0"
VS_SDK_INSTALLDIR_2012=
VS_VS_PLATFORM_NAME_2012="v110"
VS_SDK_PLATFORM_NAME_2012=

VS_DESCRIPTION_2013="Microsoft Visual Studio 2013"
VS_VERSION_INTERNAL_2013=120
VS_MSVCR_2013=msvcr120.dll
VS_MSVCP_2013=msvcp120.dll
VS_ENVVAR_2013="VS120COMNTOOLS"
VS_VS_INSTALLDIR_2013="Microsoft Visual Studio 12.0"
VS_SDK_INSTALLDIR_2013=
VS_VS_PLATFORM_NAME_2013="v120"
VS_SDK_PLATFORM_NAME_2013=

################################################################################

AC_DEFUN([TOOLCHAIN_CHECK_POSSIBLE_VISUAL_STUDIO_ROOT],
[
  if test "x$VS_ENV_CMD" = x; then
    VS_VERSION="$1"
    VS_BASE="$2"
    METHOD="$3"

    if test "x$OPENJDK_TARGET_CPU_BITS" = x32; then
      VCVARSFILE="vc/bin/vcvars32.bat"
    else
      VCVARSFILE="vc/bin/amd64/vcvars64.bat"
    fi

    BASIC_WINDOWS_REWRITE_AS_UNIX_PATH(VS_BASE)
    if test -d "$VS_BASE"; then
      if test -f "$VS_BASE/$VCVARSFILE"; then
        AC_MSG_NOTICE([Found Visual Studio installation at $VS_BASE using $METHOD])
        VS_ENV_CMD="$VS_BASE/$VCVARSFILE"
        # PLATFORM_TOOLSET is used during the compilation of the freetype sources (see
        # 'LIB_BUILD_FREETYPE' in libraries.m4) and must be one of 'v100', 'v110' or 'v120' for VS 2010, 2012 or VS2013
        eval PLATFORM_TOOLSET="\${VS_VS_PLATFORM_NAME_${VS_VERSION}}"
      else
        AC_MSG_NOTICE([Found Visual Studio installation at $VS_BASE using $METHOD])
        AC_MSG_NOTICE([Warning: $VCVARSFILE is missing, this is probably Visual Studio Express. Ignoring])
      fi
    fi
  fi
])

################################################################################

AC_DEFUN([TOOLCHAIN_CHECK_POSSIBLE_WIN_SDK_ROOT],
[
  if test "x$VS_ENV_CMD" = x; then
    VS_VERSION="$1"
    WIN_SDK_BASE="$2"
    METHOD="$3"
    BASIC_WINDOWS_REWRITE_AS_UNIX_PATH(WIN_SDK_BASE)
    if test -d "$WIN_SDK_BASE"; then
      # There have been cases of partial or broken SDK installations. A missing
      # lib dir is not going to work.
      if test ! -d "$WIN_SDK_BASE/lib"; then
        AC_MSG_NOTICE([Found Windows SDK installation at $WIN_SDK_BASE using $METHOD])
        AC_MSG_NOTICE([Warning: Installation is broken, lib dir is missing. Ignoring])
      elif test -f "$WIN_SDK_BASE/Bin/SetEnv.Cmd"; then
        AC_MSG_NOTICE([Found Windows SDK installation at $WIN_SDK_BASE using $METHOD])
        VS_ENV_CMD="$WIN_SDK_BASE/Bin/SetEnv.Cmd"
        if test "x$OPENJDK_TARGET_CPU_BITS" = x32; then
          VS_ENV_ARGS="/x86"
        else
          VS_ENV_ARGS="/x64"
        fi
        # PLATFORM_TOOLSET is used during the compilation of the freetype sources (see
        # 'LIB_BUILD_FREETYPE' in libraries.m4) and must be 'Windows7.1SDK' for Windows7.1SDK
        # TODO: improve detection for other versions of SDK
        eval PLATFORM_TOOLSET="\${VS_SDK_PLATFORM_NAME_${VS_VERSION}}"
      else
        AC_MSG_NOTICE([Found Windows SDK installation at $WIN_SDK_BASE using $METHOD])
        AC_MSG_NOTICE([Warning: Installation is broken, SetEnv.Cmd is missing. Ignoring])
      fi
    fi
  fi
])

################################################################################
# Finds the bat or cmd file in Visual Studio or the SDK that sets up a proper
# build environment and assigns it to VS_ENV_CMD
AC_DEFUN([TOOLCHAIN_FIND_VISUAL_STUDIO_BAT_FILE],
[
  VS_VERSION="$1"
  eval VS_COMNTOOLS_VAR="\${VS_ENVVAR_${VS_VERSION}}"
  eval VS_COMNTOOLS="\$${VS_COMNTOOLS_VAR}"
  eval VS_INSTALL_DIR="\${VS_VS_INSTALLDIR_${VS_VERSION}}"
  eval SDK_INSTALL_DIR="\${VS_SDK_INSTALLDIR_${VS_VERSION}}"

  # When using --with-tools-dir, assume it points to the correct and default
  # version of Visual Studio or that --with-toolchain-version was also set.
  if test "x$with_tools_dir" != x; then
    TOOLCHAIN_CHECK_POSSIBLE_VISUAL_STUDIO_ROOT([${VS_VERSION}],
        [$with_tools_dir/../..], [--with-tools-dir])
    TOOLCHAIN_CHECK_POSSIBLE_VISUAL_STUDIO_ROOT([${VS_VERSION}],
        [$with_tools_dir/../../..], [--with-tools-dir])
    if test "x$VS_ENV_CMD" = x; then
      # Having specified an argument which is incorrect will produce an instant failure;
      # we should not go on looking
      AC_MSG_NOTICE([The path given by --with-tools-dir does not contain a valid])
      AC_MSG_NOTICE([Visual Studio installation. Please point to the VC/bin or VC/bin/amd64])
      AC_MSG_NOTICE([directory within the Visual Studio installation])
      AC_MSG_ERROR([Cannot locate a valid Visual Studio installation])
    fi
  fi

  VS_ENV_CMD=""
  VS_ENV_ARGS=""

  if test "x$VS_COMNTOOLS" != x; then
    TOOLCHAIN_CHECK_POSSIBLE_VISUAL_STUDIO_ROOT([${VS_VERSION}],
        [$VS_COMNTOOLS/../..], [$VS_COMNTOOLS_VAR variable])
  fi
  if test "x$PROGRAMFILES" != x; then
    TOOLCHAIN_CHECK_POSSIBLE_VISUAL_STUDIO_ROOT([${VS_VERSION}],
        [$PROGRAMFILES/$VS_INSTALL_DIR], [well-known name])
  fi
  # Work around the insanely named ProgramFiles(x86) env variable
  PROGRAMFILES_X86="`env | $SED -n 's/^ProgramFiles(x86)=//p'`"
  if test "x$PROGRAMFILES_X86" != x; then
    TOOLCHAIN_CHECK_POSSIBLE_VISUAL_STUDIO_ROOT([${VS_VERSION}],
        [$PROGRAMFILES_X86/$VS_INSTALL_DIR], [well-known name])
  fi
  TOOLCHAIN_CHECK_POSSIBLE_VISUAL_STUDIO_ROOT([${VS_VERSION}],
      [C:/Program Files/$VS_INSTALL_DIR], [well-known name])
  TOOLCHAIN_CHECK_POSSIBLE_VISUAL_STUDIO_ROOT([${VS_VERSION}],
      [C:/Program Files (x86)/$VS_INSTALL_DIR], [well-known name])

  if test "x$SDK_INSTALL_DIR" != x; then
    if test "x$ProgramW6432" != x; then
      TOOLCHAIN_CHECK_POSSIBLE_WIN_SDK_ROOT([${VS_VERSION}],
          [$ProgramW6432/$SDK_INSTALL_DIR], [well-known name])
    fi
    if test "x$PROGRAMW6432" != x; then
      TOOLCHAIN_CHECK_POSSIBLE_WIN_SDK_ROOT([${VS_VERSION}],
          [$PROGRAMW6432/$SDK_INSTALL_DIR], [well-known name])
    fi
    if test "x$PROGRAMFILES" != x; then
      TOOLCHAIN_CHECK_POSSIBLE_WIN_SDK_ROOT([${VS_VERSION}],
          [$PROGRAMFILES/$SDK_INSTALL_DIR], [well-known name])
    fi
    TOOLCHAIN_CHECK_POSSIBLE_WIN_SDK_ROOT([${VS_VERSION}],
        [C:/Program Files/$SDK_INSTALL_DIR], [well-known name])
    TOOLCHAIN_CHECK_POSSIBLE_WIN_SDK_ROOT([${VS_VERSION}],
        [C:/Program Files (x86)/$SDK_INSTALL_DIR], [well-known name])
  fi
])

################################################################################

AC_DEFUN([TOOLCHAIN_FIND_VISUAL_STUDIO],
[
  AC_ARG_WITH(toolchain-version, [AS_HELP_STRING([--with-toolchain-version],
      [the version of the toolchain to look for, use '--help' to show possible values @<:@platform dependent@:>@])])

  if test "x$with_toolchain_version" = xlist; then
    # List all toolchains
    AC_MSG_NOTICE([The following toolchain versions are valid on this platform:])
    for version in $VALID_VS_VERSIONS; do
      eval VS_DESCRIPTION=\${VS_DESCRIPTION_$version}
      $PRINTF "  %-10s  %s\n" $version "$VS_DESCRIPTION"
    done

    exit 0
  elif test "x$DEVKIT_VS_VERSION" != x; then
    VS_VERSION=$DEVKIT_VS_VERSION
    TOOLCHAIN_VERSION=$VS_VERSION
    eval VS_DESCRIPTION="\${VS_DESCRIPTION_${VS_VERSION}}"
    eval VS_VERSION_INTERNAL="\${VS_VERSION_INTERNAL_${VS_VERSION}}"
    eval MSVCR_NAME="\${VS_MSVCR_${VS_VERSION}}"
    eval MSVCP_NAME="\${VS_MSVCP_${VS_VERSION}}"
    eval PLATFORM_TOOLSET="\${VS_VS_PLATFORM_NAME_${VS_VERSION}}"
    VS_PATH="$TOOLCHAIN_PATH:$PATH"

    # Convert DEVKIT_VS_INCLUDE into windows style VS_INCLUDE so that it
    # can still be exported as INCLUDE for compiler invocations without
    # SYSROOT_CFLAGS
    OLDIFS="$IFS"
    IFS=";"
    for i in $DEVKIT_VS_INCLUDE; do
      ipath=$i
      BASIC_WINDOWS_REWRITE_AS_WINDOWS_MIXED_PATH([ipath])
      VS_INCLUDE="$VS_INCLUDE;$ipath"
    done
    # Convert DEVKIT_VS_LIB into VS_LIB so that it can still be exported
    # as LIB for compiler invocations without SYSROOT_LDFLAGS
    for i in $DEVKIT_VS_LIB; do
      libpath=$i
      BASIC_WINDOWS_REWRITE_AS_WINDOWS_MIXED_PATH([libpath])
      VS_LIB="$VS_LIB;$libpath"
    done
    IFS="$OLDIFS"

    AC_MSG_NOTICE([Found devkit $VS_DESCRIPTION])

  elif test "x$with_toolchain_version" != x; then
    # User override; check that it is valid
    if test "x${VALID_VS_VERSIONS/$with_toolchain_version/}" = "x${VALID_VS_VERSIONS}"; then
      AC_MSG_NOTICE([Visual Studio version $with_toolchain_version is not valid.])
      AC_MSG_NOTICE([Valid Visual Studio versions: $VALID_VS_VERSIONS.])
      AC_MSG_ERROR([Cannot continue.])
    fi
    VS_VERSIONS_PROBE_LIST="$with_toolchain_version"
  else
    # No flag given, use default
    VS_VERSIONS_PROBE_LIST="$VALID_VS_VERSIONS"
  fi

  for VS_VERSION in $VS_VERSIONS_PROBE_LIST; do
    TOOLCHAIN_FIND_VISUAL_STUDIO_BAT_FILE([$VS_VERSION])
    if test "x$VS_ENV_CMD" != x; then
      TOOLCHAIN_VERSION=$VS_VERSION
      eval VS_DESCRIPTION="\${VS_DESCRIPTION_${VS_VERSION}}"
      eval VS_VERSION_INTERNAL="\${VS_VERSION_INTERNAL_${VS_VERSION}}"
      eval MSVCR_NAME="\${VS_MSVCR_${VS_VERSION}}"
      eval MSVCP_NAME="\${VS_MSVCP_${VS_VERSION}}"
      # The rest of the variables are already evaled while probing
      AC_MSG_NOTICE([Found $VS_DESCRIPTION])
      break
    fi
  done
])

################################################################################
# Check if the VS env variables were setup prior to running configure.
# If not, then find vcvarsall.bat and run it automatically, and integrate
# the set env variables into the spec file.
AC_DEFUN([TOOLCHAIN_SETUP_VISUAL_STUDIO_ENV],
[
  # Store path to cygwin link.exe to help excluding it when searching for
  # VS linker. This must be done before changing the PATH when looking for VS.
  AC_PATH_PROG(CYGWIN_LINK, link)
  if test "x$CYGWIN_LINK" != x; then
    AC_MSG_CHECKING([if the first found link.exe is actually the Cygwin link tool])
    "$CYGWIN_LINK" --version > /dev/null
    if test $? -eq 0 ; then
      AC_MSG_RESULT([yes])
    else
      AC_MSG_RESULT([no])
      # This might be the VS linker. Don't exclude it later on.
      CYGWIN_LINK=""
    fi
  fi

  # First-hand choice is to locate and run the vsvars bat file.
  TOOLCHAIN_FIND_VISUAL_STUDIO

  # If we have a devkit, skip all of the below.
  if test "x$DEVKIT_VS_VERSION" = x; then
    if test "x$VS_ENV_CMD" != x; then
      # We have found a Visual Studio environment on disk, let's extract variables from the vsvars bat file.
      BASIC_FIXUP_EXECUTABLE(VS_ENV_CMD)

      # Lets extract the variables that are set by vcvarsall.bat/vsvars32.bat/vsvars64.bat
      AC_MSG_NOTICE([Trying to extract Visual Studio environment variables])

      # We need to create a couple of temporary files.
      VS_ENV_TMP_DIR="$CONFIGURESUPPORT_OUTPUTDIR/vs-env"
      $MKDIR -p $VS_ENV_TMP_DIR

      # Cannot use the VS10 setup script directly (since it only updates the DOS subshell environment).
      # Instead create a shell script which will set the relevant variables when run.
      WINPATH_VS_ENV_CMD="$VS_ENV_CMD"
      BASIC_WINDOWS_REWRITE_AS_WINDOWS_MIXED_PATH([WINPATH_VS_ENV_CMD])
      WINPATH_BASH="$BASH"
      BASIC_WINDOWS_REWRITE_AS_WINDOWS_MIXED_PATH([WINPATH_BASH])

      # Generate a DOS batch file which runs $VS_ENV_CMD, and then creates a shell
      # script (executable by bash) that will setup the important variables.
      EXTRACT_VC_ENV_BAT_FILE="$VS_ENV_TMP_DIR/extract-vs-env.bat"
      $ECHO "@echo off" >  $EXTRACT_VC_ENV_BAT_FILE
      # This will end up something like:
      # call C:/progra~2/micros~2.0/vc/bin/amd64/vcvars64.bat
      $ECHO "call $WINPATH_VS_ENV_CMD $VS_ENV_ARGS" >> $EXTRACT_VC_ENV_BAT_FILE
      # These will end up something like:
      # C:/CygWin/bin/bash -c 'echo VS_PATH=\"$PATH\" > localdevenv.sh
      # The trailing space for everyone except PATH is no typo, but is needed due
      # to trailing \ in the Windows paths. These will be stripped later.
      $ECHO "$WINPATH_BASH -c 'echo VS_PATH="'\"$PATH\" > set-vs-env.sh' \
          >> $EXTRACT_VC_ENV_BAT_FILE
      $ECHO "$WINPATH_BASH -c 'echo VS_INCLUDE="'\"$INCLUDE\;$include \" >> set-vs-env.sh' \
          >> $EXTRACT_VC_ENV_BAT_FILE
      $ECHO "$WINPATH_BASH -c 'echo VS_LIB="'\"$LIB\;$lib \" >> set-vs-env.sh' \
          >> $EXTRACT_VC_ENV_BAT_FILE
      $ECHO "$WINPATH_BASH -c 'echo VCINSTALLDIR="'\"$VCINSTALLDIR \" >> set-vs-env.sh' \
          >> $EXTRACT_VC_ENV_BAT_FILE
      $ECHO "$WINPATH_BASH -c 'echo WindowsSdkDir="'\"$WindowsSdkDir \" >> set-vs-env.sh' \
          >> $EXTRACT_VC_ENV_BAT_FILE
      $ECHO "$WINPATH_BASH -c 'echo WINDOWSSDKDIR="'\"$WINDOWSSDKDIR \" >> set-vs-env.sh' \
          >> $EXTRACT_VC_ENV_BAT_FILE

      # Now execute the newly created bat file.
      # The | cat is to stop SetEnv.Cmd to mess with system colors on msys.
      # Change directory so we don't need to mess with Windows paths in redirects.
      cd $VS_ENV_TMP_DIR
      cmd /c extract-vs-env.bat | $CAT
      cd $CURDIR

      if test ! -s $VS_ENV_TMP_DIR/set-vs-env.sh; then
        AC_MSG_NOTICE([Could not succesfully extract the envionment variables needed for the VS setup.])
        AC_MSG_NOTICE([Try setting --with-tools-dir to the VC/bin directory within the VS installation])
        AC_MSG_NOTICE([or run "bash.exe -l" from a VS command prompt and then run configure from there.])
        AC_MSG_ERROR([Cannot continue])
      fi

      # Now set all paths and other env variables. This will allow the rest of
      # the configure script to find and run the compiler in the proper way.
      AC_MSG_NOTICE([Setting extracted environment variables])
      . $VS_ENV_TMP_DIR/set-vs-env.sh
      # Now we have VS_PATH, VS_INCLUDE, VS_LIB. For further checking, we
      # also define VCINSTALLDIR, WindowsSdkDir and WINDOWSSDKDIR.
    else
      # We did not find a vsvars bat file, let's hope we are run from a VS command prompt.
      AC_MSG_NOTICE([Cannot locate a valid Visual Studio installation, checking current environment])
    fi
  fi

  # At this point, we should have correct variables in the environment, or we can't continue.
  AC_MSG_CHECKING([for Visual Studio variables])

  if test "x$VCINSTALLDIR" != x || test "x$WindowsSDKDir" != x \
      || test "x$WINDOWSSDKDIR" != x || test "x$DEVKIT_NAME" != x; then
    if test "x$VS_INCLUDE" = x || test "x$VS_LIB" = x; then
      AC_MSG_RESULT([present but broken])
      AC_MSG_ERROR([Your VC command prompt seems broken, INCLUDE and/or LIB is missing.])
    else
      AC_MSG_RESULT([ok])
      # Remove any trailing "\" ";" and " " from the variables.
      VS_INCLUDE=`$ECHO "$VS_INCLUDE" | $SED -e 's/\\\\*;* *$//'`
      VS_LIB=`$ECHO "$VS_LIB" | $SED 's/\\\\*;* *$//'`
      VCINSTALLDIR=`$ECHO "$VCINSTALLDIR" | $SED 's/\\\\* *$//'`
      WindowsSDKDir=`$ECHO "$WindowsSDKDir" | $SED 's/\\\\* *$//'`
      WINDOWSSDKDIR=`$ECHO "$WINDOWSSDKDIR" | $SED 's/\\\\* *$//'`
      # Remove any paths containing # (typically F#) as that messes up make. This
      # is needed if visual studio was installed with F# support.
      VS_PATH=`$ECHO "$VS_PATH" | $SED 's/[[^:#]]*#[^:]*://g'`

      AC_SUBST(VS_PATH)
      AC_SUBST(VS_INCLUDE)
      AC_SUBST(VS_LIB)

      # Convert VS_INCLUDE into SYSROOT_CFLAGS
      OLDIFS="$IFS"
      IFS=";"
      for i in $VS_INCLUDE; do
        ipath=$i
        # Only process non-empty elements
        if test "x$ipath" != x; then
          IFS="$OLDIFS"
          # Check that directory exists before calling fixup_path
          testpath=$ipath
          BASIC_WINDOWS_REWRITE_AS_UNIX_PATH([testpath])
          if test -d "$testpath"; then
            BASIC_FIXUP_PATH([ipath])
            SYSROOT_CFLAGS="$SYSROOT_CFLAGS -I$ipath"
          fi
          IFS=";"
        fi
      done
      # Convert VS_LIB into SYSROOT_LDFLAGS
      for i in $VS_LIB; do
        libpath=$i
        # Only process non-empty elements
        if test "x$libpath" != x; then
          IFS="$OLDIFS"
          # Check that directory exists before calling fixup_path
          testpath=$libpath
          BASIC_WINDOWS_REWRITE_AS_UNIX_PATH([testpath])
          if test -d "$testpath"; then
            BASIC_FIXUP_PATH([libpath])
            SYSROOT_LDFLAGS="$SYSROOT_LDFLAGS -libpath:$libpath"
          fi
          IFS=";"
        fi
      done
      IFS="$OLDIFS"
    fi
  else
    AC_MSG_RESULT([not found])

    if test "x$VS_ENV_CMD" = x; then
      AC_MSG_NOTICE([Cannot locate a valid Visual Studio or Windows SDK installation on disk,])
      AC_MSG_NOTICE([nor is this script run from a Visual Studio command prompt.])
    else
      AC_MSG_NOTICE([Running the extraction script failed.])
    fi
    AC_MSG_NOTICE([Try setting --with-tools-dir to the VC/bin directory within the VS installation])
    AC_MSG_NOTICE([or run "bash.exe -l" from a VS command prompt and then run configure from there.])
    AC_MSG_ERROR([Cannot continue])
  fi
])

AC_DEFUN([TOOLCHAIN_CHECK_POSSIBLE_MSVC_DLL],
[
  DLL_NAME="$1"
  POSSIBLE_MSVC_DLL="$2"
  METHOD="$3"
  if test -n "$POSSIBLE_MSVC_DLL" -a -e "$POSSIBLE_MSVC_DLL"; then
    AC_MSG_NOTICE([Found $DLL_NAME at $POSSIBLE_MSVC_DLL using $METHOD])

    # Need to check if the found msvcr is correct architecture
    AC_MSG_CHECKING([found $DLL_NAME architecture])
    MSVC_DLL_FILETYPE=`$FILE -b "$POSSIBLE_MSVC_DLL"`
    if test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.msys"; then
      # The MSYS 'file' command returns "PE32 executable for MS Windows (DLL) (GUI) Intel 80386 32-bit"
      # on x32 and "PE32+ executable for MS Windows (DLL) (GUI) Mono/.Net assembly" on x64 systems.
      if test "x$OPENJDK_TARGET_CPU_BITS" = x32; then
        CORRECT_MSVCR_ARCH="PE32 executable"
      else
        CORRECT_MSVCR_ARCH="PE32+ executable"
      fi
    else
      if test "x$OPENJDK_TARGET_CPU_BITS" = x32; then
        CORRECT_MSVCR_ARCH=386
      else
        CORRECT_MSVCR_ARCH=x86-64
      fi
    fi
    if $ECHO "$MSVC_DLL_FILETYPE" | $GREP "$CORRECT_MSVCR_ARCH" 2>&1 > /dev/null; then
      AC_MSG_RESULT([ok])
      MSVC_DLL="$POSSIBLE_MSVC_DLL"
      BASIC_FIXUP_PATH(MSVC_DLL)
      AC_MSG_CHECKING([for $DLL_NAME])
      AC_MSG_RESULT([$MSVC_DLL])
    else
      AC_MSG_RESULT([incorrect, ignoring])
      AC_MSG_NOTICE([The file type of the located $DLL_NAME is $MSVC_DLL_FILETYPE])
    fi
  fi
])

AC_DEFUN([TOOLCHAIN_SETUP_MSVC_DLL],
[
  DLL_NAME="$1"
  MSVC_DLL=

  if test "x$MSVC_DLL" = x; then
    # Probe: Using well-known location from Visual Studio 10.0
    if test "x$VCINSTALLDIR" != x; then
      CYGWIN_VC_INSTALL_DIR="$VCINSTALLDIR"
      BASIC_WINDOWS_REWRITE_AS_UNIX_PATH(CYGWIN_VC_INSTALL_DIR)
      if test "x$OPENJDK_TARGET_CPU_BITS" = x64; then
        POSSIBLE_MSVC_DLL="$CYGWIN_VC_INSTALL_DIR/redist/x64/Microsoft.VC${VS_VERSION_INTERNAL}.CRT/$DLL_NAME"
      else
        POSSIBLE_MSVC_DLL="$CYGWIN_VC_INSTALL_DIR/redist/x86/Microsoft.VC${VS_VERSION_INTERNAL}.CRT/$DLL_NAME"
      fi
      $ECHO "POSSIBLE_MSVC_DLL $POSSIBLEMSVC_DLL"
      TOOLCHAIN_CHECK_POSSIBLE_MSVC_DLL([$DLL_NAME], [$POSSIBLE_MSVC_DLL],
          [well-known location in VCINSTALLDIR])
    fi
  fi

  if test "x$MSVC_DLL" = x; then
    # Probe: Check in the Boot JDK directory.
    POSSIBLE_MSVC_DLL="$BOOT_JDK/bin/$DLL_NAME"
    TOOLCHAIN_CHECK_POSSIBLE_MSVC_DLL([$DLL_NAME], [$POSSIBLE_MSVC_DLL],
        [well-known location in Boot JDK])
  fi

  if test "x$MSVC_DLL" = x; then
    # Probe: Look in the Windows system32 directory
    CYGWIN_SYSTEMROOT="$SYSTEMROOT"
    BASIC_WINDOWS_REWRITE_AS_UNIX_PATH(CYGWIN_SYSTEMROOT)
    POSSIBLE_MSVC_DLL="$CYGWIN_SYSTEMROOT/system32/$DLL_NAME"
    TOOLCHAIN_CHECK_POSSIBLE_MSVC_DLL([$DLL_NAME], [$POSSIBLE_MSVC_DLL],
        [well-known location in SYSTEMROOT])
  fi

  if test "x$MSVC_DLL" = x; then
    # Probe: If Visual Studio Express is installed, there is usually one with the debugger
    if test "x$VS100COMNTOOLS" != x; then
      CYGWIN_VS_TOOLS_DIR="$VS100COMNTOOLS/.."
      BASIC_WINDOWS_REWRITE_AS_UNIX_PATH(CYGWIN_VS_TOOLS_DIR)
      if test "x$OPENJDK_TARGET_CPU_BITS" = x64; then
        POSSIBLE_MSVC_DLL=`$FIND "$CYGWIN_VS_TOOLS_DIR" -name $DLL_NAME \
	    | $GREP -i /x64/ | $HEAD --lines 1`
      else
        POSSIBLE_MSVC_DLL=`$FIND "$CYGWIN_VS_TOOLS_DIR" -name $DLL_NAME \
	    | $GREP -i /x86/ | $HEAD --lines 1`
      fi
      TOOLCHAIN_CHECK_POSSIBLE_MSVC_DLL([$DLL_NAME], [$POSSIBLE_MSVC_DLL],
          [search of VS100COMNTOOLS])
    fi
  fi

  if test "x$MSVC_DLL" = x; then
    # Probe: Search wildly in the VCINSTALLDIR. We've probably lost by now.
    # (This was the original behaviour; kept since it might turn something up)
    if test "x$CYGWIN_VC_INSTALL_DIR" != x; then
      if test "x$OPENJDK_TARGET_CPU_BITS" = x64; then
        POSSIBLE_MSVC_DLL=`$FIND "$CYGWIN_VC_INSTALL_DIR" -name $DLL_NAME \
	    | $GREP x64 | $HEAD --lines 1`
      else
        POSSIBLE_MSVC_DLL=`$FIND "$CYGWIN_VC_INSTALL_DIR" -name $DLL_NAME \
	    | $GREP x86 | $GREP -v ia64 | $GREP -v x64 | $HEAD --lines 1`
        if test "x$POSSIBLE_MSVC_DLL" = x; then
          # We're grasping at straws now...
          POSSIBLE_MSVC_DLL=`$FIND "$CYGWIN_VC_INSTALL_DIR" -name $DLL_NAME \
	      | $HEAD --lines 1`
        fi
      fi

      TOOLCHAIN_CHECK_POSSIBLE_MSVC_DLL([$DLL_NAME], [$POSSIBLE_MSVC_DLL],
          [search of VCINSTALLDIR])
    fi
  fi

  if test "x$MSVC_DLL" = x; then
    AC_MSG_CHECKING([for $DLL_NAME])
    AC_MSG_RESULT([no])
    AC_MSG_ERROR([Could not find $DLL_NAME. Please specify using --with-msvcr-dll.])
  fi
])

AC_DEFUN([TOOLCHAIN_SETUP_VS_RUNTIME_DLLS],
[
  AC_ARG_WITH(msvcr-dll, [AS_HELP_STRING([--with-msvcr-dll],
      [path to microsoft C runtime dll (msvcr*.dll) (Windows only) @<:@probed@:>@])])

  if test "x$with_msvcr_dll" != x; then
    # If given explicitely by user, do not probe. If not present, fail directly.
    TOOLCHAIN_CHECK_POSSIBLE_MSVC_DLL($MSVCR_NAME, [$with_msvcr_dll], [--with-msvcr-dll])
    if test "x$MSVC_DLL" = x; then
      AC_MSG_ERROR([Could not find a proper $MSVCR_NAME as specified by --with-msvcr-dll])
    fi
    MSVCR_DLL="$MSVC_DLL"
  elif test "x$DEVKIT_MSVCR_DLL" != x; then
    TOOLCHAIN_CHECK_POSSIBLE_MSVC_DLL($MSVCR_NAME, [$DEVKIT_MSVCR_DLL], [devkit])
    if test "x$MSVC_DLL" = x; then
      AC_MSG_ERROR([Could not find a proper $MSVCR_NAME as specified by devkit])
    fi  
    MSVCR_DLL="$MSVC_DLL"
  else
    TOOLCHAIN_SETUP_MSVC_DLL([${MSVCR_NAME}])
    MSVCR_DLL="$MSVC_DLL"
  fi
  AC_SUBST(MSVCR_DLL)

  AC_ARG_WITH(msvcp-dll, [AS_HELP_STRING([--with-msvcp-dll],
      [path to microsoft C++ runtime dll (msvcp*.dll) (Windows only) @<:@probed@:>@])])

  if test "x$MSVCP_NAME" != "x"; then
    if test "x$with_msvcp_dll" != x; then
      # If given explicitely by user, do not probe. If not present, fail directly.
      TOOLCHAIN_CHECK_POSSIBLE_MSVC_DLL($MSVCP_NAME, [$with_msvcp_dll], [--with-msvcp-dll])
      if test "x$MSVC_DLL" = x; then
        AC_MSG_ERROR([Could not find a proper $MSVCP_NAME as specified by --with-msvcp-dll])
      fi
      MSVCP_DLL="$MSVC_DLL"
    elif test "x$DEVKIT_MSVCP_DLL" != x; then
      TOOLCHAIN_CHECK_POSSIBLE_MSVC_DLL($MSVCP_NAME, [$DEVKIT_MSVCP_DLL], [devkit])
      if test "x$MSVC_DLL" = x; then
        AC_MSG_ERROR([Could not find a proper $MSVCP_NAME as specified by devkit])
      fi  
      MSVCP_DLL="$MSVC_DLL"
    else
      TOOLCHAIN_SETUP_MSVC_DLL([${MSVCP_NAME}])
      MSVCP_DLL="$MSVC_DLL"
    fi
    AC_SUBST(MSVCP_DLL)
  fi
])
