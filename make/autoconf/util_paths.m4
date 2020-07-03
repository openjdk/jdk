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

###############################################################################
# Appends a string to a path variable, only adding the : when needed.
AC_DEFUN([UTIL_APPEND_TO_PATH],
[
  if test "x$2" != x; then
    if test "x[$]$1" = x; then
      $1="$2"
    else
      $1="[$]$1:$2"
    fi
  fi
])

###############################################################################
# Prepends a string to a path variable, only adding the : when needed.
AC_DEFUN([UTIL_PREPEND_TO_PATH],
[
  if test "x$2" != x; then
    if test "x[$]$1" = x; then
      $1="$2"
    else
      $1="$2:[$]$1"
    fi
  fi
])

###############################################################################
# This will make sure the given variable points to a full and proper
# path. This means:
# 1) There will be no spaces in the path. On unix platforms,
#    spaces in the path will result in an error. On Windows,
#    the path will be rewritten using short-style to be space-free.
# 2) The path will be absolute, and it will be in unix-style (on
#     cygwin).
# $1: The name of the variable to fix
# $2: if NOFAIL, errors will be silently ignored
AC_DEFUN([UTIL_FIXUP_PATH],
[
  # Only process if variable expands to non-empty
  path="[$]$1"
  if test "x$path" != x; then
    if test "x$OPENJDK_BUILD_OS" = "xwindows"; then
      imported_path=`$BASH $TOPDIR/make/scripts/fixpath.sh import "$path"`
      $BASH $TOPDIR/make/scripts/fixpath.sh verify "$imported_path"
      if test $? -ne 0; then
        if test "x$2" != "xNOFAIL"; then
          AC_MSG_ERROR([The path of $1, which resolves as "$path", could not be imported.])
        else
          imported_path=""
        fi
      fi
      if test "x$imported_path" != "x$path"; then
        $1="$imported_path"
      fi
    else
      [ if [[ "$path" =~ " " ]]; then ]
        if test "x$2" != "xNOFAIL"; then
          AC_MSG_NOTICE([The path of $1, which resolves as "$path", is invalid.])
          AC_MSG_ERROR([Spaces are not allowed in this path.])
        else
          path=""
        fi
      fi

      # Use eval to expand a potential ~.
      eval new_path="$path"
      if test ! -e "$new_path"; then
        if test "x$2" != "xNOFAIL"; then
          AC_MSG_ERROR([The path of $1, which resolves as "$new_path", is not found.])
        else
          new_path=""
        fi
      fi

      # Make the path absolute
      if test "x$new_path" != x; then
        if test -d "$new_path"; then
          path="`cd "$new_path"; pwd -L`"
        else
          dir="`$DIRNAME "$new_path"`"
          base="`$BASENAME "$new_path"`"
          path="`cd "$dir"; pwd -L`/$base"
        fi
      else
        path=""
      fi

      $1="$path"
    fi
  fi
])

###############################################################################
# Check if the given file is a unix-style or windows-style executable, that is,
# if it expects paths in unix-style or windows-style.
# Returns "windows" or "unix" in $RESULT.
AC_DEFUN([UTIL_CHECK_WINENV_EXEC_TYPE],
[
  # For cygwin and msys2, if it's linked with the correct helper lib, it
  # accept unix paths
  if test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.cygwin" || \
      test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.msys2"; then
    linked_libs=`$LDD $1 2>&1`
    if test $? -ne 0; then
      # Non-binary files (e.g. shell scripts) are unix files
      RESULT=unix
    else
      [ if [[ "$linked_libs" =~ $WINENV_MARKER_DLL ]]; then ]
        RESULT=unix
      else
        RESULT=windows
      fi
    fi
  elif test "x$OPENJDK_BUILD_OS" = "xwindows"; then
    # On WSL, we can check if it is a PE file
    file_type=`$FILE -b $1 2>&1`
    [ if [[ $file_type =~ PE.*Windows ]]; then ]
      RESULT=windows
    else
      RESULT=unix
    fi
  else
    RESULT=unix
  fi
])

###############################################################################
# This will make sure the given variable points to a executable
# with a full and proper path. This means:
# 1) There will be no spaces in the path. On unix platforms,
#    spaces in the path will result in an error. On Windows,
#    the path will be rewritten using short-style to be space-free.
# 2) The path will be absolute, and it will be in unix-style (on
#     cygwin).
# Any arguments given to the executable is preserved.
# If the input variable does not have a directory specification, then
# it need to be in the PATH.
# $1: The name of the variable to fix
# $2: Where to look for the command (replaces $PATH)
AC_DEFUN([UTIL_FIXUP_EXECUTABLE],
[
  input="[$]$1"

  # Only process if variable expands to non-empty
  if test "x$input" != x; then
    # First separate the path from the arguments. This will split at the first
    # space.
    [ if [[ "$OPENJDK_BUILD_OS" = "windows" && input =~ ^$FIXPATH ]]; then
      line="${input#$FIXPATH }"
      prefix="$FIXPATH "
    else
      line="$input"
      prefix=""
    fi ]
    path="${line%% *}"
    tmp="$line EOL"
    arguments="${tmp#* }"

    [ if ! [[ "$path" =~ /|\\ ]]; then ]
      command_type=`type -t "$path"`
      if test "x$command_type" = xbuiltin || test "x$command_type" = xkeyword; then
        # Shell builtin or keyword; we're done here
        new_path="$path"
      else
        # Search in $PATH using bash built-in 'type -p'.
        old_path="$PATH"
        if test "x$2" != x; then
          PATH="$2"
        fi
        new_path=`type -p "$path"`
        if test "x$new_path" = x && test "x$OPENJDK_BUILD_OS" = "xwindows"; then
          # Try again with .exe
          new_path=`type -p "$path.exe"`
        fi
        PATH="$old_path"

        if test "x$new_path" = x; then
          AC_MSG_NOTICE([The command for $1, which resolves as "$input", is not found in the PATH.])
          AC_MSG_ERROR([Cannot locate $path])
        fi
      fi
    else
      # This is a path with slashes, don't look at $PATH
      if test "x$OPENJDK_BUILD_OS" = "xwindows"; then
        # fixpath.sh import will do all heavy lifting for us
        new_path=`$BASH $TOPDIR/make/scripts/fixpath.sh import "$path"`

        if test ! -e $new_path; then
          # It failed, but maybe spaces were part of the path and not separating
          # the command and argument. Retry using that assumption.
          new_path=`$BASH $TOPDIR/make/scripts/fixpath.sh import "$input"`
          if test ! -e $new_path; then
            AC_MSG_NOTICE([The command for $1, which resolves as "$input", can not be found.])
            AC_MSG_ERROR([Cannot locate $input])
          fi
          # It worked, clear all "arguments"
          arguments="EOL"
        fi
      else # on unix
        # Make absolute
        $1="$path"
        UTIL_FIXUP_PATH($1, NOFAIL)
        new_path="[$]$1"

        if test ! -e $new_path; then
          AC_MSG_NOTICE([The command for $1, which resolves as "$input", is not found])
          [ if [[ "$path" =~ " " ]]; then ]
            AC_MSG_NOTICE([This might be caused by spaces in the path, which is not allowed.])
          fi
          AC_MSG_ERROR([Cannot locate $path])
        fi
        if test ! -x $new_path; then
          AC_MSG_NOTICE([The command for $1, which resolves as "$input", is not executable.])
          AC_MSG_ERROR([Cannot execute command at $path])
        fi
      fi # end on unix
    fi # end with or without slashes

    # Now we have a usable command as new_path, with arguments in arguments
    if test "x$OPENJDK_BUILD_OS" = "xwindows"; then
      if test "x$prefix" = x; then
        # Only mess around if prefix was not given
        UTIL_CHECK_WINENV_EXEC_TYPE("$new_path")
        if test "x$RESULT" = xwindows; then
          prefix="$FIXPATH "
          # make sure we have an .exe suffix
        else
          # If we have gotten a .exe suffix, remove it
          :
        fi
      fi
    fi

    # Now join together the path and the arguments once again
    if test "x$arguments" != xEOL; then
      new_complete="$prefix$new_path ${arguments% *}"
    else
      new_complete="$prefix$new_path"
    fi
    if test "x$input" != "x$new_complete"; then
      $1="$new_complete"
# FIXME: remove this.
      AC_MSG_NOTICE([Rewriting $1 to "$new_complete"])
    fi
  fi
])

###############################################################################
AC_DEFUN([UTIL_REMOVE_SYMBOLIC_LINKS],
[
  if test "x$OPENJDK_BUILD_OS" != xwindows; then
    # Follow a chain of symbolic links. Use readlink
    # where it exists, else fall back to horribly
    # complicated shell code.
    if test "x$READLINK_TESTED" != yes; then
      # On MacOSX there is a readlink tool with a different
      # purpose than the GNU readlink tool. Check the found readlink.
      READLINK_ISGNU=`$READLINK --version 2>&1 | $GREP GNU`
      # If READLINK_ISGNU is empty, then it's a non-GNU readlink. Don't use it.
      READLINK_TESTED=yes
    fi

    if test "x$READLINK" != x && test "x$READLINK_ISGNU" != x; then
      $1=`$READLINK -f [$]$1`
    else
      # Save the current directory for restoring afterwards
      STARTDIR=$PWD
      COUNTER=0
      sym_link_dir=`$DIRNAME [$]$1`
      sym_link_file=`$BASENAME [$]$1`
      cd $sym_link_dir
      # Use -P flag to resolve symlinks in directories.
      cd `pwd -P`
      sym_link_dir=`pwd -P`
      # Resolve file symlinks
      while test $COUNTER -lt 20; do
        ISLINK=`$LS -l $sym_link_dir/$sym_link_file | $GREP '\->' | $SED -e 's/.*-> \(.*\)/\1/'`
        if test "x$ISLINK" == x; then
          # This is not a symbolic link! We are done!
          break
        fi
        # Again resolve directory symlinks since the target of the just found
        # link could be in a different directory
        cd `$DIRNAME $ISLINK`
        sym_link_dir=`pwd -P`
        sym_link_file=`$BASENAME $ISLINK`
        let COUNTER=COUNTER+1
      done
      cd $STARTDIR
      $1=$sym_link_dir/$sym_link_file
    fi
  fi
])

