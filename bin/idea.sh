#!/bin/sh
#
# Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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
IDEA_MAKE="$MAKE_DIR/idea"
IDEA_TEMPLATE="$IDEA_MAKE/template"

cp -r "$IDEA_TEMPLATE"/* "$IDEA_OUTPUT"

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

# Expect MODULE_ROOTS, MODULE_NAMES, BOOT_JDK & SPEC to be set
if [ "x$MODULE_ROOTS" = "x" ] ; then
  echo "FATAL: MODULE_ROOTS is empty" >&2; exit 1
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
SPEC_DIR=`dirname $SPEC`
if [ "x$CYGPATH" = "x" ]; then
    add_replacement "###BUILD_DIR###" "$SPEC_DIR"
    add_replacement "###JTREG_HOME###" "$JT_HOME"
    add_replacement "###IMAGES_DIR###" "$SPEC_DIR/images/jdk"
    add_replacement "###ROOT_DIR###" "$TOPLEVEL_DIR"
    add_replacement "###IDEA_DIR###" "$IDEA_OUTPUT"
else
    add_replacement "###BUILD_DIR###" "`cygpath -am $SPEC_DIR`"
    add_replacement "###IMAGES_DIR###" "`cygpath -am $SPEC_DIR`/images/jdk"
    add_replacement "###ROOT_DIR###" "`cygpath -am $TOPLEVEL_DIR`"
    add_replacement "###IDEA_DIR###" "`cygpath -am $IDEA_OUTPUT`"
    if [ "x$JT_HOME" = "x" ]; then
      add_replacement "###JTREG_HOME###" ""
    else
      add_replacement "###JTREG_HOME###" "`cygpath -am $JT_HOME`"
    fi
fi

SOURCE_PREFIX="<sourceFolder url=\"file://"
SOURCE_POSTFIX="\" isTestSource=\"false\" />"

for root in $MODULE_ROOTS; do
    if [ "x$CYGPATH" != "x" ]; then
    	root=`cygpath -am $root`
    fi
    SOURCES=$SOURCES" $SOURCE_PREFIX""$root""$SOURCE_POSTFIX"
done

add_replacement "###SOURCE_ROOTS###" "$SOURCES"

replace_template_dir "$IDEA_OUTPUT"

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

if [ "x$CYGPATH" = "x" ] ; then ## CYGPATH may be set in env.cfg
  JAVAC_SOURCE_FILE=$IDEA_OUTPUT/src/idea/JdkIdeaAntLogger.java
  JAVAC_CLASSES=$CLASSES
  JAVAC_CP=$CP
else
  JAVAC_SOURCE_FILE=`cygpath -am $IDEA_OUTPUT/src/idea/JdkIdeaAntLogger.java`
  JAVAC_CLASSES=`cygpath -am $CLASSES`
  JAVAC_CP=`cygpath -am $CP`
fi

$BOOT_JDK/bin/javac -d $JAVAC_CLASSES -cp $JAVAC_CP $JAVAC_SOURCE_FILE
