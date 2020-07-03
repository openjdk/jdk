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
AC_DEFUN([UTIL_FIXUP_PATH],
[
  # Only process if variable expands to non-empty
  if test "x[$]$1" != x; then
    if test "x$OPENJDK_BUILD_OS" = "xwindows"; then
      path="[$]$1"
      imported_path=`$BASH $TOPDIR/make/scripts/fixpath.sh import "$path"`
      $BASH $TOPDIR/make/scripts/fixpath.sh verify "$imported_path"
      if test $? -ne 0; then
        echo failed to import $path
      fi
      if test "x$imported_path" != "x$path"; then
        $1="$imported_path"
      else
        $1="$path"
      fi
    else
      # We're on a unix platform. Hooray! :)
      path="[$]$1"
      has_space=`$ECHO "$path" | $GREP " "`
      if test "x$has_space" != x; then
        AC_MSG_NOTICE([The path of $1, which resolves as "$path", is invalid.])
        AC_MSG_ERROR([Spaces are not allowed in this path.])
      fi

      # Make the path absolute
      new_path="$path"

      # Use eval to expand a potential ~. This technique does not work if there
      # are spaces in the path (which is valid at this point on Windows), so only
      # try to apply it if there is an actual ~ first in the path.
      if [ [[ "$new_path" = "~"* ]] ]; then
        eval new_path="$new_path"
        if test ! -f "$new_path" && test ! -d "$new_path"; then
          AC_MSG_ERROR([The new_path of $1, which resolves as "$new_path", is not found.])
        fi
      fi

      if test -d "$new_path"; then
        path="`cd "$new_path"; $THEPWDCMD -L`"
      else
        dir="`$DIRNAME "$new_path"`"
        base="`$BASENAME "$new_path"`"
        path="`cd "$dir"; $THEPWDCMD -L`/$base"
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
    if test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.cygwin"; then
      $LDD $1 2>&1 | $GREP -q cygwin1.dll
    elif test "x$OPENJDK_BUILD_OS_ENV" = "xwindows.msys2"; then
      $LDD $1 2>&1 | $GREP -q msys-2.0.dll
    elif test "x$OPENJDK_BUILD_OS" = "xwindows"; then
      # On WSL, we can differentiate between ELF and PE files
      $FILE $1 2>&1 | $GREP -q ELF
    else
      true
    fi

    if test $? -eq 0; then
      RESULT=unix
    else
      RESULT=windows
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
AC_DEFUN([UTIL_FIXUP_EXECUTABLE],
[
  # Only process if variable expands to non-empty

  if test "x[$]$1" != x; then
    # First separate the path from the arguments. This will split at the first
    # space.
    complete="[$]$1"
    [ if [[ $complete =~ ^$FIXPATH ]]; then
      complete="${complete#$FIXPATH }"
      prefix="$FIXPATH "
    else
      prefix=""
    fi ]
    path="${complete%% *}"
    tmp="$complete EOL"
    arguments="${tmp#* }"

    # Cannot rely on the command "which" here since it doesn't always work.
    contains_slash=`$ECHO "$path" | $GREP -e / -e \\`
    if test -z "$contains_slash"; then
      # Executable has no directories. Look in $PATH.
      IFS_save="$IFS"
      IFS=:
      for p in $PATH; do
        if test -f "$p/$path" && test -x "$p/$path"; then
          new_path="$p/$path"
          break
        fi
      done
      IFS="$IFS_save"
      if test "x$new_path" = x; then
        AC_MSG_NOTICE([The path of $1, which resolves as "$complete", is not found.])
        has_space=`$ECHO "$complete" | $GREP " "`
        if test "x$has_space" != x; then
          AC_MSG_NOTICE([This might be caused by spaces in the path, which is not allowed.])
        fi
        AC_MSG_ERROR([Cannot locate the the path of $1])
      fi
    else
      # This is a path with slashes, don't look at $PATH
      new_path="$path"
    fi

    if test "x$OPENJDK_BUILD_OS" = "xwindows"; then
      ## FIXME: should do something about .exe..?
      new_path=`$BASH $TOPDIR/make/scripts/fixpath.sh import "$new_path"`
      $BASH $TOPDIR/make/scripts/fixpath.sh verify "$new_path"
      if test $? -ne 0; then
        # retry but assume spaces are part of filename
        path="$complete"

        new_path=`$BASH $TOPDIR/make/scripts/fixpath.sh import "$path"`
        $BASH $TOPDIR/make/scripts/fixpath.sh verify "$new_path"
        if test $? -ne 0; then
          echo failed to import exec $new_path FINAL
        fi
        arguments="EOL"
      fi

      if test "x$prefix" = "x"; then
        UTIL_CHECK_WINENV_EXEC_TYPE("$new_path")
        if test "x$RESULT" = xwindows; then
          prefix="$FIXPATH "
        fi
      fi
    fi

    # Now join together the path and the arguments once again
    if test "x$arguments" != xEOL; then
      new_complete="$prefix$new_path ${arguments% *}"
    else
      new_complete="$prefix$new_path"
    fi
    if test "x$complete" != "x$new_complete"; then
      $1="$new_complete"
# FIXME: this is just noise.
#      AC_MSG_NOTICE([Rewriting $1 to "$new_complete"])
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
      cd `$THEPWDCMD -P`
      sym_link_dir=`$THEPWDCMD -P`
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
        sym_link_dir=`$THEPWDCMD -P`
        sym_link_file=`$BASENAME $ISLINK`
        let COUNTER=COUNTER+1
      done
      cd $STARTDIR
      $1=$sym_link_dir/$sym_link_file
    fi
  fi
])

