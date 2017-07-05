# Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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


# @test
# @bug 4731671
# @build libraryCaller
# @run shell SolarisRunpath.sh
# @summary Verify that Solaris LD_LIBRARY_PATH rules are followed
# @author Joseph D. Darcy

# The launcher has been updated to properly take account of Solaris
# LD_LIBRARY_PATH rules when constructing the runpath for the Java
# executable.  That is, data model dependent LD_LIBRARY_PATH variables
# are tested for and override LD_LIBRARY_PATH if present.  The current
# launcher design relies on LD_LIBRARY_PATH settings to ensure the
# proper jre/jdk libraries are opening during program execution.  In
# the future, this dependence might be removed by having the vm
# explicitly dlopen the needed files.  If that change occurs, this
# test will be harmless but no long relevant.

# A more robust test for Solaris SPARC would set the different
# LD_LIBRARY_PATH variables while also varying the -d[32|64] options
# to make sure the LD_LIBRARY_PATH of the *target* data model were
# being respected.  That is "java -d64" should use the 64-bit
# LD_LIBRARY_PATH while "java -d32" should use the 32-bit
# LD_LIBRARY_PATH regardless of the data model of the "java" binary.
# However, by default builds do not contain both 32 and 64 bit
# components so such a test would often not be applicable.


# If the test is not being run on a Solaris box, SPARC or x86, the
# test succeeds immediately.

OS=`uname -s`;

case "$OS" in
	SunOS )
	PATHSEP=":"
	;;

	* )
	echo "Not a Solaris environment; test vacuously succeeds."
	exit 0;
	;;
esac

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

# Construct paths to default Java executables
JAVAC="$TESTJAVA/bin/javac"


# Create our little Java test on the fly
( printf "public class GetDataModel {"
  printf "   public static void main(String argv[]) {"
  printf "      System.out.println(System.getProperty(\"sun.arch.data.model\", \"none\"));"
  printf "   }"
  printf "}"
) > GetDataModel.java

$JAVAC GetDataModel.java


# ARCH should be sparc or i386
ARCH=`uname -p`
case "$ARCH" in 
	sparc | i386 )
	;;

	* )
	echo "Unrecognized architecture; test fails."
	exit 1
esac

# The following construction may not work as desired in a
# 64-bit build.
JAVA="$TESTJAVA/bin/java -classpath $TESTCLASSES${PATHSEP}."

# Determine data model
DM=`$JAVA GetDataModel`

# verify DM is 32 or 64
case "$DM" in 
	32 )
	ODM=64;
	;;
	
	64 )
	ODM=32;
	;;

	* )
	echo "Unknown data model \"$DM\"; test fails."
	exit 1
esac

# -------------------- Test 1 --------------------

LD_LIBRARY_PATH=$TESTSRC/lib/$ARCH/lib$DM
export LD_LIBRARY_PATH
unset LD_LIBRARY_PATH_32
unset LD_LIBRARY_PATH_64
	
# With plain LD_LIBRARY_PATH, result should always be 0
RESULT=`$JAVA libraryCaller`
if [ "${RESULT}" != "0" ]; 
then
	echo "Not using LD_LIBRARY_PATH; test fails."
	exit 1
fi

# The following two tests sets both data model dependent
# LD_LIBRARY_PATH variables individually.

# -------------------- Test 2 --------------------

# Set opposite data model variable; should return same result
# as plain LD_LIBRARY_PATH.

if [ "${DM}" = "32"  ]; then
	LD_LIBRARY_PATH_64=$TESTSRC/lib/$ARCH/lib$DM/lib$DM
	export LD_LIBRARY_PATH_64
else
	LD_LIBRARY_PATH_32=$TESTSRC/lib/$ARCH/lib$DM/lib$DM
	export LD_LIBRARY_PATH_32
fi

RESULT=`$JAVA libraryCaller`
if [ "${RESULT}" != "0" ]; 
then
	echo "Using LD_LIBRARY_PATH_$ODM for $DM binary;"
	echo "test fails."
	exit 1
fi

unset LD_LIBRARY_PATH_32
unset LD_LIBRARY_PATH_64

# -------------------- Test 3 --------------------

# Set appropriate data model variable; result should match
# data model.
if [ "${DM}" = "32"  ]; then
	LD_LIBRARY_PATH_32=$TESTSRC/lib/$ARCH/lib$DM/lib$DM
	export LD_LIBRARY_PATH_32
else
	LD_LIBRARY_PATH_64=$TESTSRC/lib/$ARCH/lib$DM/lib$DM
	export LD_LIBRARY_PATH_64
fi

RESULT=`$JAVA libraryCaller`
if [ "${RESULT}" != "$DM" ]; 
then
	echo "Data model dependent LD_LIBRARY_PATH_$DM"
	echo "not overriding LD_LIBRARY_PATH; test fails."
	exit 1
fi

unset LD_LIBRARY_PATH
unset LD_LIBRARY_PATH_32
unset LD_LIBRARY_PATH_64

# -------------------- Test 4 --------------------

# Have only data model dependent LD_LIBRARY_PATH set; result
# should match data model.

if [ "${DM}" = "32"  ]; then
	LD_LIBRARY_PATH_32=$TESTSRC/lib/$ARCH/lib$DM/lib$DM
	export LD_LIBRARY_PATH_32
else
	LD_LIBRARY_PATH_64=$TESTSRC/lib/$ARCH/lib$DM/lib$DM
	export LD_LIBRARY_PATH_64
fi

RESULT=`$JAVA libraryCaller`
if [ "${RESULT}" != "$DM" ]; 
then
	echo "Not using data-model dependent LD_LIBRARY_PATH; test fails."
	exit 1
fi

# All tests have passed
exit 0
