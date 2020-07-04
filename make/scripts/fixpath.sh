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

function setup() {
  while getopts "e:p:r:t:c:" opt; do
    case "$opt" in
    e) PATHTOOL="$OPTARG" ;;
    p) DRIVEPREFIX="$OPTARG" ;;
    r) ENVROOT="$OPTARG" ;;
    t) WINTEMP="$OPTARG" ;;
    c) CMD="$OPTARG" ;;
    ?)
      # optargs found argument error
      exit 1
      ;;
    esac
  done

  shift $((OPTIND-1))
  ACTION="$1"

  # Locate variables ourself if not giving from caller
  if [[ -z ${PATHTOOL+x} ]]; then
    PATHTOOL="$(type -p cygpath)"
    if [[ $PATHTOOL == "" ]]; then
      PATHTOOL="$(type -p wslpath)"
      if [[ $PATHTOOL == "" ]]; then
        echo fixpath: failure: Cannot locate cygpath or wslpath >&2
        exit 2
      fi
    fi
  fi

  if [[ -z ${DRIVEPREFIX+x} ]]; then
    winroot="$($PATHTOOL -u c:/)"
    DRIVEPREFIX="${winroot%/c/}"
  fi

  if [[ -z ${ENVROOT+x} ]]; then
    unixroot="$($PATHTOOL -w / 2> /dev/null)"
    # Remove trailing backslash
    ENVROOT="${unixroot%\\}"
  elif [[ "$ENVROOT" == "[unavailable]" ]]; then
    ENVROOT=""
  fi

  if [[ -z ${CMD+x} ]]; then
    CMD="$DRIVEPREFIX/c/windows/system32/cmd.exe"
  fi

  if [[ -z ${WINTEMP+x} ]]; then
    wintemp_win="$($CMD /q /c echo %TEMP% 2>/dev/null | tr -d \\n\\r)"
    WINTEMP="$($PATHTOOL -u "$wintemp_win")"
  fi

  # Make regexp tests case insensitive
  shopt -s nocasematch
  # Prohibit msys2 from meddling with paths
  export MSYS2_ARG_CONV_EXCL="*"

}

# Cleanup handling
TEMPDIRS=""
trap "cleanup" EXIT
function cleanup() {
  if [[ "$TEMPDIRS" != "" ]]; then
    rm -rf $TEMPDIRS
  fi
}

function convert_pathlist_to_win() {
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

function convert_to_win() {
  arg="$1"
  if [[ $arg =~ : ]]; then
    convert_pathlist_to_win "$arg"
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

function verify_conversion() {
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

function verify_current_dir() {
  arg="$PWD"
  if [[ $arg =~ ^($DRIVEPREFIX/)([a-z])(/[^/]+.*$) ]] ; then
    return 0
  elif [[ $arg =~ ^(/[^/]+.*$) ]] ; then
    if [[ $ENVROOT == "" || $ENVROOT =~ ^\\\\.* ]]; then
      return 1
    fi
    return 0
  fi
  return 1
}

function convert_at_file() {
  infile="$1"
  if [[ -e $infile ]] ; then
    tempdir=$(mktemp -dt fixpath.XXXXXX -p "$WINTEMP")
    TEMPDIRS="$TEMPDIRS $tempdir"

    while read line; do
      convert_to_win "$line"
      echo "$result" >> $tempdir/atfile
    done < $infile
    convert_to_win "$tempdir/atfile"
    result="@$result"
  else
    result="@$infile"
  fi
}

function import_to_unix() {
  path="$1"
  path="${path#"${path%%[![:space:]]*}"}"
  path="${path%"${path##*[![:space:]]}"}"

  if [[ $path =~ ^.:[/\\].*$ ]] || [[ "$path" =~ ^"$ENVROOT"\\.*$ ]] ; then
    # We really don't want windows paths as input, but try to handle them anyway
    path="$($PATHTOOL -u "$path")"
    # Path will now be absolute
  else
    # Make path absolute, and resolve '..' in path
    dirpart="$(dirname "$path")"
    dirpart="$(cd "$dirpart" 2>&1 > /dev/null && pwd)"
    if [[ $? -ne 0 ]]; then
      echo fixpath: failure: Directory containing path "'"$path"'" does not exist 1>&2
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
      if [[ -e $path.exe ]]; then
        path="$path.exe"
      else
        echo fixpath: failure: Path "'"$path"'" does not exist 1>&2
        exit 1
      fi
    fi
  fi

  if [[ "$path" =~ " " ]]; then
    echo fixpath: failure: Path "'"$path"'" contains space 1>&2
    exit 1
  fi

  result="$path"
}

function import_pathlist() {
  converted=""

  old_ifs="$IFS"
  IFS=";"
  for arg in $1; do
    if ! [[ $arg =~ ^" "+$ ]]; then
      import_to_unix "$arg"

      if [[ "$converted" = "" ]]; then
        converted="$result"
      else
        converted="$converted:$result"
      fi
    fi
  done
  IFS="$old_ifs"

  result="$converted"
}

function convert_command_line() {
  converted_args=""
  for arg in "$@" ; do
    if [[ $arg =~ ^@(.*$) ]] ; then
      convert_at_file "${BASH_REMATCH[1]}"
    else
      convert_to_win "$arg"
    fi
    converted_args="$converted_args$result "
  done
  # FIXME: fix quoting?
  result="$converted_args"
}

function exec_command_line() {
  verify_current_dir
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
        convert_to_win "$arg"
        export $key="$result"
        export WSLENV=$WSLENV:$key/w
      else
        # The actual command will be executed by bash, so don't convert it
        command="$arg"
      fi
    else
      if [[ $arg =~ ^@(.*$) ]] ; then
        convert_at_file "${BASH_REMATCH[1]}"
      else
        convert_to_win "$arg"
      fi
      collected_args=("${collected_args[@]}" "$result")
    fi
  done
  # Now execute it
  if [[ -v DEBUG_FIXPATH ]]; then
    echo fixpath: debug: "$command" "${collected_args[@]}" 1>&2
  fi
  if [[ $ENVROOT != "" || ! -x /bin/grep ]]; then
    "$command" "${collected_args[@]}"
  else
    # For WSL1, automatically strip away warnings from WSLENV=PATH/l
    "$command" "${collected_args[@]}" 2> >(/bin/grep -v "ERROR: UtilTranslatePathList" 1>&2)
  fi
}

#### MAIN FUNCTION

setup "$@"
shift $((OPTIND))

if [[ "$ACTION" == "import" ]] ; then
  import_pathlist "$@"
  echo "$result"
elif [[ "$ACTION" == "print" ]] ; then
  convert_command_line "$@"
  echo "$result"
elif [[ "$ACTION" == "exec" ]] ; then
  exec_command_line "$@"
  # Propagate exit code
  exit $?
elif [[ "$ACTION" == "verify" ]] ; then
  verify_conversion "$@"
  exit $?
else
  echo Unknown operation: "$ACTION" 1>&2
  echo Supported operations: import print exec verify 1>&2
fi
