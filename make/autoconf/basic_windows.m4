#
# Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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

# Setup basic configuration paths, and platform-specific stuff related to PATHs.
AC_DEFUN([BASIC_CHECK_PATHS_WINDOWS],
[
  SRC_ROOT_LENGTH=`$THEPWDCMD -L|$WC -m`
  if test $SRC_ROOT_LENGTH -gt 100; then
    AC_MSG_ERROR([Your base path is too long. It is $SRC_ROOT_LENGTH characters long, but only 100 is supported])
  fi

  if test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.cygwin"; then
    AC_MSG_CHECKING([cygwin release])
    CYGWIN_VERSION=`$UNAME -r`
    AC_MSG_RESULT([$CYGWIN_VERSION])
    WINDOWS_ENV_VENDOR='cygwin'
    WINDOWS_ENV_VERSION="$CYGWIN_VERSION"

    CYGWIN_VERSION_OLD=`$ECHO $CYGWIN_VERSION | $GREP -e '^1\.[0-6]'`
    if test "x$CYGWIN_VERSION_OLD" != x; then
      AC_MSG_NOTICE([Your cygwin is too old. You are running $CYGWIN_VERSION, but at least cygwin 1.7 is required. Please upgrade.])
      AC_MSG_ERROR([Cannot continue])
    fi
    if test "x$CYGPATH" = x; then
      AC_MSG_ERROR([Something is wrong with your cygwin installation since I cannot find cygpath.exe in your path])
    fi
    AC_MSG_CHECKING([cygwin root directory as unix-style path])
    # The cmd output ends with Windows line endings (CR/LF)
    cygwin_winpath_root=`cd / ; cmd /c cd | $TR -d '\r\n'`
    # Force cygpath to report the proper root by including a trailing space, and then stripping it off again.
    CYGWIN_ROOT_PATH=`$CYGPATH -u "$cygwin_winpath_root " | $CUT -f 1 -d " "`
    AC_MSG_RESULT([$CYGWIN_ROOT_PATH])
    WINDOWS_ENV_ROOT_PATH="$CYGWIN_ROOT_PATH"
    test_cygdrive_prefix=`$ECHO $CYGWIN_ROOT_PATH | $GREP ^/cygdrive/`
    if test "x$test_cygdrive_prefix" = x; then
      AC_MSG_ERROR([Your cygdrive prefix is not /cygdrive. This is currently not supported. Change with mount -c.])
    fi
  elif test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.msys"; then
    AC_MSG_CHECKING([msys release])
    MSYS_VERSION=`$UNAME -r`
    AC_MSG_RESULT([$MSYS_VERSION])

    WINDOWS_ENV_VENDOR='msys'
    WINDOWS_ENV_VERSION="$MSYS_VERSION"

    AC_MSG_CHECKING([msys root directory as unix-style path])
    # The cmd output ends with Windows line endings (CR/LF), the grep command will strip that away
    MSYS_ROOT_PATH=`cd / ; cmd /c cd | $GREP ".*"`
    UTIL_REWRITE_AS_UNIX_PATH(MSYS_ROOT_PATH)
    AC_MSG_RESULT([$MSYS_ROOT_PATH])
    WINDOWS_ENV_ROOT_PATH="$MSYS_ROOT_PATH"
  elif test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.wsl"; then
    AC_MSG_CHECKING([Windows version])
    # m4 replaces [ and ] so we use @<:@ and @:>@ instead
    WINDOWS_VERSION=`$CMD /c ver.exe | $EGREP -o '(@<:@0-9@:>@+\.)+@<:@0-9@:>@+'`
    AC_MSG_RESULT([$WINDOWS_VERSION])

    AC_MSG_CHECKING([WSL kernel version])
    WSL_KERNEL_VERSION=`$UNAME -v`
    AC_MSG_RESULT([$WSL_KERNEL_VERSION])

    AC_MSG_CHECKING([WSL kernel release])
    WSL_KERNEL_RELEASE=`$UNAME -r`
    AC_MSG_RESULT([$WSL_KERNEL_RELEASE])

    AC_MSG_CHECKING([WSL distribution])
    WSL_DISTRIBUTION=`$LSB_RELEASE -d | sed 's/Description:\t//'`
    AC_MSG_RESULT([$WSL_DISTRIBUTION])

    WINDOWS_ENV_VENDOR='WSL'
    WINDOWS_ENV_VERSION="$WSL_DISTRIBUTION $WSL_KERNEL_VERSION $WSL_KERNEL_RELEASE (on Windows build $WINDOWS_VERSION)"
  else
    AC_MSG_ERROR([Unknown Windows environment. Neither cygwin, msys, nor wsl was detected.])
  fi

  # Test if windows or unix (cygwin/msys) find is first in path.
  AC_MSG_CHECKING([what kind of 'find' is first on the PATH])
  FIND_BINARY_OUTPUT=`find --version 2>&1`
  if test "x`echo $FIND_BINARY_OUTPUT | $GREP GNU`" != x; then
    AC_MSG_RESULT([unix style])
  elif test "x`echo $FIND_BINARY_OUTPUT | $GREP FIND`" != x; then
    AC_MSG_RESULT([Windows])
    AC_MSG_NOTICE([Your path contains Windows tools (C:\Windows\system32) before your unix (cygwin or msys) tools.])
    AC_MSG_NOTICE([This will not work. Please correct and make sure /usr/bin (or similar) is first in path.])
    AC_MSG_ERROR([Cannot continue])
  else
    AC_MSG_RESULT([unknown])
    AC_MSG_WARN([It seems that your find utility is non-standard.])
  fi
])

AC_DEFUN_ONCE([BASIC_COMPILE_FIXPATH],
[
  # When using cygwin or msys, we need a wrapper binary that renames
  # /cygdrive/c/ arguments into c:/ arguments and peeks into
  # @files and rewrites these too! This wrapper binary is
  # called fixpath.
  FIXPATH=
  if test "x$OPENJDK_BUILD_OS" = xwindows; then
    AC_MSG_CHECKING([if fixpath can be created])
    FIXPATH_SRC="$TOPDIR/make/src/native/fixpath.c"
    FIXPATH_BIN="$CONFIGURESUPPORT_OUTPUTDIR/bin/fixpath.exe"
    FIXPATH_DIR="$CONFIGURESUPPORT_OUTPUTDIR/fixpath"
    if test "x$OPENJDK_BUILD_OS_ENV" = xwindows.cygwin; then
      # Important to keep the .exe suffix on Cygwin for Hotspot makefiles
      FIXPATH="$FIXPATH_BIN -c"
    elif test "x$OPENJDK_BUILD_OS_ENV" = xwindows.msys; then
      # Take all collected prefixes and turn them into a -m/c/foo@/c/bar@... command line
      # @ was chosen as separator to minimize risk of other tools messing around with it
      all_unique_prefixes=`echo "${all_fixpath_prefixes@<:@@@:>@}" \
          | tr ' ' '\n' | $GREP '^/./' | $SORT | $UNIQ`
      fixpath_argument_list=`echo $all_unique_prefixes  | tr ' ' '@'`
      FIXPATH="$FIXPATH_BIN -m$fixpath_argument_list"
    elif test "x$OPENJDK_BUILD_OS_ENV" = xwindows.wsl; then
      FIXPATH="$FIXPATH_BIN -w"
    fi
    FIXPATH_SRC_W="$FIXPATH_SRC"
    FIXPATH_BIN_W="$FIXPATH_BIN"
    UTIL_REWRITE_AS_WINDOWS_MIXED_PATH([FIXPATH_SRC_W])
    UTIL_REWRITE_AS_WINDOWS_MIXED_PATH([FIXPATH_BIN_W])
    $RM -rf $FIXPATH_BIN $FIXPATH_DIR
    $MKDIR -p $FIXPATH_DIR $CONFIGURESUPPORT_OUTPUTDIR/bin
    cd $FIXPATH_DIR
    $CC $FIXPATH_SRC_W -Fe$FIXPATH_BIN_W > $FIXPATH_DIR/fixpath1.log 2>&1
    cd $CONFIGURE_START_DIR

    if test ! -x $FIXPATH_BIN; then
      AC_MSG_RESULT([no])
      cat $FIXPATH_DIR/fixpath1.log
      AC_MSG_ERROR([Could not create $FIXPATH_BIN])
    fi
    AC_MSG_RESULT([yes])

    if test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.wsl"; then
      OLD_WSLENV="$WSLENV"
      WSLENV=`$ECHO $WSLENV | $SED 's/PATH\/l://'`
      UTIL_APPEND_TO_PATH(WSLENV, "FIXPATH_PATH")
      export WSLENV
      export FIXPATH_PATH=$VS_PATH_WINDOWS
      AC_MSG_NOTICE([FIXPATH_PATH is $FIXPATH_PATH])
      AC_MSG_NOTICE([Rewriting WSLENV from $OLD_WSLENV to $WSLENV])
    fi

    AC_MSG_CHECKING([if fixpath.exe works])
    cd $FIXPATH_DIR
    $FIXPATH $CC $FIXPATH_SRC -Fe$FIXPATH_DIR/fixpath2.exe \
        > $FIXPATH_DIR/fixpath2.log 2>&1
    cd $CONFIGURE_START_DIR
    if test ! -x $FIXPATH_DIR/fixpath2.exe; then
      AC_MSG_RESULT([no])
      cat $FIXPATH_DIR/fixpath2.log
      AC_MSG_ERROR([fixpath did not work!])
    fi
    AC_MSG_RESULT([yes])

    FIXPATH_DETACH_FLAG="--detach"
  fi

  AC_SUBST(FIXPATH)
  AC_SUBST(FIXPATH_DETACH_FLAG)
])
