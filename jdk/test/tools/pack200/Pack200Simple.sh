#
# Copyright (c) 2003, 2007, Oracle and/or its affiliates. All rights reserved.
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

# @test Pack200Simple.sh
# @bug  6521334
# @build Pack200Test
# @run shell/timeout=1200 Pack200Simple.sh
# @summary An ad hoc test to verify class-file format.
# @author Kumar Srinivasan

# The goal of this test is to assist javac or other developers 
# who modify class file formats, to quickly test those modifications
# without having to build the install workspace. However it must
# be noted that building the install workspace is the only know
# way to prevent build breakages.

# Pack200 developers could use this  as a basic smoke-test, however 
# please note, there are other more elaborate and  thorough tests for 
# this very purpose. 

# We try a potpouri of things ie. we have pack.conf to setup some 
# options as well as a couple of command line options. We also test
# the packing and unpacking mechanism using the Java APIs.

# print error and exit with a message 
errorOut() {
  if [ "x$1" = "x" ]; then
    printf "Error: Unknown error\n"
  else
    printf "Error: %s\n" "$1"
  fi

  exit 1
}

# Verify directory context variables are set
if [ "${TESTJAVA}" = "" ]; then
  errorOut "TESTJAVA not set.  Test cannot execute.  Failed."
fi

if [ "${TESTSRC}" = "" ]; then
  errorOut "TESTSRC not set.  Test cannot execute.  Failed."
fi


if [ "${TESTCLASSES}" = "" ]; then
  errorOut "TESTCLASSES not set.  Test cannot execute.  Failed."
fi

# The common java utils we need
PACK200=${TESTJAVA}/bin/pack200
UNPACK200=${TESTJAVA}/bin/unpack200
JAR=${TESTJAVA}/bin/jar

# For Windows and Linux needs the heap to be set, for others ergonomics
# will do the rest. It is important to use ea, which can expose class
# format errors much earlier than later.

OS=`uname -s`


case "$OS" in
  Windows*|CYGWIN* )
    PackOptions="-J-Xmx512m -J-ea"
    break
    ;;

  Linux )
    PackOptions="-J-Xmx512m -J-ea"
    break
    ;;

  * )
    PackOptions="-J-ea"
    ;;
esac

# Creates a packfile of choice expects 1 argument the filename
createConfigFile() {
 # optimize for speed
 printf "pack.effort=1\n" > $1
 # we DO want to know about new attributes
 printf "pack.unknown.attribute=error\n" >> $1
 # optimize for speed
 printf "pack.deflate.hint=false\n" >> $1
 # keep the ordering for easy compare
 printf "pack.keep.class.order=true\n" >> $1
}


# Tests a given jar, expects 1 argument the fully qualified
# name to a test jar, it writes all output to the current
# directory which is a scratch  area.
testAJar() {
  PackConf="pack.conf"
  createConfigFile $PackConf

  # Try some command line options
  CLIPackOptions="$PackOptions -v --no-gzip --segment-limit=10000 --config-file=$PackConf"

  jfName=`basename $1`

  ${PACK200}   $CLIPackOptions  ${jfName}.pack $1 > ${jfName}.pack.log 2>&1
  if [ $? != 0 ]; then
    errorOut "$jfName packing failed"
  fi

  # We want to test unpack200, therefore we dont use -r with pack
  ${UNPACK200} -v ${jfName}.pack $jfName > ${jfName}.unpack.log 2>&1
  if [ $? != 0 ]; then
    errorOut "$jfName unpacking failed"
  fi

  # A quick crc compare test to ensure a well formed zip
  # archive, this is a critical unpack200 behaviour.

  unzip -t $jfName > ${jfName}.unzip.log 2>&1 
  if [ $? != 0 ]; then
    errorOut "$jfName unzip -t test failed"
  fi

  # The PACK200 signature should be at the top of the log
  # this tag is critical for deployment related tools.

  head -5 ${jfName}.unzip.log | grep PACK200 > /dev/null 2>&1
  if [ $? != 0 ]; then
    errorOut "$jfName PACK200 signature missing"
  fi


  # we know  the size fields don't match, strip 'em out, its
  # extremely important to ensure that the date stamps match up.
  # Don't EVER sort the output we are checking for correct ordering.

  ${JAR} -tvf $1 | sed -e 's/^ *[0-9]* //g'> ${jfName}.ref.txt
  ${JAR} -tvf $jfName | sed -e 's/^ *[0-9]* //g'> ${jfName}.cmp.txt

  diff ${jfName}.ref.txt ${jfName}.cmp.txt > ${jfName}.diff.log 2>&1
  if [ $? != 0 ]; then
    errorOut "$jfName files missing"
  fi
}

# These JARs are the largest and also the most likely specimens to 
# expose class format issues and stress the packer as well.

JLIST="${TESTJAVA}/lib/tools.jar ${TESTJAVA}/jre/lib/rt.jar"


# Test the Command Line Interfaces (CLI).
mkdir cliTestDir 
_pwd=`pwd`
cd cliTestDir

for jarfile in $JLIST ; do 
  if [ -f $jarfile ]; then
    testAJar $jarfile
  else
    errorOut "Error: '$jarFile' does not exist\nTest requires a j2sdk-image\n"
  fi
done
cd $_pwd

# Test the Java APIs.
mkdir apiTestDir 
_pwd=`pwd`
cd  apiTestDir

# Strip out the -J prefixes.
JavaPackOptions=`printf %s "$PackOptions" | sed -e 's/-J//g'`

# Test the Java APIs now.
$TESTJAVA/bin/java $JavaPackOptions -cp $TESTCLASSES Pack200Test $JLIST || exit 1

cd $_pwd

exit 0
