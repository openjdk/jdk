# 
# Copyright (c) 2007 Sun Microsystems, Inc.  All Rights Reserved.
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
# @test
# @bug 4212439 5102289 6272156
# @summary Tests for expiration control and reloading expired resource bundles.
# @build ExpirationTest
# @run shell/timeout=300 ExpirationTest.sh

#
# Timings of the test sequence
#
# 0         5    7      10             20             40  [seconds]
# +---------+----+------+------//------+------//------+--
# g         X    g      X              U              g   [event]
#
#  0 g - java starts; the first getBundle call gets "German";
#        sleep for 7 sec
#  5 X - the bundle expires (every 5 seconds)
#  7 g - java wakes up; the second getBundle call still gets "German";
#        sleep for 33 sec
# 10 X - the bundle expires in the cache
# 20 U - shell script updates DE and add AT
# 40 g - java wakes up; third getBundle call; gets "Deutsch"
#
# event: g - getBundle, X - eXpire, U - Update
#
#
# ExpirationTest.java uses 3 exit values.
#  0 - passed
#  1 - failed
#  2 - can't proceed due to slow platform
#

# Check environment variables
if [ "x$TESTJAVA" = "x" ]; then
    1>&2 echo "No TESTJAVA defined. exiting..."
    exit 1
fi

# Make sure that this test is run in C locale
LANG=C
export LANG
LC_ALL=
export LC_ALL

: ${TESTCLASSES:=.}

# YES if the platform has %s support in date
HAS_S=NO

case "`uname`" in
Windows* | CYGWIN* )
    DEL=";"
    ;;
SunOS)
    DEL=":"
    ;;
Linux)
    DEL=":"
    HAS_S=YES
    ;;
esac

# Interval until resources are updated
INTERVAL=20

DATA=ExpirationData

ROOT=${DATA}.properties
JA=${DATA}_ja.properties
DE=${DATA}_de.properties
AT=${DATA}_de_AT.properties

JARFILE=data.jar

createProperties() {
    rm -f ${DATA}*.properties
    echo "data: English" > $ROOT
    (echo "data: Japanese"; echo "january: 1gatsu") > $JA
    (echo "data: German"; echo "january: Januar") > $DE
    echo "Properties files have been created at `date +%T`"
}

createJar() {
    if [ "$FORMAT" = "properties" ]; then
	createProperties
	F="${DATA}*.properties"
    else
	createClasses
	F="-C classes ${ROOT}.class -C classes ${JA}.class -C classes ${DE}.class"
    fi
    ${TESTJAVA}/bin/jar cf $JARFILE $F
    ${TESTJAVA}/bin/jar tvf $JARFILE
    rm -f ${DATA}*.properties
    echo "Jar created at `date +%T`"
}

createClasses() {
    rm -f ${DATA}*.java
    rm -rf classes
    mkdir classes
    createJava $ROOT English
    createJava $JA Japanese
    createJava $DE German Januar
    ${TESTJAVA}/bin/javac -d classes ${ROOT}.java ${JA}.java ${DE}.java
    echo "Created" classes/*.class "at `date +%T`"
}

createJava() {
    (echo "
import java.util.*;

public class $1 extends ListResourceBundle {
    public Object[][] getContents() {
	return new Object[][] {
	    { \"data\", \"$2\" },"
    if [ "x$3" != "x" ]; then
	echo "	    { \"january\", \"$3\" },"
    fi
echo "	};
    }
}") >$1.java
}

updateDEaddAT() {
    rm -f $DE
    (echo "data=Deutsch"; echo "january=Januar") > $DE
    # add de_AT
    echo "january=J\u00e4nner" > $AT
    echo "Updated '"${DE}"' and added '"${AT}"' at `date +%T`"
}

updateClassDEaddClassAT() {
    rm -f $DE.java classes/$DE.class
    createJava $DE Deutsch Januar
    ${TESTJAVA}/bin/javac -d classes ${DE}.java
    createJava $AT Deutsch "J\\u00e4nner"
    ${TESTJAVA}/bin/javac -d classes ${AT}.java
    echo "Updated '"${DE}"' class and added '"${AT}"' class at `date +%T`"
}

updateJar() {
    if [ "$FORMAT" = "properties" ]; then
	updateDEaddAT
	F="$DE $AT"
    else
	updateClassDEaddClassAT
	F="-C classes ${DE}.class -C classes ${AT}.class"
    fi
    ${TESTJAVA}/bin/jar uf $JARFILE $F
    rm -f $DE $AT
    echo "Updated '"${JARFILE}"' at `date +%T`"
    ${TESTJAVA}/bin/jar tvf $JARFILE
}

getSeconds() {
    if [ "$HAS_S" = "YES" ]; then
	date '+%s'
    else
	# Returns an approximation of the offset from the Epoch in
	# seconds.
	date -u '+%Y %j %H %M %S' | \
	awk '{d=($1-1970)*365.2425; print int(((((((d+$2-1)*24)+$3)*60)+$3)*60)+$5);}'
    fi
}

#
# Execute $1 and check how long it takes
#
timedExec() {
    S=`getSeconds`
    eval $1
    E=`getSeconds`
    D=`expr $E - $S`
    #
    # If this machine is too slow, give up the further testing.
    #
    if [ "$D" -gt $2 ]; then
	1>&2 echo "This machine took $D seconds to prepare test data," \
		  "which is too slow to proceed. Exiting..."
	exit 0
    fi
    unset S
    unset E
    unset D
}

checkStatus() {
    if [ $1 = 0 ]; then
	echo "$2: PASSED"
    elif [ $1 != 2 ]; then
	echo "$2: FAILED"
	exit 1
    else
	# Just we should't proceed to avoid timing issues.
	exit 0
    fi
}

#
# Before starting tests, check the latency with Thread.sleep().
#
${TESTJAVA}/bin/java -cp "${TESTCLASSES}${DEL}." ExpirationTest -latency
STATUS=$?
if [ $STATUS = 2 ]; then
    exit 0
fi

#
# Tests for properties
#
FORMAT=properties

#
# Test with plain files (properties)
#
echo "Starting test with properties files at `date +%T`"

#
# Creates properties files
#
timedExec createProperties 10

#
# Execute a child process which will update files in $INTERVAL seconds.
#
(sleep $INTERVAL; updateDEaddAT; exit 0) &

${TESTJAVA}/bin/java -cp "${TESTCLASSES}${DEL}." ExpirationTest properties file
STATUS=$?
wait
checkStatus $STATUS "Test with properties files"

#
# Test with jar file if jar is available (properties)
#
if [ -x ${TESTJAVA}/bin/jar ] || [ -x ${TESTJAVA}/bin/jar.exe ]; then
    HASJAR=YES
else
    HASJAR=NO
fi

if [ $HASJAR = YES  ]; then
    echo ""
    echo "Starting test with a jar file (properties) at `date +%T`"

    #
    # Create a jar files with properties
    #
    timedExec createJar 10

    (sleep $INTERVAL; updateJar; exit 0) &
    ${TESTJAVA}/bin/java -cp "${TESTCLASSES}${DEL}${JARFILE}" ExpirationTest properties jar
    STATUS=$?
    wait
    checkStatus $STATUS "Test with a jar file (properties)"
fi

#
# Test for classes
#

# Note: class-based resource bundles can't be reloaded due to the
# cache support in class loaders. So the results of the test cases
# below are not checked. (Test cases always pass.)

# If there's no javac available, then give up the test with
# class-based properties.
if [ ! -x ${TESTJAVA}/bin/javac ] && [ ! -x ${TESTJAVA}/bin/javac.exe ]; then
    exit 0
fi

rm -f ${DATA}*.properties $JARFILE

FORMAT=class
ROOT=`basename $ROOT .properties`
JA=`basename $JA .properties`
DE=`basename $DE .properties`
AT=`basename $AT .properties`

echo ""
echo "Starting test with class files at `date +%T`"

#
# Create class files
#
timedExec createClasses 10

(sleep $INTERVAL; updateClassDEaddClassAT; exit 0) &
${TESTJAVA}/bin/java -cp "${TESTCLASSES}${DEL}classes" ExpirationTest class file
STATUS=$?
wait
checkStatus $STATUS "Test with class files"

if [ $HASJAR = YES ]; then
    echo ""
    echo "Starting test with a jar file (class) at `date +%T`"

    #
    # Create a jar file with class files
    #
    timedExec createJar 10

    (sleep $INTERVAL; updateJar; exit 0) &
    ${TESTJAVA}/bin/java -cp "${TESTCLASSES}${DEL}${JARFILE}" ExpirationTest class jar
    STATUS=$?
    wait
    checkStatus $STATUS "Test with a jar file (class)"
fi

exit 0
