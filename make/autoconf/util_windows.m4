#
# Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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

AC_DEFUN([UTIL_REWRITE_AS_UNIX_PATH],
[
  windows_path="[$]$1"
  if test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.cygwin"; then
    unix_path=`$CYGPATH -u "$windows_path"`
    $1="$unix_path"
  elif test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.msys"; then
    unix_path=`$ECHO "$windows_path" | $SED -e 's,^\\(.\\):,/\\1,g' -e 's,\\\\,/,g'`
    $1="$unix_path"
  elif test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.wsl"; then
    # wslpath does not check the input, only call if an actual windows path was
    # given.
    if $ECHO "$windows_path" | $GREP -q ["^[a-zA-Z]:[\\\\/]"]; then
      unix_path=`$WSLPATH -u "$windows_path"`
      $1="$unix_path"
    fi
  fi
])

AC_DEFUN([UTIL_REWRITE_AS_WINDOWS_MIXED_PATH],
[
  unix_path="[$]$1"
  if test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.cygwin"; then
    windows_path=`$CYGPATH -m "$unix_path"`
    $1="$windows_path"
  elif test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.msys"; then
    windows_path=`cmd //c echo $unix_path`
    $1="$windows_path"
  elif test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.wsl"; then
    windows_path=`$WSLPATH -m "$unix_path"`
    $1="$windows_path"
  fi
])

# Helper function which possibly converts a path using DOS-style short mode.
# If so, the updated path is stored in $new_path.
# $1: The path to check
AC_DEFUN([UTIL_MAKE_WINDOWS_SPACE_SAFE_CYGWIN],
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
AC_DEFUN([UTIL_MAKE_WINDOWS_SPACE_SAFE_MSYS],
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

# Helper function which possibly converts a path using DOS-style short mode.
# If so, the updated path is stored in $new_path.
# $1: The path to check
AC_DEFUN([UTIL_MAKE_WINDOWS_SPACE_SAFE_WSL],
[
  input_path="$1"
  # Check if we need to convert this using DOS-style short mode. If the path
  # contains just simple characters, use it. Otherwise (spaces, weird characters),
  # take no chances and rewrite it.
  # Note: m4 eats our [], so we need to use @<:@ and @:>@ instead.
  has_forbidden_chars=`$ECHO "$input_path" | $GREP [[^-_/:a-zA-Z0-9\\.]]`
  if test "x$has_forbidden_chars" != x; then
    # Now convert it to mixed DOS-style, short mode (no spaces, and / instead of \)
    TOPDIR_windows="$TOPDIR"
    UTIL_REWRITE_AS_WINDOWS_MIXED_PATH([TOPDIR_windows])
    # First convert to Windows path to make input valid for cmd
    UTIL_REWRITE_AS_WINDOWS_MIXED_PATH([input_path])
    # Reset PATH since it can contain a mix of WSL/linux paths and Windows paths from VS,
    # which, in combination with WSLENV, will make the WSL layer complain
    old_path="$PATH"
    PATH=
    new_path=`$CMD /c $TOPDIR_windows/make/scripts/windowsShortName.bat "$input_path" \
        | $SED -e 's|\r||g' \
        | $TR \\\\\\\\ / | $TR 'ABCDEFGHIJKLMNOPQRSTUVWXYZ' 'abcdefghijklmnopqrstuvwxyz'`
    # Rewrite back to unix style
    PATH="$old_path"
    UTIL_REWRITE_AS_UNIX_PATH([new_path])
  fi
])

# FIXME: The UTIL_FIXUP_*_CYGWIN/MSYS is most likely too convoluted
# and could probably be heavily simplified. However, all changes in this
# area tend to need lot of testing in different scenarios, and in lack of
# proper unit testing, cleaning this up has not been deemed worth the effort
# at the moment.

AC_DEFUN([UTIL_FIXUP_PATH_CYGWIN],
[
  # Input might be given as Windows format, start by converting to
  # unix format.
  path="[$]$1"
  new_path=`$CYGPATH -u "$path"`

  UTIL_ABSOLUTE_PATH(new_path)

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
  UTIL_MAKE_WINDOWS_SPACE_SAFE_CYGWIN([$new_path])

  if test "x$path" != "x$new_path"; then
    $1="$new_path"
    AC_MSG_NOTICE([Rewriting $1 to "$new_path"])
  fi
])

AC_DEFUN([UTIL_FIXUP_PATH_MSYS],
[
  path="[$]$1"
  has_colon=`$ECHO $path | $GREP ^.:`
  new_path="$path"
  if test "x$has_colon" = x; then
    # Not in mixed or Windows style, start by that.
    new_path=`cmd //c echo $path`
  fi

  UTIL_ABSOLUTE_PATH(new_path)

  UTIL_MAKE_WINDOWS_SPACE_SAFE_MSYS([$new_path])
  UTIL_REWRITE_AS_UNIX_PATH(new_path)
  if test "x$path" != "x$new_path"; then
    $1="$new_path"
    AC_MSG_NOTICE([Rewriting $1 to "$new_path"])
  fi

  # Save the first 10 bytes of this path to the storage, so fixpath can work.
  all_fixpath_prefixes=("${all_fixpath_prefixes@<:@@@:>@}" "${new_path:0:10}")
])

AC_DEFUN([UTIL_FIXUP_PATH_WSL],
[
  # Input might be given as Windows format, start by converting to
  # unix format.
  new_path="[$]$1"
  UTIL_REWRITE_AS_UNIX_PATH([new_path])

  UTIL_ABSOLUTE_PATH(new_path)

  # Call helper function which possibly converts this using DOS-style short mode.
  # If so, the updated path is stored in $new_path.
  UTIL_MAKE_WINDOWS_SPACE_SAFE_WSL([$new_path])

  if test "x$path" != "x$new_path"; then
    $1="$new_path"
    AC_MSG_NOTICE([Rewriting $1 to "$new_path"])
  fi
])

AC_DEFUN([UTIL_FIXUP_EXECUTABLE_CYGWIN],
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
  UTIL_MAKE_WINDOWS_SPACE_SAFE_CYGWIN([$input_to_shortpath])
  # remove trailing .exe if any
  new_path="${new_path/%.exe/}"
])

AC_DEFUN([UTIL_FIXUP_EXECUTABLE_MSYS],
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
  UTIL_REWRITE_AS_UNIX_PATH(new_path)

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
    UTIL_REWRITE_AS_UNIX_PATH(new_path)

    new_path=`$WHICH "$new_path" 2> /dev/null`
    # bat and cmd files are not always considered executable in MSYS causing which
    # to not find them
    if test "x$new_path" = x \
        && test "x`$ECHO \"$path\" | $GREP -i -e \"\\.bat$\" -e \"\\.cmd$\"`" != x \
        && test "x`$LS \"$path\" 2>/dev/null`" != x; then
      new_path="$path"
      UTIL_REWRITE_AS_UNIX_PATH(new_path)
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
    UTIL_MAKE_WINDOWS_SPACE_SAFE_MSYS([$new_path])
    # Output is in $new_path
    UTIL_REWRITE_AS_UNIX_PATH(new_path)
    # remove trailing .exe if any
    new_path="${new_path/%.exe/}"

    # Save the first 10 bytes of this path to the storage, so fixpath can work.
    all_fixpath_prefixes=("${all_fixpath_prefixes@<:@@@:>@}" "${new_path:0:10}")
  fi
])

AC_DEFUN([UTIL_FIXUP_EXECUTABLE_WSL],
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
  UTIL_REWRITE_AS_UNIX_PATH([new_path])

  # Now try to locate executable using which
  new_path_bak="$new_path"
  new_path=`$WHICH "$new_path" 2> /dev/null`
  # bat and cmd files are not considered executable in WSL
  if test "x$new_path" = x \
      && test "x`$ECHO \"$path\" | $GREP -i -e \"\\.bat$\" -e \"\\.cmd$\"`" != x \
      && test "x`$LS \"$path\" 2>/dev/null`" != x; then
    new_path="$new_path_back"
  fi
  if test "x$new_path" = x; then
    # Oops. Which didn't find the executable.
    # The splitting of arguments from the executable at a space might have been incorrect,
    # since paths with space are more likely in Windows. Give it another try with the whole
    # argument.
    path="$complete"
    arguments="EOL"
    new_path="$path"
    UTIL_REWRITE_AS_UNIX_PATH([new_path])
    new_path_bak="$new_path"
    new_path=`$WHICH "$new_path" 2> /dev/null`
    # bat and cmd files are not considered executable in WSL
    if test "x$new_path" = x \
        && test "x`$ECHO \"$path\" | $GREP -i -e \"\\.bat$\" -e \"\\.cmd$\"`" != x \
        && test "x`$LS \"$path\" 2>/dev/null`" != x; then
      new_path="$new_path_bak"
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

  # In WSL, suffixes must be present for Windows executables
  if test ! -f "$new_path"; then
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
  UTIL_MAKE_WINDOWS_SPACE_SAFE_WSL([$input_to_shortpath])
])

