#
# Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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

# @test
# @bug 4894330 4810347 6277269
# @run shell ChangeDataModel.sh
# @summary Verify -d32 and -d64 options are accepted(rejected) on all platforms 
# @author Joseph D. Darcy

OS=`uname -s`;

# To remove CR from output, needed for java apps in CYGWIN, harmless otherwise
SED_CR="sed -e s@\\r@@g"

case "$OS" in
	Windows* | CYGWIN* )
	  PATHSEP=";"
	  ;;

	* )
	  PATHSEP=":"
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
JAVA="$TESTJAVA/bin/java -classpath $TESTCLASSES${PATHSEP}."
JAVAC="$TESTJAVA/bin/javac"


# Create our little Java test on the fly
( printf "public class GetDataModel {"
  printf "   public static void main(String argv[]) {"
  printf "      System.out.println(System.getProperty(\"sun.arch.data.model\", \"none\"));"
  printf "   }"
  printf "}"
) > GetDataModel.java

$JAVAC GetDataModel.java


# All preconditions are met; run the tests.


# Verify data model flag for default data model is accepted

DM=`$JAVA GetDataModel | ${SED_CR}`
case "$DM" in
        32 )
		DM2=`${JAVA} -d32 GetDataModel | ${SED_CR}`	
		if [ "${DM2}" != "32" ]
		then
	  	echo "Data model flag -d32 not accepted or had improper effect."
	  	exit 1
		fi
        ;;

        64 )
		DM2=`${JAVA} -d64 GetDataModel | ${SED_CR}`	
		if [ "${DM2}" != "64" ]
		then
	  	echo "Data model flag -d64 not accepted or had improper effect."
	  	exit 1
		fi
	;;

	* )
		echo "Unrecognized data model: $DM"
        	exit 1
	;;
esac

# Determine if platform might be dual-mode capable.

case "$OS" in
	SunOS )
		# ARCH should be sparc or i386
		ARCH=`uname -p`
		case "${ARCH}" in 
			sparc )
			DUALMODE=true
			PATH64=sparcv9
			;;

			i386 )
			DUALMODE=true
			PATH64=amd64
			;;

			* )
			DUALMODE=false
			;;
		esac
	;;


	Linux )
		# ARCH should be ia64, x86_64, or i*86
		ARCH=`uname -m`
		case "${ARCH}" in 
			ia64 )
			DUALMODE=false
			;;

			x86_64 )
			DUALMODE=true
			PATH64=amd64
			;;

			* )
			DUALMODE=false;
			;;
		esac
	;;

	Windows* | CYGWIN* )
		ARCH=`uname -m`
		case "${ARCH}" in 
			* )
			DUALMODE=false;
			;;
		esac
	;;

	* )
		echo "Warning: unknown environment."
		DUALMODE=false
	;;
esac

if [ "${DUALMODE}" = "true" ]
then
	# Construct path to 64-bit Java executable, might not exist
	JAVA64FILE="${TESTJAVA}/bin/${PATH64}/java"
	JAVA64="${JAVA64FILE} -classpath ${TESTCLASSES}${PATHSEP}."

	if [ -f ${JAVA64FILE} ]; then
		# Verify that, at least on Solaris, only one exec is
		# used to change data models
		if [ "${OS}" = "SunOS" ]
		then
			rm -f truss.out
			truss -texec ${JAVA} -d64 GetDataModel > /dev/null 2> truss.out
			execCount=`grep -c execve truss.out`
			if [ "${execCount}" -gt 2 ]
			then
				echo "Maximum exec count of 2 exceeded: got $execCount."
				exit 1
			fi

			rm -f truss.out
			truss -texec ${JAVA64} -d32 GetDataModel > /dev/null 2> truss.out
			execCount=`grep -c execve truss.out`
			if [ "${execCount}" -gt 2 ]
			then
				echo "Maximum exec count of 2 exceeded: got $execCount."
				exit 1
			fi
		fi

		DM2=`${JAVA} -d64 GetDataModel`
		if [ "${DM2}" != "64" ]
		then
		  echo "Data model flag -d64 not accepted or had improper effect."
		  exit 1
		fi

		DM2=`${JAVA64} GetDataModel`
		if [ "${DM2}" != "64" ]
		then
		  echo "Improper data model returned."
		  exit 1
		fi

		DM2=`${JAVA64} -d64 GetDataModel`
		if [ "${DM2}" != "64" ]
		then
		  echo "Data model flag -d64 not accepted or had improper effect."
		  exit 1
		fi

		DM2=`${JAVA64} -d32 GetDataModel`
		if [ "${DM2}" != "32" ]
		then
		  echo "Data model flag -d32 not accepted or had improper effect."
		  exit 1
		fi

	else
	  echo "Warning: no 64-bit components found; only one data model tested."
	fi
else
# Negative tests for non-dual mode platforms to ensure the other data model is 
# rejected
	DM=`$JAVA GetDataModel | ${SED_CR}`
	case "$DM" in
	   32 )
		DM2=`${JAVA} -d64 GetDataModel | ${SED_CR}`	
		if [ "x${DM2}" != "x" ]
		then
		   echo "Data model flag -d64 was accepted."
		   exit 1
		fi
           ;;

	   64 )
		DM2=`${JAVA} -d32 GetDataModel | ${SED_CR}`	
		if [ "x${DM2}" != "x" ]
		  then
		    echo "Data model flag -d32 was accepted."
	  	    exit 1
		fi
	   ;;

	   * )
		echo "Unrecognized data model: $DM"
        	exit 1
	   ;;
        esac
fi

exit 0;
