# @test MultipleJRE.sh
# @bug 4811102 4953711 4955505 4956301 4991229 4998210 5018605 6387069 6733959
# @build PrintVersion
# @build UglyPrintVersion
# @build ZipMeUp
# @run shell MultipleJRE.sh
# @summary Verify Multiple JRE version support
# @author Joseph E. Kowalski

#
# Copyright (c) 2003, 2008, Oracle and/or its affiliates. All rights reserved.
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

JAVAEXE="$TESTJAVA/bin/java"
JAVA="$TESTJAVA/bin/java -classpath $TESTCLASSES"
JAR="$TESTJAVA/bin/jar"
OS=`uname -s`;

#
# Tests whether we are on windows (true) or not.
#
IsWindows() {
    case "$OS" in
        Windows* | CYGWIN* )
            printf "true"
	;;
	* )
            printf "false"
	;;
    esac
}

#
# Shell routine to test for the proper rejection of syntactically incorrect
# version specifications.
#
TestSyntax() {
	mess="`$JAVA -version:\"$1\" -version 2>&1`"
	if [ $? -eq 0 ]; then
		echo "Invalid version syntax $1 accepted"
		exit 1
	fi
	prefix="`echo "$mess" | cut -d ' ' -f 1-3`"
	if [ "$prefix" != "Error: Syntax error" ]; then
		echo "Unexpected error message for invalid syntax $1"
		exit 1
	fi
}

#
# Just as the name says.  We sprinkle these in the appropriate location
# in the test file system and they just say who they are pretending to be.
#
CreateMockVM() {
	mkdir -p jdk/j2re$1/bin
	echo "#!/bin/sh"    > jdk/j2re$1/bin/java
	echo "echo \"$1\"" >> jdk/j2re$1/bin/java
	chmod +x jdk/j2re$1/bin/java
}

#
# Constructs the jar file needed by these tests.
#
CreateJar() {
	mkdir -p META-INF
	echo "Manifest-Version: 1.0" > META-INF/MANIFEST.MF
	echo "Main-Class: PrintVersion" >> META-INF/MANIFEST.MF
	if [ "$1" != "" ]; then
		echo "JRE-Version: $1" >> META-INF/MANIFEST.MF
	fi
	cp $TESTCLASSES/PrintVersion.class .
	$JAR $2cmf META-INF/MANIFEST.MF PrintVersion PrintVersion.class
}

#
# Constructs a jar file using zip.
#
CreateZippyJar() {
	mkdir -p META-INF
	echo "Manifest-Version: 1.0" > META-INF/MANIFEST.MF
	echo "Main-Class: PrintVersion" >> META-INF/MANIFEST.MF
	if [ "$1" != "" ]; then
		echo "JRE-Version: $1" >> META-INF/MANIFEST.MF
	fi
	cp $TESTCLASSES/PrintVersion.class .
	/usr/bin/zip $2 PrintVersion META-INF/MANIFEST.MF PrintVersion.class
}

#
# Constructs a jar file with a Main-Class attribute of greater than
# 80 characters to validate the continuation line processing.
#
# Make this just long enough to require two continuation lines.  Longer
# paths take too much away from the restricted Windows maximum path length.
# Note: see the variable UGLYCLASS and its check for path length.
#
# Make sure that 5018605 remains fixed by including additional sections
# in the Manifest which contain the same names as those allowed in the
# main section.
#
PACKAGE=reallyreallyreallyreallyreallyreallyreallyreallyreallyreallyreallyreallyreallyreallyreallyreallyreallylongpackagename
UGLYCLASS=$TESTCLASSES/$PACKAGE/UglyPrintVersion.class
CreateUglyJar() {
	mkdir -p META-INF
	echo "Manifest-Version: 1.0" > META-INF/MANIFEST.MF
	echo "Main-Class: $PACKAGE.UglyPrintVersion" >> META-INF/MANIFEST.MF
	if [ "$1" != "" ]; then
		echo "JRE-Version: $1" >> META-INF/MANIFEST.MF
	fi
	echo "" >> META-INF/MANIFEST.MF
	echo "Name: NotToBeFound.class" >> META-INF/MANIFEST.MF
	echo "Main-Class: NotToBeFound" >> META-INF/MANIFEST.MF
	mkdir -p $PACKAGE
	cp $UGLYCLASS $PACKAGE
	$JAR $2cmf META-INF/MANIFEST.MF PrintVersion \
	    $PACKAGE/UglyPrintVersion.class
}

#
# Constructs a jar file with a fair number of "zip directory" entries and
# the MANIFEST.MF entry at or near the end of that directory to validate
# the ability to transverse that directory.
#
CreateFullJar() {
	mkdir -p META-INF
	echo "Manifest-Version: 1.0" > META-INF/MANIFEST.MF
	echo "Main-Class: PrintVersion" >> META-INF/MANIFEST.MF
	if [ "$1" != "" ]; then
	    echo "JRE-Version: $1" >> META-INF/MANIFEST.MF
	fi
	cp $TESTCLASSES/PrintVersion.class .
	for i in 0 1 2 3 4 5 6 7 8 9 ; do
		for j in 0 1 2 3 4 5 6 7 8 9 ; do
			touch AfairlyLongNameEatsUpDirectorySpaceBetter$i$j
		done
	done
	$JAR $2cMf PrintVersion PrintVersion.class AfairlyLong*
	$JAR $2umf META-INF/MANIFEST.MF PrintVersion
	rm -f AfairlyLong*
}

#
# Creates a jar file with the attributes which caused the failure
# described in 4991229.
#
# Generate a bunch of CENTAB entries, each of which is 64 bytes long
# which practically guarentees we will hit the appropriate power of
# two buffer (initially 1K).  Note that due to the perversity of
# zip/jar files, the first entry gets extra stuff so it needs a
# shorter name to compensate.
#
CreateAlignedJar() {
	mkdir -p META-INF
	echo "Manifest-Version: 1.0" > META-INF/MANIFEST.MF
	echo "Main-Class: PrintVersion" >> META-INF/MANIFEST.MF
	if [ "$1" != "" ]; then
	    echo "JRE-Version: $1" >> META-INF/MANIFEST.MF
	fi
	cp $TESTCLASSES/PrintVersion.class .
	touch 57BytesSpecial
	for i in 0 1 2 3 4 5 6 7 8 9 ; do
		for j in 0 1 2 3 4 5 6 7 8 9 ; do
			touch 64BytesPerEntry-$i$j
		done
	done
	$JAR $2cMf PrintVersion 57* 64* PrintVersion.class
	$JAR $2umf META-INF/MANIFEST.MF PrintVersion
	rm -f 57* 64*
}

#
# Adds comments to a jar/zip file.  This serves two purposes:
#
#   1)	Make sure zip file comments (both per file and per archive) are
#	properly processed and ignored.
#
#   2)	A long file comment creates a mondo "Central Directory" entry in
#	the zip file. Such a "mondo" entry could also be due to a very
#	long file name (path) or a long "Ext" entry, but adding the long
#	comment is the easiest way.
#
CommentZipFile() {
    file=
    tail="is designed to take up space - lots and lots of space."
    mv PrintVersion PrintVersion.zip
    /usr/bin/zipnote PrintVersion.zip > zipout
    while read ampersand line; do
	if [ "$ampersand" = "@" ]; then
	    if [ "$line" = "(comment above this line)" ]; then
		echo "File Comment Line." >> zipin
		if [ "$file" = "$1" ]; then
		    for i in 0 1 2 3 4 5 6 7 8 9 a b c d e f; do
			for j in 0 1 2 3 4 5 6 7 8 9 a b c d e f; do
			    echo "Mondo comment line $i$j $tail" >> zipin
			done
		    done
		fi
	    else
		file=$line
	    fi
	fi
	echo "$ampersand $line" >> zipin
	if [ "$ampersand" = "@" ]; then
	    if [ "$line" = "(zip file comment below this line)" ]; then
		echo "Zip File Comment Line number 1" >> zipin
		echo "Zip File Comment Line number 2" >> zipin
	    fi
	fi
    done < zipout
    /usr/bin/zipnote -w PrintVersion.zip < zipin
    mv PrintVersion.zip PrintVersion
    rm zipout zipin
}

#
# Attempt to launch a vm using a version specifier and make sure the
# resultant launch (probably a "mock vm") is appropriate.
#
LaunchVM() {
	if [ "$1" != "" ]; then
		mess="`$JAVA -version:\"$1\" -jar PrintVersion 2>&1`"
	else
		mess="`$JAVA -jar PrintVersion 2>&1`"
	fi
	if [ $? -ne 0 ]; then
		prefix=`echo "$mess" | cut -d ' ' -f 1-3`
		if [ "$prefix" != "Unable to locate" ]; then
			echo "$mess"
			exit 1
		fi
		echo "Unexpected error in attempting to locate $1"
		exit 1
	fi
	echo $mess | grep "$2" > /dev/null 2>&1
	if [ $? != 0 ]; then
	    echo "Launched $mess, expected $2"
	    exit 1
	fi
}

# Tests very long Main-Class attribute in the jar
TestLongMainClass() {
    JVER=$1
    if [ "$JVER" = "mklink" ]; then
        JVER=XX
        JDKXX=jdk/j2re$JVER
        rm -rf jdk
        mkdir jdk
        ln -s $TESTJAVA $JDKXX
        JAVA_VERSION_PATH="`pwd`/jdk"
        export JAVA_VERSION_PATH
    fi
    $JAVAEXE -cp $TESTCLASSES ZipMeUp UglyBetty.jar 4097 
    message="`$JAVAEXE -version:$JVER -jar UglyBetty.jar 2>&1`"
    echo $message | grep "Error: main-class: attribute exceeds system limits" > /dev/null 2>&1
    if [ $? -ne 0 ]; then
        printf "Long manifest test did not get expected error"
        exit 1
    fi
    unset JAVA_VERSION_PATH
    rm -rf jdk
}

#
# Main test sequence starts here
#
RELEASE=`$JAVA -version 2>&1 | head -n 1 | cut -d ' ' -f 3 | \
  sed -e "s/\"//g"`
BASE_RELEASE=`echo $RELEASE | sed -e "s/-.*//g"`

#
# Make sure that the generic jar/manifest reading code works. Test both
# compressed and "stored" jar files.
#
# The "Ugly" jar (long manifest line) tests are only run if the combination
# of the file name length restrictions and the length of the cwd allow it.
#
CreateJar "" ""
LaunchVM "" "${RELEASE}"
CreateJar "" "0"
LaunchVM "" "${RELEASE}"
if [ `IsWindows` = "true" ]; then
    MAXIMUM_PATH=255;
else
    MAXIMUM_PATH=1024;
fi

PATH_LENGTH=`printf "%s" "$UGLYCLASS" | wc -c`
if [ ${PATH_LENGTH} -lt ${MAXIMUM_PATH} ]; then
	CreateUglyJar "" ""
	LaunchVM "" "${RELEASE}"
	CreateUglyJar "" "0"
	LaunchVM "" "${RELEASE}"
else
    printf "Warning: Skipped UglyJar test, path length exceeded, %d" $MAXIMUM_PATH
    printf " allowed, the current path is %d\n" $PATH_LENGTH
fi
CreateAlignedJar "" ""
LaunchVM "" "${RELEASE}"
CreateFullJar "" ""
LaunchVM "" "${RELEASE}"

#
# 4998210 shows that some very strange behaviors are semi-supported.
# In this case, it's the ability to prepend any kind of stuff to the
# jar file and require that the jar file still work.  Note that this
# "interface" isn't publically supported and we may choose to break
# it in the future, but this test guarantees that we won't break it
# without informed consent. We take advantage the fact that the
# "FullJar" we just tested is probably the best jar to begin with
# for this test.
#
echo "This is just meaningless bytes to prepend to the jar" > meaningless
mv PrintVersion meaningfull
cat meaningless meaningfull > PrintVersion
LaunchVM "" "${RELEASE}" 
rm meaningless meaningfull

#
# Officially, one must use "the jar command to create a jar file.  However,
# all the comments about jar commands **imply** that jar files and zip files
# are equivalent.  (Note: this isn't true due to the "0xcafe" insertion.)
# On systems which have a command line zip, test the ability to use zip
# to construct a jar and then use it (6387069).
#
if [ -x /usr/bin/zip ]; then
	CreateZippyJar "" "-q"
	LaunchVM "" "${RELEASE}"
fi

#
# jar files shouldn't have comments, but it is possible that somebody added
# one by using zip -c, zip -z, zipnote or a similar utility.  On systems
# that have "zipnote", verify this functionality.
#
# This serves a dual purpose of creating a very large "central directory
# entry" which validates to code to read such entries.
#
if [ -x /usr/bin/zipnote ]; then
	CreateFullJar "" ""
	CommentZipFile "AfairlyLongNameEatsUpDirectorySpaceBetter20"
	LaunchVM "" "${RELEASE}"
fi

#
# Throw some syntactically challenged (illegal) version specifiers at
# the interface.  Failure (of the launcher) is success for the test.
#
TestSyntax "1.2..3"				# Two adjacent separators
TestSyntax "_1.2.3"				# Begins with a separator
TestSyntax "1.2.3-"				# Ends with a separator
TestSyntax "1.2+.3"				# Embedded modifier
TestSyntax "1.2.4+&1.2*&1++"			# Long and invalid

# On windows we see if there is another jre installed, usually
# there is, then we test using that, otherwise links are created
# to get through to SelectVersion.
if [ `IsWindows` = "false" ]; then
   TestLongMainClass "mklink"
else
    $JAVAEXE -version:1.0+
    if [ $? -eq 0 ]; then
        TestLongMainClass "1.0+"
    else
        printf  "Warning: TestLongMainClass skipped as there is no"
	printf  "viable MJRE installed.\n"
    fi
fi

#
# Because scribbling in the registry can be rather destructive, only a
# subset of the tests are run on Windows.
#
if [ `IsWindows` = "true" ]; then
    exit 0;
fi

#
# Additional version specifiers containing spaces.  (Sigh, unable to
# figure out the glomming on Windows)
#
TestSyntax "1.2.3_99 1.3.2+ 1.2.4+&1.2*&1++"	# Long and invalid

#
# Create a mock installation of a number of shell scripts named as though
# they were installed JREs.  Then test to see if the launcher can cause
# the right shell scripts to be invoked.
#
# Note, that as a side effect, this test verifies that JAVA_VERSION_PATH
# works.
#
rm -rf jdk
JAVA_VERSION_PATH="`pwd`/jdk"
export JAVA_VERSION_PATH

CreateMockVM 1.10
CreateMockVM 1.11.3
CreateMockVM 1.11.3_03
CreateMockVM 1.11.4
CreateMockVM 1.12.3_03
CreateMockVM 1.12.3_03-lastweek
CreateMockVM 1.13.3_03
CreateMockVM 1.13.3_03-lastweek
CreateMockVM 1.13.3_03_lastweek
CreateMockVM 1.20.0

#
# Test extracting the version information from the jar file:
#
#	  Requested		Expected
CreateJar "1.10+" ""
LaunchVM  ""			"1.20.0"
CreateJar "1.11.3_03+&1.11*" ""
LaunchVM  ""			"1.11.4"
CreateJar "1.12.3_03+&1.12.3*" ""
LaunchVM  ""			"1.12.3_03"
CreateJar "1.13.3_03+&1.13.3*" ""
LaunchVM  ""			"1.13.3_03_lastweek"	# Strange but true

#
# Test obtaining the version information from the command line (and that
# it overrides the manifest).
#
CreateJar "${BASERELEASE}*" ""
LaunchVM  "1.10+"		"1.20.0"
LaunchVM  "1.11.3_03+&1.11*"	"1.11.4"
LaunchVM  "1.12.3_03+&1.12.3*"	"1.12.3_03"
LaunchVM  "1.13.3_03+&1.13.3*"	"1.13.3_03_lastweek"	# Strange but true

[ -d jdk ] && rm -rf jdk
[ -d META_INF ] && rm -rf META_INF

exit 0
