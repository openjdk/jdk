#!/bin/sh
#
# Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
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

# Script for updating a patch file as per the shuffled/unshuffled source location.

usage() {
      echo "Usage: $0 [-h|--help] [-v|--verbose] <repo> <input_patch> <output_patch>"
      echo "where:"
      echo "  <repo>          is one of: corba, jaxp, jaxws, jdk, langtools, nashorn"
      echo "                  [Note: patches from other repos do not need updating]"
      echo "  <input_patch>   is the input patch file, that needs shuffling/unshuffling"
      echo "  <output_patch>  is the updated patch file "
      echo " "
      exit 1
}

SCRIPT_DIR=`dirname $0`
UNSHUFFLE_LIST=$SCRIPT_DIR"/unshuffle_list.txt"

if [ ! -f "$UNSHUFFLE_LIST" ] ; then
  echo "FATAL: cannot find $UNSHUFFLE_LIST" >&2
  exit 1
fi

vflag="false"
while [ $# -gt 0 ]
do
  case $1 in
    -h | --help )
      usage
      ;;

    -v | --verbose )
      vflag="true"
      ;;

    -*)  # bad option
      usage
      ;;

     * )  # non option
      break
      ;;
  esac
  shift
done

# Make sure we have the right number of arguments
if [ ! $# -eq 3 ] ; then
  echo "ERROR: Invalid number of arguments." >&2
  usage
fi

# Check the given repo
repos="corba jaxp jaxws jdk langtools nashorn"
repo="$1"
found="false"
for r in $repos ; do
  if [ $repo = "$r" ] ; then
    found="true"
    break;
  fi
done
if [ $found = "false" ] ; then
  echo "ERROR: Unknown repo: $repo. Should be one of [$repos]." >&2
  usage
fi

# Check given input/output files
input="$2"
if [ "x$input" = "x-" ] ; then
  input="/dev/stdin"
fi

if [ ! -f $input -a "x$input" != "x/dev/stdin" ] ; then
  echo "ERROR: Cannot find input patch file: $input" >&2
  exit 1
fi

output="$3"
if [ "x$output" = "x-" ] ; then
  output="/dev/stdout"
fi

if [ -f $output -a "x$output" != "x/dev/stdout" ] ; then
  echo "ERROR: Output patch already exists: $output" >&2
  exit 1
fi

what=""  ## shuffle or unshuffle

verbose() {
  if [ ${vflag} = "true" ] ; then
    echo "$@" >&2
  fi
}

unshuffle() {
  line=$@
  verbose "Attempting to rewrite: \"$line\""

  # Retrieve the file name
  path=
  if echo "$line" | egrep '^diff' > /dev/null ; then
    if ! echo "$line" | egrep '\-\-git' > /dev/null ; then
      echo "ERROR: Only git patches supported. Please use 'hg export --git ...'." >&2
      exit 1
    fi
    path="`echo "$line" | sed -e s@'diff --git a/'@@ -e s@' b/.*$'@@`"
  elif echo "$line" | egrep '^\-\-\-' > /dev/null ; then
    path="`echo "$line" | sed -e s@'--- a/'@@`"
  elif echo "$line" | egrep '^\+\+\+' > /dev/null ; then
    path="`echo "$line" | sed s@'+++ b/'@@`"
  fi
  verbose "Extracted path: \"$path\""

  # Only source can be shuffled, or unshuffled
  if ! echo "$path" | egrep '^src/.*' > /dev/null ; then
    verbose "Not a src path, skipping."
    echo "$line" >> $output
    return
  fi

  # Shuffle or unshuffle?
  if [ "${what}" = "" ] ; then
    if echo "$path" | egrep '^src/java\..*|^src/jdk\..*' > /dev/null ; then
      what="unshuffle"
    else
      what="shuffle"
    fi
    verbose "Shuffle or unshuffle: $what"
  fi

  # Find the most specific matches in the shuffle list
  matches=
  matchpath="$repo"/"$path"/x
  while [ "$matchpath" != "" ] ; do
    matchpath="`echo $matchpath | sed s@'\(.*\)/.*$'@'\1'@`"

    if [ "${what}" =  "shuffle" ] ; then
      pattern=": $matchpath$"
    else
      pattern="^$matchpath :"
    fi
    verbose "Attempting to find \"$matchpath\""
    matches=`egrep "$pattern" "$UNSHUFFLE_LIST"`
    if ! [ "x${matches}" = "x" ] ; then
      verbose "Got matches: [$matches]"
      break;
    fi

    if ! echo "$matchpath" | egrep '.*/.*' > /dev/null ; then
      break;
    fi
  done

  # Rewrite the line, if we have a match
  if ! [ "x${matches}" = "x" ] ; then
    shuffled="`echo "$matches" | sed -e s@' : .*'@@g -e s@'^[a-z]*\/'@@`"
    unshuffled="`echo "$matches" | sed -e s@'.* : '@@g -e s@'^[a-z]*\/'@@`"
    if [ "${what}" =  "shuffle" ] ; then
      newline="`echo "$line" | sed -e s@"$unshuffled"@"$shuffled"@g`"
    else
      newline="`echo "$line" | sed -e s@"$shuffled"@"$unshuffled"@g`"
    fi
    verbose "Rewriting to \"$newline\""
    echo "$newline" >> $output
  else
    echo "WARNING: no match found for $path"
    echo "$line" >> $output
  fi
}

while IFS= read -r line
do
  if echo "$line" | egrep '^diff|^\-\-\-|^\+\+\+' > /dev/null ; then
    unshuffle "$line"
  else
    printf "%s\n" "$line" >> $output
  fi
done < "$input"
