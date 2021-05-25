#!/bin/sh
#
# Copyright (c) 2009, 2020, Oracle and/or its affiliates. All rights reserved.
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

# Shell script for generating an IDEA project from a given list of modules

usage() {
      echo "usage: $0 [-h|--help] [-v|--verbose] [-o|--output <path>] [modules]+"
      exit 1
}

SCRIPT_DIR=`dirname $0`
#assume TOP is the dir from which the script has been called
TOP=`pwd`
cd $SCRIPT_DIR; SCRIPT_DIR=`pwd`
cd $TOP;

IDEA_OUTPUT=$TOP/.idea
CUSTOM_IDEA_OUTPUT=false
VERBOSE="false"
while [ $# -gt 0 ]
do
  case $1 in
    -h | --help )
      usage
      ;;

    -v | --vebose )
      VERBOSE="true"
      ;;

    -o | --output )
      IDEA_OUTPUT=$2/.idea
      CUSTOM_IDEA_OUTPUT=true
      shift
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

mkdir -p $IDEA_OUTPUT || exit 1
cd $IDEA_OUTPUT; IDEA_OUTPUT=`pwd`

if [ "x$TOPLEVEL_DIR" = "x" ] ; then
    cd $SCRIPT_DIR/..
    TOPLEVEL_DIR=`pwd`
    cd $IDEA_OUTPUT
fi

MAKE_DIR="$SCRIPT_DIR/../make"
IDEA_MAKE="$MAKE_DIR/ide/idea/jdk"
IDEA_TEMPLATE="$IDEA_MAKE/template"

cp -rn "$IDEA_TEMPLATE"/* "$IDEA_OUTPUT"

#override template
if [ -d "$TEMPLATES_OVERRIDE" ] ; then
    for file in `ls -p "$TEMPLATES_OVERRIDE" | grep -v /`; do
        cp "$TEMPLATES_OVERRIDE"/$file "$IDEA_OUTPUT"/
    done
fi

if [ "$VERBOSE" = "true" ] ; then
  echo "output dir: $IDEA_OUTPUT"
  echo "idea template dir: $IDEA_TEMPLATE"
fi

cd $TOP ; make -f "$IDEA_MAKE/idea.gmk" -I $MAKE_DIR/.. idea MAKEOVERRIDES= OUT=$IDEA_OUTPUT/env.cfg MODULES="$*" || exit 1
cd $SCRIPT_DIR

. $IDEA_OUTPUT/env.cfg

# Expect MODULES, MODULE_NAMES, BOOT_JDK & SPEC to be set
if [ "xMODULES" = "x" ] ; then
  echo "FATAL: MODULES is empty" >&2; exit 1
fi

if [ "x$MODULE_NAMES" = "x" ] ; then
  echo "FATAL: MODULE_NAMES is empty" >&2; exit 1
fi

if [ "x$BOOT_JDK" = "x" ] ; then
  echo "FATAL: BOOT_JDK is empty" >&2; exit 1
fi

if [ "x$SPEC" = "x" ] ; then
  echo "FATAL: SPEC is empty" >&2; exit 1
fi

if [ -d "$TOPLEVEL_DIR/.hg" ] ; then
    VCS_TYPE="hg4idea"
fi

if [ -d "$TOPLEVEL_DIR/.git" ] ; then
    VCS_TYPE="Git"
fi

### Replace template variables

NUM_REPLACEMENTS=0

replace_template_file() {
    for i in $(seq 1 $NUM_REPLACEMENTS); do
      eval "sed \"s|\${FROM${i}}|\${TO${i}}|g\" $1 > $1.tmp"
      mv $1.tmp $1
    done
}

replace_template_dir() {
    for f in `find $1 -type f` ; do
        replace_template_file $f
    done
}

add_replacement() {
    NUM_REPLACEMENTS=`expr $NUM_REPLACEMENTS + 1`
    eval FROM$NUM_REPLACEMENTS='$1'
    eval TO$NUM_REPLACEMENTS='$2'
}

add_replacement "###MODULE_NAMES###" "$MODULE_NAMES"
add_replacement "###VCS_TYPE###" "$VCS_TYPE"
SPEC_DIR=`dirname $SPEC`
RELATIVE_SPEC_DIR="`realpath --relative-to=\"$TOPLEVEL_DIR\" \"$SPEC_DIR\"`"
add_replacement "###BUILD_DIR###" "$RELATIVE_SPEC_DIR"
add_replacement "###IMAGES_DIR###" "$RELATIVE_SPEC_DIR/images/jdk"
if [ "x$PATHTOOL" != "x" ]; then
  if [ "$CUSTOM_IDEA_OUTPUT" = true ]; then
    add_replacement "###BASH_RUNNER_PREFIX###" "`$PATHTOOL -am $IDEA_OUTPUT/.idea/bash.bat`"
  else
    add_replacement "###BASH_RUNNER_PREFIX###" ".idea\\\\bash.bat"
  fi
else
  add_replacement "###BASH_RUNNER_PREFIX###" ""
fi
if [ "x$PATHTOOL" != "x" ]; then
    if [ "x$JT_HOME" = "x" ]; then
      add_replacement "###JTREG_HOME###" ""
    else
      add_replacement "###JTREG_HOME###" "`$PATHTOOL -am $JT_HOME`"
    fi
else
    add_replacement "###JTREG_HOME###" "$JT_HOME"
fi

MODULE_IMLS=""
TEST_MODULE_DEPENDENCIES=""
for module in $MODULE_NAMES; do
    MODULE_IMLS="$MODULE_IMLS<module fileurl=\"file://\$PROJECT_DIR$/.idea/$module.iml\" filepath=\"\$PROJECT_DIR$/.idea/$module.iml\" /> "
    TEST_MODULE_DEPENDENCIES="$TEST_MODULE_DEPENDENCIES<orderEntry type=\"module\" module-name=\"$module\" scope=\"TEST\" /> "
done
add_replacement "###MODULE_IMLS###" "$MODULE_IMLS"
add_replacement "###TEST_MODULE_DEPENDENCIES###" "$TEST_MODULE_DEPENDENCIES"

replace_template_dir "$IDEA_OUTPUT"

### Generate module project files

(
DEFAULT_IFS="$IFS"
IFS='#'
if [ "x$PATHTOOL" != "x" ]; then
  TOPDIR_FOR_RELATIVITY_CHECKS="`echo \"$TOPLEVEL_DIR\" | tr '[:upper:]' '[:lower:]'`"
else
  TOPDIR_FOR_RELATIVITY_CHECKS="$TOPLEVEL_DIR"
fi
for value in $MODULES; do
  (
  eval "$value"
  if [ "$VERBOSE" = "true" ] ; then
    echo "generating project module: $module"
  fi
  add_replacement "###MODULE_DIR###" "src/$module"
  SOURCE_DIRS=""
  IFS=' '
  for dir in $moduleSrcDirs; do
    if [ "x$PATHTOOL" != "x" ]; then
      dir="`echo \"$dir\" | tr '[:upper:]' '[:lower:]'`"
    fi
    dir="`realpath --relative-to=\"$TOPDIR_FOR_RELATIVITY_CHECKS\" \"$dir\"`"
    case $dir in # Exclude generated sources to avoid module-info conflicts, see https://youtrack.jetbrains.com/issue/IDEA-185108
      "$SPEC_DIR"*) ;;
      *) SOURCE_DIRS="$SOURCE_DIRS<sourceFolder url=\"file://\$MODULE_DIR\$/$dir\" isTestSource=\"false\" /> "
    esac
  done
  add_replacement "###SOURCE_DIRS###" "$SOURCE_DIRS"
  DEPENDENCIES=""
  for dep in $moduleDependencies; do
    DEPENDENCIES="$DEPENDENCIES<orderEntry type=\"module\" module-name=\"$dep\" /> "
  done
  add_replacement "###DEPENDENCIES###" "$DEPENDENCIES"
  cp "$IDEA_OUTPUT/module.iml" "$IDEA_OUTPUT/$module.iml"
  IFS="$DEFAULT_IFS"
  replace_template_file "$IDEA_OUTPUT/$module.iml"
  )
done
)
rm "$IDEA_OUTPUT/module.iml"

### Create shell script runner for Windows

if [ "x$PATHTOOL" != "x" ]; then
  echo "@echo off" > "$IDEA_OUTPUT/bash.bat"
  if [ "x$WSL_DISTRO_NAME" != "x" ] ; then
    echo "wsl -d $WSL_DISTRO_NAME --cd \"%cd%\" -e %*" >> "$IDEA_OUTPUT/bash.bat"
  else
    echo "$WINENV_ROOT\bin\bash.exe -l -c \"cd %CD:\=/%/ && %*\"" >> "$IDEA_OUTPUT/bash.bat"
  fi
fi
