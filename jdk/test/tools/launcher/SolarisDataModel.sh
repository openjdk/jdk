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
# @bug 4780570
# @run shell SolarisDataModel.sh
# @summary Verify Solaris SPARC -d32 and -d64 options work with preset LD_LIBRARY_PATH
# @author Joseph D. Darcy

# Test to see if presetting LD_LIBRARY_PATH affects the treatment of
# -d32 and -d64 options; also checks that -d<n> options result in the
# desired data model.

# If the test is not being run on a Solaris SPARC box SPARC the test
# succeeds immediately.

OS=`uname -s`;

case "$OS" in
	SunOS )
		# ARCH should be sparc or i386
		ARCH=`uname -p`
		case "$ARCH" in 
			sparc)
			PATHSEP=":"
			;;

			* )
			echo "Non-SPARC Solaris environment; test vacuously succeeds."
			exit 0
		esac
	;;

	* )
	echo "Not a Solaris SPARC environment; test vacuously succeeds."
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


JAVAC="$TESTJAVA/bin/javac"

# Create our little Java tests on the fly
( printf "public class GetDataModel {"
  printf "   public static void main(String argv[]) {"
  printf "      System.out.println(System.getProperty(\"sun.arch.data.model\", \"none\"));"
  printf "   }"
  printf "}"
) > GetDataModel.java

$JAVAC  GetDataModel.java

( printf "public class GetLdLibraryPath {"
  printf "   public static void main(String argv[]) {"
  printf "      System.out.println(System.getProperty(\"java.library.path\"));"
  printf "   }"
  printf "}"
) > GetLdLibraryPath.java

$JAVAC  GetLdLibraryPath.java



# All preconditions are met; run the tests


# Construct path to 32-bit Java executable
JAVA="$TESTJAVA/bin/java -classpath $TESTCLASSES${PATHSEP}."


# Construct path to 64-bit Java executable, might not exist
JAVA64="$TESTJAVA/bin/sparcv9/java -classpath $TESTCLASSES${PATHSEP}."
JAVA64FILE="$TESTJAVA/bin/sparcv9/java"


# java -d32 tests

LD_LIBRARY_PATH=""
export LD_LIBRARY_PATH

DM=`$JAVA -d32 GetDataModel`
case "$DM" in
        32 )
        ;;

        * )
	echo "The combination \"java -d32\" failed."
	echo $DM
        exit 1
esac

# Rerun test with LD_LIBRARY_PATH preset
LD_LIBRARY_PATH=`$JAVA GetLdLibraryPath`;
DM=`$JAVA -d32 GetDataModel`
case "$DM" in
        32 )
        ;;

        * )
	echo "The combination \"java -d32\" failed with preset LD_LIBRARY_PATH."
	echo $DM
        exit 1
esac

# Reset LD_LIBRARY_PATH
LD_LIBRARY_PATH=


# Test for 64-bit executable

if [ -f $JAVA64FILE ]; then

	DM=`$JAVA -d64 GetDataModel`
	case "$DM" in
        	64 )
        	;;

        	* )
		echo "The combination \"java -d64\" failed."
        	exit 1
	esac

	DM=`$JAVA64 -d32 GetDataModel`
	case "$DM" in
        	32 )
        	;;

        	* )
		echo "The combination \"sparcv9/java -d32\" failed."
        	exit 1
	esac

	DM=`$JAVA64 -d64 GetDataModel`
	case "$DM" in
        	64 )
        	;;

        	* )
		echo "The combination \"sparcv9/java -d64\" failed."
        	exit 1
	esac

	# Rerun tests with LD_LIBRARY_PATH preset
	LD_LIBRARY_PATH=`$JAVA GetLdLibraryPath`;
	echo "Presetting LD_LIBRARY_PATH"

	DM=`$JAVA -d64 GetDataModel`
	case "$DM" in
        	64 )
        	;;

        	* )
		echo "The combination \"java -d64\" failed with preset LD_LIBRARY_PATH."
        	exit 1
	esac

	DM=`$JAVA64 -d32 GetDataModel`
	case "$DM" in
        	32 )
        	;;

        	* )
		echo "The combination \"sparcv9/java -d32\" failed with preset LD_LIBRARY_PATH."
        	exit 1
	esac

	DM=`$JAVA64 -d64 GetDataModel`
	case "$DM" in
        	64 )
        	;;

        	* )
		echo "The combination \"sparcv9/java -d64\" failed with preset LD_LIBRARY_PATH."
        	exit 1
	esac

else
  echo "Warning: no 64-bit components found; only java -d32 tests have been run."
fi
exit 0;
