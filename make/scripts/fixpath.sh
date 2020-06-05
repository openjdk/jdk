#!/bin/bash

DRIVEPREFIX=/cygdrive
DRIVEPREFIX=
ENVROOT=c:/cygwin64
ENVROOT='\\wsl$\Ubuntu-20.04'

TEMPDIRS=""
trap "cleanup" EXIT

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
    if [[ $arg =~ (^[^/]*)($DRIVEPREFIX/)([a-z])(/[^/]+.*$) ]] ; then
      # Start looking for drive prefix
      arg="${BASH_REMATCH[1]}${BASH_REMATCH[3]}:${BASH_REMATCH[4]}"
    elif [[ $arg =~ (^[^/]*)(/[^/]+/[^/]+.*$) ]] ; then
      # Does arg contain a potential unix path? Check for /foo/bar
      arg="${BASH_REMATCH[1]}$ENVROOT${BASH_REMATCH[2]}"
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
elif [[ "$action" == "import" ]] ; then
  orig="$1"
  unix_style=$(cygwin_import_to_unix "$orig")
  print "$unix_style"
  :
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
