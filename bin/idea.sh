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
if [ "$CUSTOM_IDEA_OUTPUT" = true ]; then
  if [ "x$PATHTOOL" != "x" ]; then
    add_replacement "###IDEA_DIR###" "`$PATHTOOL -am $IDEA_OUTPUT`"
  else
    add_replacement "###IDEA_DIR###" "$IDEA_OUTPUT"
  fi
else
  add_replacement "###IDEA_DIR###" "\$PROJECT_DIR\$/.idea"
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

### Compile the custom Logger

CLASSES=$IDEA_OUTPUT/classes

if [ "x$ANT_HOME" = "x" ] ; then
   # try some common locations, before giving up
   if [ -f "/usr/share/ant/lib/ant.jar" ] ; then
     ANT_HOME="/usr/share/ant"
   elif [ -f "/usr/local/Cellar/ant/1.9.4/libexec/lib/ant.jar" ] ; then
     ANT_HOME="/usr/local/Cellar/ant/1.9.4/libexec"
   else
     echo "FATAL: cannot find ant. Try setting ANT_HOME." >&2; exit 1
   fi
fi
CP=$ANT_HOME/lib/ant.jar
rm -rf $CLASSES; mkdir $CLASSES

if [ "x$WSL_DISTRO_NAME" != "x" ] ; then
  JAVAC_SOURCE_FILE=`realpath --relative-to=./ $IDEA_OUTPUT/src/idea/IdeaLoggerWrapper.java`
  JAVAC_SOURCE_PATH=`realpath --relative-to=./ $IDEA_OUTPUT/src`
  JAVAC_CLASSES=`realpath --relative-to=./ $CLASSES`
  ANT_TEMP=`mktemp -d -p ./`
  cp $ANT_HOME/lib/ant.jar $ANT_TEMP/ant.jar
  JAVAC_CP=$ANT_TEMP/ant.jar
  JAVAC=javac.exe
elif [ "x$PATHTOOL" != "x" ] ; then ## PATHTOOL may be set in env.cfg
  JAVAC_SOURCE_FILE=`$PATHTOOL -am $IDEA_OUTPUT/src/idea/IdeaLoggerWrapper.java`
  JAVAC_SOURCE_PATH=`$PATHTOOL -am $IDEA_OUTPUT/src`
  JAVAC_CLASSES=`$PATHTOOL -am $CLASSES`
  JAVAC_CP=`$PATHTOOL -am $CP`
  JAVAC=javac
else
  JAVAC_SOURCE_FILE=$IDEA_OUTPUT/src/idea/IdeaLoggerWrapper.java
  JAVAC_SOURCE_PATH=$IDEA_OUTPUT/src
  JAVAC_CLASSES=$CLASSES
  JAVAC_CP=$CP
  JAVAC=javac
fi

$BOOT_JDK/bin/$JAVAC -d $JAVAC_CLASSES -sourcepath $JAVAC_SOURCE_PATH -cp $JAVAC_CP $JAVAC_SOURCE_FILE

if [ "x$WSL_DISTRO_NAME" != "x" ]; then
  rm -rf $ANT_TEMP
fi