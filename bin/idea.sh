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
      IDEA_OUTPUT=$2
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

mkdir $IDEA_OUTPUT || exit 1
cd $IDEA_OUTPUT; IDEA_OUTPUT=`pwd`

MAKE_DIR="$SCRIPT_DIR/../make"
IDEA_MAKE="$MAKE_DIR/idea"
IDEA_TEMPLATE="$IDEA_MAKE/template"

cp -r "$IDEA_TEMPLATE"/* "$IDEA_OUTPUT"

#init template variables
for file in `ls -p $IDEA_TEMPLATE | grep -v /`; do
	VAR_SUFFIX=`echo $file | cut -d'.' -f1 | tr [:lower:] [:upper:]`
    eval "$VAR_SUFFIX"_TEMPLATE="$IDEA_TEMPLATE"/$file
	eval IDEA_"$VAR_SUFFIX"="$IDEA_OUTPUT"/$file
done

#override template variables
if [ -d "$TEMPLATES_OVERRIDE" ] ; then
    for file in `ls -p "$TEMPLATES_OVERRIDE" | grep -v /`; do
        cp "$TEMPLATES_OVERRIDE"/$file "$IDEA_OUTPUT"/
    	VAR_SUFFIX=`echo $file | cut -d'.' -f1 | tr [:lower:] [:upper:]`
        eval "$VAR_SUFFIX"_TEMPLATE="$TEMPLATES_OVERRIDE"/$file
    done
fi

if [ "$VERBOSE" = "true" ] ; then
  echo "output dir: $IDEA_OUTPUT"
  echo "idea template dir: $IDEA_TEMPLATE"
fi

if [ ! -f "$JDK_TEMPLATE" ] ; then
  echo "FATAL: cannot find $JDK_TEMPLATE" >&2; exit 1
fi

if [ ! -f "$ANT_TEMPLATE" ] ; then
  echo "FATAL: cannot find $ANT_TEMPLATE" >&2; exit 1
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

SOURCE_FOLDER="      <sourceFolder url=\"file://\$MODULE_DIR\$/####\" isTestSource=\"false\" />"
SOURCE_FOLDERS_DONE="false"

addSourceFolder() {
  root=$@
  relativePath="`echo "$root" | sed -e s@"$TOP/\(.*$\)"@"\1"@`"
  folder="`echo "$SOURCE_FOLDER" | sed -e s@"\(.*/\)####\(.*\)"@"\1$relativePath\2"@`"
  printf "%s\n" "$folder" >> $IDEA_JDK
}

### Generate project iml

rm -f $IDEA_JDK
while IFS= read -r line
do
  if echo "$line" | egrep "^ .* <sourceFolder.*####" > /dev/null ; then
    if [ "$SOURCE_FOLDERS_DONE" = "false" ] ; then
      SOURCE_FOLDERS_DONE="true"
      for root in $MODULE_ROOTS; do
         addSourceFolder $root
      done
    fi
  else
    printf "%s\n" "$line" >> $IDEA_JDK
  fi
done < "$JDK_TEMPLATE"


MODULE_NAME="        <property name=\"module.name\" value=\"####\" />"

addModuleName() {
  mn="`echo "$MODULE_NAME" | sed -e s@"\(.*\)####\(.*\)"@"\1$MODULE_NAMES\2"@`"
  printf "%s\n" "$mn" >> $IDEA_ANT
}

BUILD_DIR="        <property name=\"build.target.dir\" value=\"####\" />"

addBuildDir() {
  DIR=`dirname $SPEC`
  mn="`echo "$BUILD_DIR" | sed -e s@"\(.*\)####\(.*\)"@"\1$DIR\2"@`"
  printf "%s\n" "$mn" >> $IDEA_ANT
}

### Generate ant.xml

rm -f $IDEA_ANT
while IFS= read -r line
do
  if echo "$line" | egrep "^ .* <property name=\"module.name\"" > /dev/null ; then
    addModuleName
  elif echo "$line" | egrep "^ .* <property name=\"build.target.dir\"" > /dev/null ; then
    addBuildDir
  else
    printf "%s\n" "$line" >> $IDEA_ANT
  fi
done < "$ANT_TEMPLATE"

### Generate misc.xml

rm -f $IDEA_MISC

JTREG_HOME="    <path>####</path>"

IMAGES_DIR="    <jre alt=\"true\" value=\"####\" />"

addImagesDir() {
  DIR=`dirname $SPEC`/images/jdk
  mn="`echo "$IMAGES_DIR" | sed -e s@"\(.*\)####\(.*\)"@"\1$DIR\2"@`"
  printf "%s\n" "$mn" >> $IDEA_MISC
}

addJtregHome() {
  DIR=`dirname $SPEC`
  mn="`echo "$JTREG_HOME" | sed -e s@"\(.*\)####\(.*\)"@"\1$JT_HOME\2"@`"
  printf "%s\n" "$mn" >> $IDEA_MISC
}

rm -f $MISC_ANT
while IFS= read -r line
do
  if echo "$line" | egrep "^ .*<path>jtreg_home</path>" > /dev/null ; then
	addJtregHome
  elif echo "$line" | egrep "^ .*<jre alt=\"true\" value=\"images_jdk\"" > /dev/null ; then
    addImagesDir
  else
    printf "%s\n" "$line" >> $IDEA_MISC
  fi
done < "$MISC_TEMPLATE"

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
