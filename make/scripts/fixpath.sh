#!/bin/bash

PATHTOOL=cygpath
#PATHTOOL=wslpath


DRIVEPREFIX=/cygdrive
#DRIVEPREFIX=
#DRIVEPREFIX=/mnt

CMD=$DRIVEPREFIX/c/Windows/System32/cmd.exe

ENVROOT="c:\cygwin64"
#ENVROOT='\\wsl$\Ubuntu-20.04'
#ENVROOT="c:\msys64"
#ENVROOT=

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

cygwin_convert_to_win() {
  converted=""

  old_ifs="$IFS"
  IFS=":"
  for arg in $1; do
    mixedpath=""
    if [[ $arg =~ (^[^/]*)($DRIVEPREFIX/)([a-z])(/[^/]+.*$) ]] ; then
      # Start looking for drive prefix
      prefix="${BASH_REMATCH[1]}"
      mixedpath="${BASH_REMATCH[3]}:${BASH_REMATCH[4]}"
    elif [[ $arg =~ (^[^/]*)(/[^/]+/[^/]+.*$) ]] ; then
      # Does arg contain a potential unix path? Check for /foo/bar
      prefix="${BASH_REMATCH[1]}"
      pathmatch="${BASH_REMATCH[2]}"
      if [[ $ENVROOT == "" ]]; then
        echo fixpath: failure: Path "'"$pathmatch"'" cannot be converted to Windows path 1>&2
        exit 1
      fi
      mixedpath="$ENVROOT$pathmatch"
    fi
    if [[ $mixedpath != "" ]]; then
      # If it was a converted path, change slash to backslash
      arg="$prefix${mixedpath//'/'/'\'}"
    fi

    if [[ "$converted" = "" ]]; then
      converted="$arg"
    else
      converted="$converted:$arg"
    fi
  done
  IFS="$old_ifs"

  result="$converted"
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

cygwin_convert_at_file() {
  infile="$1"
  if [[ -e $infile ]] ; then
    tempdir=$(mktemp -dt fixpath.XXXXXX)
    TEMPDIRS="$TEMPDIRS $tempdir"

    while read line; do
      cygwin_convert_to_win "$line" >> $tempdir/atfile
    done < $infile
    result="@$tempdir/atfile"
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


os_env="$1"
action="$2"
shift 2

if [[ "$action" == "exec-detach" ]] ; then
  DETACH=true
  action=exec
fi

if [[ "$action" == "print" ]] ; then
  args=""
  for arg in "$@" ; do
    if [[ $arg =~ ^@(.*$) ]] ; then
      cygwin_convert_at_file "${BASH_REMATCH[1]}"
    else
      cygwin_convert_to_win "$arg"
    fi
    args="$args$result "
  done
  # FIXME: fix quoting?
  echo "$args"
elif [[ "$action" == "verify" ]] ; then
  orig="$1"
  cygwin_verify_conversion "$orig"
  exit $?
elif [[ "$action" == "import" ]] ; then
  orig="$1"
  cygwin_import_to_unix "$orig"
  echo "$result"
elif [[ "$action" == "exec" ]] ; then
  for arg in "$@" ; do

    win_style=$(cygwin_convert_to_win "$arg")
    args="$args $win_style"
  done
  # FIXME: fix quoting
  echo "$args"
else
  echo Unknown operation: "$action"
fi
