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

AC_DEFUN([BASIC_WINDOWS_REWRITE_AS_UNIX_PATH],
[
  windows_path="[$]$1"
  if test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.cygwin"; then
    unix_path=`$CYGPATH -u "$windows_path"`
    $1="$unix_path"
  elif test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.msys"; then
    unix_path=`$ECHO "$windows_path" | $SED -e 's,^\\(.\\):,/\\1,g' -e 's,\\\\,/,g'`
    $1="$unix_path"
  fi
])

AC_DEFUN([BASIC_WINDOWS_REWRITE_AS_WINDOWS_MIXED_PATH],
[
  unix_path="[$]$1"
  if test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.cygwin"; then
    windows_path=`$CYGPATH -m "$unix_path"`
    $1="$windows_path"
  elif test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.msys"; then
    windows_path=`cmd //c echo $unix_path`
    $1="$windows_path"
  fi
])

# Helper function which possibly converts a path using DOS-style short mode.
# If so, the updated path is stored in $new_path.
# $1: The path to check
AC_DEFUN([BASIC_MAKE_WINDOWS_SPACE_SAFE_CYGWIN],
[
  input_path="$1"
  # Check if we need to convert this using DOS-style short mode. If the path
  # contains just simple characters, use it. Otherwise (spaces, weird characters),
  # take no chances and rewrite it.
  # Note: m4 eats our [], so we need to use @<:@ and @:>@ instead.
  has_forbidden_chars=`$ECHO "$input_path" | $GREP @<:@^-._/a-zA-Z0-9@:>@`
  if test "x$has_forbidden_chars" != x; then
    # Now convert it to mixed DOS-style, short mode (no spaces, and / instead of \)
    shortmode_path=`$CYGPATH -s -m -a "$input_path"`
    path_after_shortmode=`$CYGPATH -u "$shortmode_path"`
    if test "x$path_after_shortmode" != "x$input_to_shortpath"; then
      # Going to short mode and back again did indeed matter. Since short mode is
      # case insensitive, let's make it lowercase to improve readability.
      shortmode_path=`$ECHO "$shortmode_path" | $TR 'ABCDEFGHIJKLMNOPQRSTUVWXYZ' 'abcdefghijklmnopqrstuvwxyz'`
      # Now convert it back to Unix-style (cygpath)
      input_path=`$CYGPATH -u "$shortmode_path"`
      new_path="$input_path"
    fi
  fi

  test_cygdrive_prefix=`$ECHO $input_path | $GREP ^/cygdrive/`
  if test "x$test_cygdrive_prefix" = x; then
    # As a simple fix, exclude /usr/bin since it's not a real path.
    if test "x`$ECHO $1 | $GREP ^/usr/bin/`" = x; then
      # The path is in a Cygwin special directory (e.g. /home). We need this converted to
      # a path prefixed by /cygdrive for fixpath to work.
      new_path="$CYGWIN_ROOT_PATH$input_path"
    fi
  fi
])

# Helper function which possibly converts a path using DOS-style short mode.
# If so, the updated path is stored in $new_path.
# $1: The path to check
AC_DEFUN([BASIC_MAKE_WINDOWS_SPACE_SAFE_MSYS],
[
  input_path="$1"
  # Check if we need to convert this using DOS-style short mode. If the path
  # contains just simple characters, use it. Otherwise (spaces, weird characters),
  # take no chances and rewrite it.
  # Note: m4 eats our [], so we need to use @<:@ and @:>@ instead.
  has_forbidden_chars=`$ECHO "$input_path" | $GREP @<:@^-_/:a-zA-Z0-9@:>@`
  if test "x$has_forbidden_chars" != x; then
    # Now convert it to mixed DOS-style, short mode (no spaces, and / instead of \)
    new_path=`cmd /c "for %A in (\"$input_path\") do @echo %~sA"|$TR \\\\\\\\ / | $TR 'ABCDEFGHIJKLMNOPQRSTUVWXYZ' 'abcdefghijklmnopqrstuvwxyz'`
  fi
])

# FIXME: The BASIC_FIXUP_*_CYGWIN/MSYS is most likely too convoluted
# and could probably be heavily simplified. However, all changes in this
# area tend to need lot of testing in different scenarios, and in lack of
# proper unit testing, cleaning this up has not been deemed worth the effort
# at the moment.

AC_DEFUN([BASIC_FIXUP_PATH_CYGWIN],
[
  # Input might be given as Windows format, start by converting to
  # unix format.
  path="[$]$1"
  new_path=`$CYGPATH -u "$path"`

  # Cygwin tries to hide some aspects of the Windows file system, such that binaries are
  # named .exe but called without that suffix. Therefore, "foo" and "foo.exe" are considered
  # the same file, most of the time (as in "test -f"). But not when running cygpath -s, then
  # "foo.exe" is OK but "foo" is an error.
  #
  # This test is therefore slightly more accurate than "test -f" to check for file precense.
  # It is also a way to make sure we got the proper file name for the real test later on.
  test_shortpath=`$CYGPATH -s -m "$new_path" 2> /dev/null`
  if test "x$test_shortpath" = x; then
    AC_MSG_NOTICE([The path of $1, which resolves as "$path", is invalid.])
    AC_MSG_ERROR([Cannot locate the the path of $1])
  fi

  # Call helper function which possibly converts this using DOS-style short mode.
  # If so, the updated path is stored in $new_path.
  BASIC_MAKE_WINDOWS_SPACE_SAFE_CYGWIN([$new_path])

  if test "x$path" != "x$new_path"; then
    $1="$new_path"
    AC_MSG_NOTICE([Rewriting $1 to "$new_path"])
  fi
])

AC_DEFUN([BASIC_FIXUP_PATH_MSYS],
[
  path="[$]$1"
  has_colon=`$ECHO $path | $GREP ^.:`
  new_path="$path"
  if test "x$has_colon" = x; then
    # Not in mixed or Windows style, start by that.
    new_path=`cmd //c echo $path`
  fi

  BASIC_MAKE_WINDOWS_SPACE_SAFE_MSYS([$new_path])
  BASIC_WINDOWS_REWRITE_AS_UNIX_PATH(new_path)
  if test "x$path" != "x$new_path"; then
    $1="$new_path"
    AC_MSG_NOTICE([Rewriting $1 to "$new_path"])
  fi

  # Save the first 10 bytes of this path to the storage, so fixpath can work.
  all_fixpath_prefixes=("${all_fixpath_prefixes@<:@@@:>@}" "${new_path:0:10}")
])

AC_DEFUN([BASIC_FIXUP_EXECUTABLE_CYGWIN],
[
  # First separate the path from the arguments. This will split at the first
  # space.
  complete="[$]$1"
  path="${complete%% *}"
  tmp="$complete EOL"
  arguments="${tmp#* }"

  # Input might be given as Windows format, start by converting to
  # unix format.
  new_path=`$CYGPATH -u "$path"`

  # Now try to locate executable using which
  new_path=`$WHICH "$new_path" 2> /dev/null`
  # bat and cmd files are not always considered executable in cygwin causing which
  # to not find them
  if test "x$new_path" = x \
      && test "x`$ECHO \"$path\" | $GREP -i -e \"\\.bat$\" -e \"\\.cmd$\"`" != x \
      && test "x`$LS \"$path\" 2>/dev/null`" != x; then
    new_path=`$CYGPATH -u "$path"`
  fi
  if test "x$new_path" = x; then
    # Oops. Which didn't find the executable.
    # The splitting of arguments from the executable at a space might have been incorrect,
    # since paths with space are more likely in Windows. Give it another try with the whole
    # argument.
    path="$complete"
    arguments="EOL"
    new_path=`$CYGPATH -u "$path"`
    new_path=`$WHICH "$new_path" 2> /dev/null`
    # bat and cmd files are not always considered executable in cygwin causing which
    # to not find them
    if test "x$new_path" = x \
        && test "x`$ECHO \"$path\" | $GREP -i -e \"\\.bat$\" -e \"\\.cmd$\"`" != x \
        && test "x`$LS \"$path\" 2>/dev/null`" != x; then
      new_path=`$CYGPATH -u "$path"`
    fi
    if test "x$new_path" = x; then
      # It's still not found. Now this is an unrecoverable error.
      AC_MSG_NOTICE([The path of $1, which resolves as "$complete", is not found.])
      has_space=`$ECHO "$complete" | $GREP " "`
      if test "x$has_space" != x; then
        AC_MSG_NOTICE([You might be mixing spaces in the path and extra arguments, which is not allowed.])
      fi
      AC_MSG_ERROR([Cannot locate the the path of $1])
    fi
  fi

  # Cygwin tries to hide some aspects of the Windows file system, such that binaries are
  # named .exe but called without that suffix. Therefore, "foo" and "foo.exe" are considered
  # the same file, most of the time (as in "test -f"). But not when running cygpath -s, then
  # "foo.exe" is OK but "foo" is an error.
  #
  # This test is therefore slightly more accurate than "test -f" to check for file presence.
  # It is also a way to make sure we got the proper file name for the real test later on.
  test_shortpath=`$CYGPATH -s -m "$new_path" 2> /dev/null`
  if test "x$test_shortpath" = x; then
    # Short path failed, file does not exist as specified.
    # Try adding .exe or .cmd
    if test -f "${new_path}.exe"; then
      input_to_shortpath="${new_path}.exe"
    elif test -f "${new_path}.cmd"; then
      input_to_shortpath="${new_path}.cmd"
    else
      AC_MSG_NOTICE([The path of $1, which resolves as "$new_path", is invalid.])
      AC_MSG_NOTICE([Neither "$new_path" nor "$new_path.exe/cmd" can be found])
      AC_MSG_ERROR([Cannot locate the the path of $1])
    fi
  else
    input_to_shortpath="$new_path"
  fi

  # Call helper function which possibly converts this using DOS-style short mode.
  # If so, the updated path is stored in $new_path.
  new_path="$input_to_shortpath"
  BASIC_MAKE_WINDOWS_SPACE_SAFE_CYGWIN([$input_to_shortpath])
  # remove trailing .exe if any
  new_path="${new_path/%.exe/}"
])

AC_DEFUN([BASIC_FIXUP_EXECUTABLE_MSYS],
[
  # First separate the path from the arguments. This will split at the first
  # space.
  complete="[$]$1"
  path="${complete%% *}"
  tmp="$complete EOL"
  arguments="${tmp#* }"

  # Input might be given as Windows format, start by converting to
  # unix format.
  new_path="$path"
  BASIC_WINDOWS_REWRITE_AS_UNIX_PATH(new_path)

  # Now try to locate executable using which
  new_path=`$WHICH "$new_path" 2> /dev/null`

  if test "x$new_path" = x; then
    # Oops. Which didn't find the executable.
    # The splitting of arguments from the executable at a space might have been incorrect,
    # since paths with space are more likely in Windows. Give it another try with the whole
    # argument.
    path="$complete"
    arguments="EOL"
    new_path="$path"
    BASIC_WINDOWS_REWRITE_AS_UNIX_PATH(new_path)

    new_path=`$WHICH "$new_path" 2> /dev/null`
    # bat and cmd files are not always considered executable in MSYS causing which
    # to not find them
    if test "x$new_path" = x \
        && test "x`$ECHO \"$path\" | $GREP -i -e \"\\.bat$\" -e \"\\.cmd$\"`" != x \
        && test "x`$LS \"$path\" 2>/dev/null`" != x; then
      new_path="$path"
      BASIC_WINDOWS_REWRITE_AS_UNIX_PATH(new_path)
    fi

    if test "x$new_path" = x; then
      # It's still not found. Now this is an unrecoverable error.
      AC_MSG_NOTICE([The path of $1, which resolves as "$complete", is not found.])
      has_space=`$ECHO "$complete" | $GREP " "`
      if test "x$has_space" != x; then
        AC_MSG_NOTICE([You might be mixing spaces in the path and extra arguments, which is not allowed.])
      fi
      AC_MSG_ERROR([Cannot locate the the path of $1])
    fi
  fi

  # Now new_path has a complete unix path to the binary
  if test "x`$ECHO $new_path | $GREP ^/bin/`" != x; then
    # Keep paths in /bin as-is, but remove trailing .exe if any
    new_path="${new_path/%.exe/}"
    # Do not save /bin paths to all_fixpath_prefixes!
  else
    # Not in mixed or Windows style, start by that.
    new_path=`cmd //c echo $new_path`
    BASIC_MAKE_WINDOWS_SPACE_SAFE_MSYS([$new_path])
    # Output is in $new_path
    BASIC_WINDOWS_REWRITE_AS_UNIX_PATH(new_path)
    # remove trailing .exe if any
    new_path="${new_path/%.exe/}"

    # Save the first 10 bytes of this path to the storage, so fixpath can work.
    all_fixpath_prefixes=("${all_fixpath_prefixes@<:@@@:>@}" "${new_path:0:10}")
  fi
])

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
    # The cmd output ends with Windows line endings (CR/LF), the grep command will strip that away
    cygwin_winpath_root=`cd / ; cmd /c cd | grep ".*"`
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
    MSYS_ROOT_PATH=`cd / ; cmd /c cd | grep ".*"`
    BASIC_WINDOWS_REWRITE_AS_UNIX_PATH(MSYS_ROOT_PATH)
    AC_MSG_RESULT([$MSYS_ROOT_PATH])
    WINDOWS_ENV_ROOT_PATH="$MSYS_ROOT_PATH"
  else
    AC_MSG_ERROR([Unknown Windows environment. Neither cygwin nor msys was detected.])
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
    FIXPATH_SRC="$SRC_ROOT/common/src/fixpath.c"
    FIXPATH_BIN="$CONFIGURESUPPORT_OUTPUTDIR/bin/fixpath.exe"
    FIXPATH_DIR="$CONFIGURESUPPORT_OUTPUTDIR/fixpath"
    if test "x$OPENJDK_BUILD_OS_ENV" = xwindows.cygwin; then
      # Important to keep the .exe suffix on Cygwin for Hotspot makefiles
      FIXPATH="$FIXPATH_BIN -c"
    elif test "x$OPENJDK_BUILD_OS_ENV" = xwindows.msys; then
      # Take all collected prefixes and turn them into a -m/c/foo@/c/bar@... command line
      # @ was chosen as separator to minimize risk of other tools messing around with it
      all_unique_prefixes=`echo "${all_fixpath_prefixes@<:@@@:>@}" \
          | tr ' ' '\n' | grep '^/./' | sort | uniq`
      fixpath_argument_list=`echo $all_unique_prefixes  | tr ' ' '@'`
      FIXPATH="$FIXPATH_BIN -m$fixpath_argument_list"
    fi
    FIXPATH_SRC_W="$FIXPATH_SRC"
    FIXPATH_BIN_W="$FIXPATH_BIN"
    BASIC_WINDOWS_REWRITE_AS_WINDOWS_MIXED_PATH([FIXPATH_SRC_W])
    BASIC_WINDOWS_REWRITE_AS_WINDOWS_MIXED_PATH([FIXPATH_BIN_W])
    $RM -rf $FIXPATH_BIN $FIXPATH_DIR
    $MKDIR -p $FIXPATH_DIR $CONFIGURESUPPORT_OUTPUTDIR/bin
    cd $FIXPATH_DIR
    $CC $FIXPATH_SRC_W -Fe$FIXPATH_BIN_W > $FIXPATH_DIR/fixpath1.log 2>&1
    cd $CURDIR

    if test ! -x $FIXPATH_BIN; then
      AC_MSG_RESULT([no])
      cat $FIXPATH_DIR/fixpath1.log
      AC_MSG_ERROR([Could not create $FIXPATH_BIN])
    fi
    AC_MSG_RESULT([yes])
    AC_MSG_CHECKING([if fixpath.exe works])
    cd $FIXPATH_DIR
    $FIXPATH $CC $FIXPATH_SRC -Fe$FIXPATH_DIR/fixpath2.exe \
        > $FIXPATH_DIR/fixpath2.log 2>&1
    cd $CURDIR
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
