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
      echo "Usage: $0 [-h|--help] [-q|--quiet] [-a|--absolute-paths] [-o|--output <path>] [modules...]"
      echo "    -h | --help"
      echo "    -q | --quiet
        No stdout output"
      echo "    -a | --absolute-paths
        Use absolute paths to this jdk, so that generated .idea
        project files can be moved independently of jdk sources"
      echo "    -o | --output <path>
        Where .idea directory with project files will be generated
        (e.g. using '-o .' will place project files in './.idea')
        Default: $TOPLEVEL_DIR"
      echo "    [modules...]
        Generate project modules for specific java modules
        (e.g. 'java.base java.desktop')
        Default: all existing modules (java.* and jdk.*)"
      exit 1
}

SCRIPT_DIR=`dirname $0`
#assume TOP is the dir from which the script has been called
TOP=`pwd`
cd $SCRIPT_DIR; SCRIPT_DIR=`pwd`
cd .. ; TOPLEVEL_DIR=`pwd`
cd $TOP;

IDEA_OUTPUT=$TOPLEVEL_DIR/.idea
VERBOSE=true
ABSOLUTE_PATHS=false
while [ $# -gt 0 ]
do
  case $1 in
    -h | --help )
      usage
      ;;

    -q | --quiet )
      VERBOSE=false
      ;;

    -a | --absolute-paths )
      ABSOLUTE_PATHS=true
      ;;

    -o | --output )
      IDEA_OUTPUT=$2/.idea
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
cd ..; IDEA_OUTPUT_PARENT=`pwd`

MAKE_DIR="$TOPLEVEL_DIR/make"
IDEA_MAKE="$MAKE_DIR/ide/idea/jdk"
IDEA_TEMPLATE="$IDEA_MAKE/template"

cp -rn "$IDEA_TEMPLATE"/* "$IDEA_OUTPUT"

#override template
if [ -d "$TEMPLATES_OVERRIDE" ] ; then
    for file in `ls -p "$TEMPLATES_OVERRIDE" | grep -v /`; do
        cp "$TEMPLATES_OVERRIDE"/$file "$IDEA_OUTPUT"/
    done
fi

if [ "$VERBOSE" = true ] ; then
  echo "Will generate IDEA project files in \"$IDEA_OUTPUT\" for project \"$TOPLEVEL_DIR\""
fi

cd $TOP ; make -f "$IDEA_MAKE/idea.gmk" -I "$TOPLEVEL_DIR" idea \
    MAKEOVERRIDES= IDEA_OUTPUT_PARENT="$IDEA_OUTPUT_PARENT" OUT="$IDEA_OUTPUT/env.cfg" MODULES="$*" || exit 1
cd $SCRIPT_DIR

. $IDEA_OUTPUT/env.cfg

# Expect MODULES, MODULE_NAMES, RELATIVE_PROJECT_DIR, RELATIVE_BUILD_DIR to be set
if [ "xMODULES" = "x" ] ; then
  echo "FATAL: MODULES is empty" >&2; exit 1
fi

if [ "x$MODULE_NAMES" = "x" ] ; then
  echo "FATAL: MODULE_NAMES is empty" >&2; exit 1
fi

if [ "x$RELATIVE_PROJECT_DIR" = "x" ] ; then
  echo "FATAL: RELATIVE_PROJECT_DIR is empty" >&2; exit 1
fi

if [ "x$RELATIVE_BUILD_DIR" = "x" ] ; then
  echo "FATAL: RELATIVE_BUILD_DIR is empty" >&2; exit 1
fi

if [ -d "$TOPLEVEL_DIR/.hg" ] ; then
    VCS_TYPE="hg4idea"
fi

if [ -d "$TOPLEVEL_DIR/.git" ] ; then
    VCS_TYPE="Git"
fi

if [ "$ABSOLUTE_PATHS" = true ] ; then
  if [ "x$PATHTOOL" != "x" ]; then
    PROJECT_DIR="`$PATHTOOL -am $TOPLEVEL_DIR`"
  else
    PROJECT_DIR="$TOPLEVEL_DIR"
  fi
  MODULE_DIR="$PROJECT_DIR"
  cd "$TOPLEVEL_DIR" && cd "$RELATIVE_BUILD_DIR" && BUILD_DIR="`pwd`"
else
  if [ "$RELATIVE_PROJECT_DIR" = "." ] ; then
    PROJECT_DIR=""
  else
    PROJECT_DIR="/$RELATIVE_PROJECT_DIR"
  fi
  MODULE_DIR="\$MODULE_DIR\$$PROJECT_DIR"
  PROJECT_DIR="\$PROJECT_DIR\$$PROJECT_DIR"
  BUILD_DIR="\$PROJECT_DIR\$/$RELATIVE_BUILD_DIR"
fi
if [ "$VERBOSE" = true ] ; then
  echo "Project root: $PROJECT_DIR"
  echo "Generating IDEA project files..."
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

add_replacement "###PROJECT_DIR###" "$PROJECT_DIR"
add_replacement "###MODULE_DIR###" "$MODULE_DIR"
add_replacement "###MODULE_NAMES###" "$MODULE_NAMES"
add_replacement "###VCS_TYPE###" "$VCS_TYPE"
add_replacement "###BUILD_DIR###" "$BUILD_DIR"
if [ "x$PATHTOOL" != "x" ]; then
  add_replacement "###BASH_RUNNER_PREFIX###" "\$PROJECT_DIR\$/.idea/bash.bat"
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

if [ "$VERBOSE" = true ] ; then
    echo "Generating project modules:"
  fi
(
DEFAULT_IFS="$IFS"
IFS='#'
for value in $MODULES; do
  (
  eval "$value"
  if [ "$VERBOSE" = true ] ; then
    echo "    $module"
  fi
  add_replacement "###MODULE_CONTENT###" "src/$module"
  SOURCE_DIRS=""
  IFS=' '
  for dir in $moduleSrcDirs; do
    case $dir in # Exclude generated sources to avoid module-info conflicts, see https://youtrack.jetbrains.com/issue/IDEA-185108
      "src/"*) SOURCE_DIRS="$SOURCE_DIRS<sourceFolder url=\"file://$MODULE_DIR/$dir\" isTestSource=\"false\" /> "
    esac
  done
  add_replacement "###SOURCE_DIRS###" "$SOURCE_DIRS"
  DEPENDENCIES=""
  for dep in $moduleDependencies; do
    case $MODULE_NAMES in # Exclude skipped modules from dependencies
      *"$dep"*) DEPENDENCIES="$DEPENDENCIES<orderEntry type=\"module\" module-name=\"$dep\" /> "
    esac
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
