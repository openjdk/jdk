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

AC_DEFUN([TOOLCHAIN_CHECK_POSSIBLE_VISUAL_STUDIO_ROOT],
[
  if test "x$VS_ENV_CMD" = x; then
    VS100BASE="$1"
    METHOD="$2"
    BASIC_WINDOWS_REWRITE_AS_UNIX_PATH(VS100BASE)
    if test -d "$VS100BASE"; then
      if test -f "$VS100BASE/$VCVARSFILE"; then
        AC_MSG_NOTICE([Found Visual Studio installation at $VS100BASE using $METHOD])
        VS_ENV_CMD="$VS100BASE/$VCVARSFILE"
        # PLATFORM_TOOLSET is used during the compilation of the freetype sources (see
        # 'LIB_BUILD_FREETYPE' in libraries.m4) and must be one of 'v100', 'v110' or 'v120' for VS 2010, 2012 or VS2013
        # TODO: improve detection for other versions of VS
        PLATFORM_TOOLSET="v100"
      else
        AC_MSG_NOTICE([Found Visual Studio installation at $VS100BASE using $METHOD])
        AC_MSG_NOTICE([Warning: $VCVARSFILE is missing, this is probably Visual Studio Express. Ignoring])
      fi
    fi
  fi
])

AC_DEFUN([TOOLCHAIN_CHECK_POSSIBLE_WIN_SDK_ROOT],
[
  if test "x$VS_ENV_CMD" = x; then
    WIN_SDK_BASE="$1"
    METHOD="$2"
    BASIC_WINDOWS_REWRITE_AS_UNIX_PATH(WIN_SDK_BASE)
    if test -d "$WIN_SDK_BASE"; then
      # There have been cases of partial or broken SDK installations. A missing
      # lib dir is not going to work.
      if test ! -d "$WIN_SDK_BASE/../lib"; then
        AC_MSG_NOTICE([Found Windows SDK installation at $WIN_SDK_BASE using $METHOD])
        AC_MSG_NOTICE([Warning: Installation is broken, lib dir is missing. Ignoring])
      elif test -f "$WIN_SDK_BASE/SetEnv.Cmd"; then
        AC_MSG_NOTICE([Found Windows SDK installation at $WIN_SDK_BASE using $METHOD])
        VS_ENV_CMD="$WIN_SDK_BASE/SetEnv.Cmd"
        if test "x$OPENJDK_TARGET_CPU_BITS" = x32; then
          VS_ENV_ARGS="/x86"
        else
          VS_ENV_ARGS="/x64"
        fi
        # PLATFORM_TOOLSET is used during the compilation of the freetype sources (see
        # 'LIB_BUILD_FREETYPE' in libraries.m4) and must be 'Windows7.1SDK' for Windows7.1SDK
        # TODO: improve detection for other versions of SDK
        PLATFORM_TOOLSET="Windows7.1SDK"
      else
        AC_MSG_NOTICE([Found Windows SDK installation at $WIN_SDK_BASE using $METHOD])
        AC_MSG_NOTICE([Warning: Installation is broken, SetEnv.Cmd is missing. Ignoring])
      fi
    fi
  fi
])

AC_DEFUN([TOOLCHAIN_FIND_VISUAL_STUDIO_BAT_FILE],
[
  if test "x$OPENJDK_TARGET_CPU_BITS" = x32; then
    VCVARSFILE="vc/bin/vcvars32.bat"
  else
    VCVARSFILE="vc/bin/amd64/vcvars64.bat"
  fi

  VS_ENV_CMD=""
  VS_ENV_ARGS=""
  if test "x$with_toolsdir" != x; then
    TOOLCHAIN_CHECK_POSSIBLE_VISUAL_STUDIO_ROOT([$with_toolsdir/../..], [--with-tools-dir])
  fi

  if test "x$with_toolsdir" != x && test "x$VS_ENV_CMD" = x; then
    # Having specified an argument which is incorrect will produce an instant failure;
    # we should not go on looking
    AC_MSG_NOTICE([The path given by --with-tools-dir does not contain a valid Visual Studio installation])
    AC_MSG_NOTICE([Please point to the VC/bin directory within the Visual Studio installation])
    AC_MSG_ERROR([Cannot locate a valid Visual Studio installation])
  fi

  if test "x$VS100COMNTOOLS" != x; then
    TOOLCHAIN_CHECK_POSSIBLE_VISUAL_STUDIO_ROOT([$VS100COMNTOOLS/../..], [VS100COMNTOOLS variable])
  fi
  if test "x$PROGRAMFILES" != x; then
    TOOLCHAIN_CHECK_POSSIBLE_VISUAL_STUDIO_ROOT([$PROGRAMFILES/Microsoft Visual Studio 10.0], [well-known name])
  fi
  TOOLCHAIN_CHECK_POSSIBLE_VISUAL_STUDIO_ROOT([C:/Program Files/Microsoft Visual Studio 10.0], [well-known name])
  TOOLCHAIN_CHECK_POSSIBLE_VISUAL_STUDIO_ROOT([C:/Program Files (x86)/Microsoft Visual Studio 10.0], [well-known name])

  if test "x$ProgramW6432" != x; then
    TOOLCHAIN_CHECK_POSSIBLE_WIN_SDK_ROOT([$ProgramW6432/Microsoft SDKs/Windows/v7.1/Bin], [well-known name])
  fi
  if test "x$PROGRAMW6432" != x; then
    TOOLCHAIN_CHECK_POSSIBLE_WIN_SDK_ROOT([$PROGRAMW6432/Microsoft SDKs/Windows/v7.1/Bin], [well-known name])
  fi
  if test "x$PROGRAMFILES" != x; then
    TOOLCHAIN_CHECK_POSSIBLE_WIN_SDK_ROOT([$PROGRAMFILES/Microsoft SDKs/Windows/v7.1/Bin], [well-known name])
  fi
  TOOLCHAIN_CHECK_POSSIBLE_WIN_SDK_ROOT([C:/Program Files/Microsoft SDKs/Windows/v7.1/Bin], [well-known name])
  TOOLCHAIN_CHECK_POSSIBLE_WIN_SDK_ROOT([C:/Program Files (x86)/Microsoft SDKs/Windows/v7.1/Bin], [well-known name])
])

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
  TOOLCHAIN_FIND_VISUAL_STUDIO_BAT_FILE
  if test "x$VS_ENV_CMD" != x; then
    # We have found a Visual Studio environment on disk, let's extract variables from the vsvars bat file.
    BASIC_FIXUP_EXECUTABLE(VS_ENV_CMD)

    # Lets extract the variables that are set by vcvarsall.bat/vsvars32.bat/vsvars64.bat
    AC_MSG_NOTICE([Trying to extract Visual Studio environment variables])

    # We need to create a couple of temporary files.
    VS_ENV_TMP_DIR="$OUTPUT_ROOT/vs-env"
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
    $ECHO "$WINPATH_BASH -c 'echo VS_PATH="'\"$PATH\" > set-vs-env.sh' >> $EXTRACT_VC_ENV_BAT_FILE
    $ECHO "$WINPATH_BASH -c 'echo VS_INCLUDE="'\"$INCLUDE \" >> set-vs-env.sh' >> $EXTRACT_VC_ENV_BAT_FILE
    $ECHO "$WINPATH_BASH -c 'echo VS_LIB="'\"$LIB \" >> set-vs-env.sh' >> $EXTRACT_VC_ENV_BAT_FILE
    $ECHO "$WINPATH_BASH -c 'echo VCINSTALLDIR="'\"$VCINSTALLDIR \" >> set-vs-env.sh' >> $EXTRACT_VC_ENV_BAT_FILE
    $ECHO "$WINPATH_BASH -c 'echo WindowsSdkDir="'\"$WindowsSdkDir \" >> set-vs-env.sh' >> $EXTRACT_VC_ENV_BAT_FILE
    $ECHO "$WINPATH_BASH -c 'echo WINDOWSSDKDIR="'\"$WINDOWSSDKDIR \" >> set-vs-env.sh' >> $EXTRACT_VC_ENV_BAT_FILE

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

  # At this point, we should have correct variables in the environment, or we can't continue.
  AC_MSG_CHECKING([for Visual Studio variables])

  if test "x$VCINSTALLDIR" != x || test "x$WindowsSDKDir" != x || test "x$WINDOWSSDKDIR" != x; then
    if test "x$VS_INCLUDE" = x || test "x$VS_LIB" = x; then
      AC_MSG_RESULT([present but broken])
      AC_MSG_ERROR([Your VC command prompt seems broken, INCLUDE and/or LIB is missing.])
    else
      AC_MSG_RESULT([ok])
      # Remove any trailing "\" and " " from the variables.
      VS_INCLUDE=`$ECHO "$VS_INCLUDE" | $SED 's/\\\\* *$//'`
      VS_LIB=`$ECHO "$VS_LIB" | $SED 's/\\\\* *$//'`
      VCINSTALLDIR=`$ECHO "$VCINSTALLDIR" | $SED 's/\\\\* *$//'`
      WindowsSDKDir=`$ECHO "$WindowsSDKDir" | $SED 's/\\\\* *$//'`
      WINDOWSSDKDIR=`$ECHO "$WINDOWSSDKDIR" | $SED 's/\\\\* *$//'`
      # Remove any paths containing # (typically F#) as that messes up make. This
      # is needed if visual studio was installed with F# support.
      VS_PATH=`$ECHO "$VS_PATH" | $SED 's/[[^:#]]*#[^:]*://g'`

      AC_SUBST(VS_PATH)
      AC_SUBST(VS_INCLUDE)
      AC_SUBST(VS_LIB)
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

AC_DEFUN([TOOLCHAIN_CHECK_POSSIBLE_MSVCR_DLL],
[
  POSSIBLE_MSVCR_DLL="$1"
  METHOD="$2"
  if test -e "$POSSIBLE_MSVCR_DLL"; then
    AC_MSG_NOTICE([Found msvcr100.dll at $POSSIBLE_MSVCR_DLL using $METHOD])
    
    # Need to check if the found msvcr is correct architecture
    AC_MSG_CHECKING([found msvcr100.dll architecture])
    MSVCR_DLL_FILETYPE=`$FILE -b "$POSSIBLE_MSVCR_DLL"`
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
    if $ECHO "$MSVCR_DLL_FILETYPE" | $GREP "$CORRECT_MSVCR_ARCH" 2>&1 > /dev/null; then
      AC_MSG_RESULT([ok])
      MSVCR_DLL="$POSSIBLE_MSVCR_DLL"
      AC_MSG_CHECKING([for msvcr100.dll])
      AC_MSG_RESULT([$MSVCR_DLL])
    else
      AC_MSG_RESULT([incorrect, ignoring])
      AC_MSG_NOTICE([The file type of the located msvcr100.dll is $MSVCR_DLL_FILETYPE])
    fi
  fi
])

AC_DEFUN([TOOLCHAIN_SETUP_MSVCR_DLL],
[
  AC_ARG_WITH(msvcr-dll, [AS_HELP_STRING([--with-msvcr-dll],
      [copy this msvcr100.dll into the built JDK (Windows only) @<:@probed@:>@])])

  if test "x$with_msvcr_dll" != x; then
    # If given explicitely by user, do not probe. If not present, fail directly.
    TOOLCHAIN_CHECK_POSSIBLE_MSVCR_DLL([$with_msvcr_dll], [--with-msvcr-dll])
    if test "x$MSVCR_DLL" = x; then
      AC_MSG_ERROR([Could not find a proper msvcr100.dll as specified by --with-msvcr-dll])
    fi
  fi
  
  if test "x$MSVCR_DLL" = x; then
    # Probe: Using well-known location from Visual Studio 10.0
    if test "x$VCINSTALLDIR" != x; then
      CYGWIN_VC_INSTALL_DIR="$VCINSTALLDIR"
      BASIC_WINDOWS_REWRITE_AS_UNIX_PATH(CYGWIN_VC_INSTALL_DIR)
      if test "x$OPENJDK_TARGET_CPU_BITS" = x64; then
        POSSIBLE_MSVCR_DLL="$CYGWIN_VC_INSTALL_DIR/redist/x64/Microsoft.VC100.CRT/msvcr100.dll"
      else
        POSSIBLE_MSVCR_DLL="$CYGWIN_VC_INSTALL_DIR/redist/x86/Microsoft.VC100.CRT/msvcr100.dll"
      fi
      TOOLCHAIN_CHECK_POSSIBLE_MSVCR_DLL([$POSSIBLE_MSVCR_DLL], [well-known location in VCINSTALLDIR])
    fi
  fi

  if test "x$MSVCR_DLL" = x; then
    # Probe: Check in the Boot JDK directory.
    POSSIBLE_MSVCR_DLL="$BOOT_JDK/bin/msvcr100.dll"
    TOOLCHAIN_CHECK_POSSIBLE_MSVCR_DLL([$POSSIBLE_MSVCR_DLL], [well-known location in Boot JDK])
  fi
  
  if test "x$MSVCR_DLL" = x; then
    # Probe: Look in the Windows system32 directory 
    CYGWIN_SYSTEMROOT="$SYSTEMROOT"
    BASIC_WINDOWS_REWRITE_AS_UNIX_PATH(CYGWIN_SYSTEMROOT)
    POSSIBLE_MSVCR_DLL="$CYGWIN_SYSTEMROOT/system32/msvcr100.dll"
    TOOLCHAIN_CHECK_POSSIBLE_MSVCR_DLL([$POSSIBLE_MSVCR_DLL], [well-known location in SYSTEMROOT])
  fi

  if test "x$MSVCR_DLL" = x; then
    # Probe: If Visual Studio Express is installed, there is usually one with the debugger
    if test "x$VS100COMNTOOLS" != x; then
      CYGWIN_VS_TOOLS_DIR="$VS100COMNTOOLS/.."
      BASIC_WINDOWS_REWRITE_AS_UNIX_PATH(CYGWIN_VS_TOOLS_DIR)
      if test "x$OPENJDK_TARGET_CPU_BITS" = x64; then
        POSSIBLE_MSVCR_DLL=`$FIND "$CYGWIN_VS_TOOLS_DIR" -name msvcr100.dll | $GREP -i /x64/ | $HEAD --lines 1`
      else
        POSSIBLE_MSVCR_DLL=`$FIND "$CYGWIN_VS_TOOLS_DIR" -name msvcr100.dll | $GREP -i /x86/ | $HEAD --lines 1`
      fi
      TOOLCHAIN_CHECK_POSSIBLE_MSVCR_DLL([$POSSIBLE_MSVCR_DLL], [search of VS100COMNTOOLS])
    fi
  fi
      
  if test "x$MSVCR_DLL" = x; then
    # Probe: Search wildly in the VCINSTALLDIR. We've probably lost by now.
    # (This was the original behaviour; kept since it might turn up something)
    if test "x$CYGWIN_VC_INSTALL_DIR" != x; then
      if test "x$OPENJDK_TARGET_CPU_BITS" = x64; then
        POSSIBLE_MSVCR_DLL=`$FIND "$CYGWIN_VC_INSTALL_DIR" -name msvcr100.dll | $GREP x64 | $HEAD --lines 1`
      else
        POSSIBLE_MSVCR_DLL=`$FIND "$CYGWIN_VC_INSTALL_DIR" -name msvcr100.dll | $GREP x86 | $GREP -v ia64 | $GREP -v x64 | $HEAD --lines 1`
        if test "x$POSSIBLE_MSVCR_DLL" = x; then
          # We're grasping at straws now...
          POSSIBLE_MSVCR_DLL=`$FIND "$CYGWIN_VC_INSTALL_DIR" -name msvcr100.dll | $HEAD --lines 1`
        fi
      fi
      
      TOOLCHAIN_CHECK_POSSIBLE_MSVCR_DLL([$POSSIBLE_MSVCR_DLL], [search of VCINSTALLDIR])
    fi
  fi
  
  if test "x$MSVCR_DLL" = x; then
    AC_MSG_CHECKING([for msvcr100.dll])
    AC_MSG_RESULT([no])
    AC_MSG_ERROR([Could not find msvcr100.dll. Please specify using --with-msvcr-dll.])
  fi

  BASIC_FIXUP_PATH(MSVCR_DLL)
])
