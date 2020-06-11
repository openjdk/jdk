#!/bin/bash
#
# Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

# FIXME: hack for WSL
PATH=$PATH:/usr/bin

setup() {
  if [[ $PATHTOOL == "" ]]; then
    PATHTOOL="$(type -p cygpath)"
    if [[ $PATHTOOL == "" ]]; then
      PATHTOOL="$(type -p wslpath)"
      if [[ $PATHTOOL == "" ]]; then
        echo fixpath: failure: Cannot locate cygpath or wslpath >&2
        exit 2
      fi
    fi
  fi

  if [[ $ENVROOT == "" ]]; then
    unixroot="$($PATHTOOL -w / 2> /dev/null)"
    # Remove trailing backslash
    ENVROOT=${unixroot%\\}
  fi

  if [[ $DRIVEPREFIX == "" ]]; then
    winroot=$($PATHTOOL -u c:/)
    DRIVEPREFIX=${winroot%/c/}
  fi

  if [[ $CMD == "" ]]; then
    CMD=$DRIVEPREFIX/c/windows/system32/cmd.exe
  fi

  if [[ $WINTEMP == "" ]]; then
    wintemp_win="$($CMD /q /c echo %TEMP% 2>/dev/null | tr -d \\n\\r)"
    WINTEMP="$($PATHTOOL -u "$wintemp_win")"
  fi
}

TEMPDIRS=""
trap "cleanup" EXIT

# Make regexp tests case insensitive
shopt -s nocasematch
# Prohibit msys2 from meddling with paths
export MSYS2_ARG_CONV_EXCL="*"

cleanup() {
  if [[ "$TEMPDIRS" != "" ]]; then
    rm -rf $TEMPDIRS
  fi
}

cygwin_convert_pathlist_to_win() {
    # If argument seems to be colon separated path list, and all elements
    # are possible to convert to paths, make a windows path list
    converted=""
    old_ifs="$IFS"
    IFS=":"
    pathlist_args="$1"
    IFS=':' read -r -a arg_array <<< "$pathlist_args"
    for arg in "${arg_array[@]}"; do
      mixedpath=""
      if [[ $arg =~ ^($DRIVEPREFIX/)([a-z])(/[^/]+.*$) ]] ; then
        # Start looking for drive prefix
        mixedpath="${BASH_REMATCH[2]}:${BASH_REMATCH[3]}"
        # If it was a converted path, change slash to backslash
        mixedpath="${mixedpath//'/'/'\'}"
      elif [[ "$arg" =~ ^(/[-_.*a-zA-Z0-9]+(/[-_.*a-zA-Z0-9]+)+.*$) ]] ; then
        # Does arg contain a potential unix path? Check for /foo/bar
        pathmatch="${BASH_REMATCH[1]}"
        if [[ $ENVROOT == "" ]]; then
          echo fixpath: failure: Path "'"$pathmatch"'" cannot be converted to Windows path 1>&2
          exit 1
        fi
        # If it was a converted path, change slash to backslash
        mixedpath="${pathmatch//'/'/'\'}"
        mixedpath="$ENVROOT$mixedpath"
      else
        result=""
        return 1
      fi
      if [[ $mixedpath != "" ]]; then
        arg="$mixedpath"
      fi

      if [[ "$converted" = "" ]]; then
        converted="$arg"
      else
        converted="$converted;$arg"
      fi
    done
    IFS="$old_ifs"

    result="$converted"
    return 0
}

cygwin_convert_to_win() {
  arg="$1"
  if [[ $arg =~ : ]]; then
    cygwin_convert_pathlist_to_win "$arg"
    if [[ $? -eq 0 ]]; then
      return 0
    fi
  fi
  arg="$1"

  # Arg did not contain ":", or not all elements was possible to convert to
  # Windows paths, so it was not presumed to be a pathlist.
  mixedpath=""
  if [[ $arg =~ (^[^/]*)($DRIVEPREFIX/)([a-z])(/[^/]+.*$) ]] ; then
    # Start looking for drive prefix
    prefix="${BASH_REMATCH[1]}"
    mixedpath="${BASH_REMATCH[3]}:${BASH_REMATCH[4]}"
    # If it was a converted path, change slash to backslash
    mixedpath="${mixedpath//'/'/'\'}"
  elif [[ $arg =~ (^[^/]*)(/[-_.a-zA-Z0-9]+(/[-_.a-zA-Z0-9]+)+)(.*)?$ ]] ; then
    # Does arg contain a potential unix path? Check for /foo/bar
    prefix="${BASH_REMATCH[1]}"
    pathmatch="${BASH_REMATCH[2]}"
    suffix="${BASH_REMATCH[4]}"
    if [[ $ENVROOT == "" ]]; then
      echo fixpath: failure: Path "'"$pathmatch"'" cannot be converted to Windows path 1>&2
      exit 1
    fi
    # If it was a converted path, change slash to backslash
    mixedpath="${pathmatch//'/'/'\'}"
    mixedpath="$ENVROOT$mixedpath$suffix"
  fi
  if [[ $mixedpath != "" ]]; then
    arg="$prefix$mixedpath"
  fi

  result="$arg"
}

cygwin_verify_conversion() {
  arg="$1"
  if [[ $arg =~ ^($DRIVEPREFIX/)([a-z])(/[^/]+.*$) ]] ; then
    return 0
  elif [[ $arg =~ ^(/[^/]+/[^/]+.*$) ]] ; then
    if [[ $ENVROOT != "" ]]; then
      return 0
    fi
  fi
  return 1
}

cygwin_verify_current_dir() {
  arg="$PWD"
  if [[ $arg =~ ^($DRIVEPREFIX/)([a-z])(/[^/]+.*$) ]] ; then
    return 0
  elif [[ $arg =~ ^(/[^/]+/[^/]+.*$) ]] ; then
    if [[ $ENVROOT == "" || $ENVROOT =~ ^\\\\.* ]]; then
      return 1
    fi
    return 0
  fi
  return 1
}

cygwin_convert_at_file() {
  infile="$1"
  if [[ -e $infile ]] ; then
    tempdir=$(mktemp -dt fixpath.XXXXXX -p "$WINTEMP")
    TEMPDIRS="$TEMPDIRS $tempdir"

    while read line; do
      cygwin_convert_to_win "$line"
      echo "$result" >> $tempdir/atfile
    done < $infile
    cygwin_convert_to_win "$tempdir/atfile"
    result="@$result"
  else
    result="@$infile"
  fi
}

cygwin_import_to_unix() {
  path="$1"

  if [[ $path =~ ^.:[/\\].*$ ]] ; then
    # We really don't want windows paths as input, but try to handle them anyway
    path="$($PATHTOOL -u "$path")"
    # Path will now be absolute
  else
    # Make path absolute, and resolve '..' in path
    dirpart="$(dirname "$path")"
    dirpart="$(cd "$dirpart" 2>&1 > /dev/null && pwd)"
    if [[ $? -ne 0 ]]; then
      echo fixpath: failure: Path "'"$path"'" does not exist 1>&2
      exit 1
    fi
    basepart="$(basename "$path")"
    if [[ $dirpart == / ]]; then
      # Avoid double leading /
      dirpart=""
    fi
    if [[ $basepart == / ]]; then
      # Avoid trailing /
      basepart=""
    fi
    path="$dirpart/$basepart"
  fi

  # Now turn it into a windows path
  winpath="$($PATHTOOL -w "$path" 2>/dev/null)"

  if [[ $? -eq 0 ]]; then
    if [[ ! "$winpath" =~ ^"$ENVROOT"\\.*$ ]] ; then
      # If it is not in envroot, it's a generic windows path
      if [[ ! $winpath =~ ^[-_.:\\a-zA-Z0-9]*$ ]] ; then
        # Path has forbidden characters, rewrite as short name
        shortpath="$($CMD /q /c for %I in \( "$winpath" \) do echo %~sI 2>/dev/null | tr -d \\n\\r)"
        path="$($PATHTOOL -u "$shortpath")"
        # Path is now unix style, based on short name
      fi
      # Make it lower case
      path="$(echo "$path" | tr [:upper:] [:lower:])"
    fi
  else
    # On WSL1, PATHTOOL will fail for files in envroot. If the unix path
    # exists, we assume that $path is a valid unix path.

    if [[ ! -e $path ]]; then
      echo fixpath: failure: Path "'"$path"'" does not exist 1>&2
      exit 1
    fi
  fi

  if [[ "$path" =~ " " ]]; then
    echo fixpath: failure: Path "'"$path"'" contains space 1>&2
    exit 1
  fi

  result="$path"
}

cygwin_import_pathlist() {
  converted=""

  old_ifs="$IFS"
  IFS=";"
  for arg in $1; do
    cygwin_import_to_unix "$arg"

    if [[ "$converted" = "" ]]; then
      converted="$result"
    else
      converted="$converted:$result"
    fi
  done
  IFS="$old_ifs"

  result="$converted"
}

cygwin_convert_command_line() {
  converted_args=""
  for arg in "$@" ; do
    if [[ $arg =~ ^@(.*$) ]] ; then
      cygwin_convert_at_file "${BASH_REMATCH[1]}"
    else
      cygwin_convert_to_win "$arg"
    fi
    converted_args="$converted_args$result "
  done
  # FIXME: fix quoting?
  result="$converted_args"
}

cygwin_exec_command_line() {
  cygwin_verify_current_dir
  if [[ $? -ne 0 ]]; then
    # WSL1 will just forcefully put us in C:\Windows\System32 if we execute this from
    # a unix directory. WSL2 will do the same, and print a warning. In both cases,
    # we prefer to take control.
    cd $DRIVEPREFIX/c
    echo fixpath: warning: Changing directory to $DRIVEPREFIX/c 1>&2
  fi
  collected_args=()
  command=""
  for arg in "$@" ; do
    if [[ $command == "" ]]; then
      if [[ $arg =~ ^(.*)=(.*)$ ]]; then
        # It's a leading env variable assignment
        key="${BASH_REMATCH[1]}"
        arg="${BASH_REMATCH[2]}"
        cygwin_convert_to_win "$arg"
        export $key="$result"
        export WSLENV=$WSLENV:$key/w
      else
        # The actual command will be executed by bash, so don't convert it
        command="$arg"
      fi
    else
      if [[ $arg =~ ^@(.*$) ]] ; then
        cygwin_convert_at_file "${BASH_REMATCH[1]}"
      else
        cygwin_convert_to_win "$arg"
      fi
      collected_args=("${collected_args[@]}" "$result")
    fi
  done
  # Now execute it
  if [[ -v DEBUG_FIXPATH ]]; then
    echo fixpath: debug: "$command" "${collected_args[@]}" 1>&2
  fi
  "$command" "${collected_args[@]}"
}

#### MAIN FUNCTION

setup

action="$1"
shift

if [[ "$action" == "print" ]] ; then
  cygwin_convert_command_line "$@"
  echo "$result"
elif [[ "$action" == "verify" ]] ; then
  orig="$1"
  cygwin_verify_conversion "$orig"
  exit $?
elif [[ "$action" == "import" ]] ; then
  orig="$1"
  cygwin_import_pathlist "$orig"
  echo "$result"
elif [[ "$action" == "exec" ]] ; then
  cygwin_exec_command_line "$@"
  # Propagate exit code
  exit $?
else
  echo Unknown operation: "$action"
fi
