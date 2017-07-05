#!/bin/sh
# @test Arrrghs.sh
# @bug 5030233 6214916 6356475 6571029 6684582
# @build Arrrghs
# @run shell Arrrghs.sh
# @summary Argument parsing validation.
# @author Joseph E. Kowalski

#
# Copyright 2004-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.
#

#
# This test is intended to validate generic argument parsing and
# handling.
#
# Oh yes, since the response to argument parsing errors is often
# a visceral one, the name Arrrghs (pronounced "args") seems rather
# appropriate.
#

# Verify directory context variables are set
if [ "${TESTJAVA}" = "" ]
then
  echo "TESTJAVA not set.  Test cannot execute.  Failed."
  exit 1
fi

if [ "${TESTSRC}" = "" ]
then
  echo "TESTSRC not set.  Test cannot execute.  Failed."
  exit 1
fi

if [ "${TESTCLASSES}" = "" ]
then
  echo "TESTCLASSES not set.  Test cannot execute.  Failed."
  exit 1
fi

#
# Shell routine to test for the proper handling of the cp/classpath 
# option is correct (see 5030233).  This option is unique in that it
# is the only option to the java command (and friends) which is
# separated from its option argument by a space, rather than an
# equals sign.
#
# Parameters:
#	$1	cmd	utility name to be tested (java, javac, ...)
#	$2	option	either the -cp or -classpath option to be
#			tested.
#
TestCP() {
	mess="`$TESTJAVA/bin/$1 $2 2>&1 1>/dev/null`"
	if [ $? -eq 0 ]; then
		echo "Invalid $1 $2 syntax accepted"
		exit 1
	fi
	if [ -z "$mess" ]; then
		echo "No Usage message from invalid $1 $2 syntax"
		exit 1
	fi
}

#
# Test for 6356475 "REGRESSION:"java -X" from cmdline fails"
#
TestXUsage() {
	$TESTJAVA/bin/java -X > /dev/null 2>&1
	if [ $? -ne 0 ]; then
		echo "-X option failed"
		exit 1
	fi
}

#
# Test if java -help works
#
TestHelp() {
	$TESTJAVA/bin/java -help > /dev/null 2>&1
	if [ $? -ne 0 ]; then
		echo "-help option failed"
		exit 1
	fi
}

#
# Test to ensure that a missing main class is indicated in the error message
#
TestMissingMainClass() {
	# First create a small jar file with no main
        printf "public class Foo {}\n" > Foo.java
	$TESTJAVA/bin/javac Foo.java
	if [ $? -ne 0 ]; then
		printf "Error: compilation of Foo.java failed\n" 
 		exit 1
	fi
	printf "Main-Class: Bar\n" > manifest
	$TESTJAVA/bin/jar -cvfm some.jar manifest Foo.class
	if [ ! -f some.jar ]; then
		printf "Error: did not find some.jar\n" 
 		exit 1
	fi

	# test a non-existence main-class using -jar 
	mess="`$TESTJAVA/bin/java -jar some.jar 2>&1 1>/dev/null`"
	echo $mess | grep 'Bar' 2>&1 > /dev/null
	if [ $? -ne 0 ]; then
		printf "Error: did not find main class missing message\n"
		exit 1
	fi

	# test a non-existent main-class using classpath
	mess="`$TESTJAVA/bin/java -cp some.jar Bar 2>&1 1>/dev/null`"
	echo $mess | grep 'Bar' 2>&1 > /dev/null
	if [ $? -ne 0 ]; then
		printf "Error: did not find main class missing message\n"
		exit 1
	fi

	# cleanup
	rm -f some.jar Foo.* manifest
}

#
# Main processing:
#

#
# Tests for 5030233
#
TestCP java -cp
TestCP java -classpath
TestCP java -jar
TestCP javac -cp
TestCP javac -classpath
TestXUsage
TestHelp
TestMissingMainClass

#
# Tests for 6214916
#
#
# These tests require that a JVM (any JVM) be installed in the system registry.
# If none is installed, skip this test.
$TESTJAVA/bin/java -version:1.1+ -version >/dev/null 2>&1
if [ $? -eq 0 ]; then
   $TESTJAVA/bin/java -classpath $TESTCLASSES Arrrghs $TESTJAVA/bin/java
   if [ $? -ne 0 ]; then
      echo "Argument Passing Tests failed"
      exit 1
   fi
else
   printf "Warning:Argument Passing Tests were skipped, no java found in system registry."
fi
exit 0
