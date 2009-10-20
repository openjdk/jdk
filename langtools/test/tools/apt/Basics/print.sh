#!/bin/sh

#
# Copyright 2004-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
# @bug 5008759 4998341 5018369 5032476 5060121 5096932 5096931
# @run shell ../verifyVariables.sh
# @run shell print.sh
# @summary test availabilty of print option
# @author Joseph D. Darcy

OS=`uname -s`;
case "${OS}" in
        CYGWIN* )
                DIFFOPTS="--strip-trailing-cr"
        ;;

	* )
	;;
esac

# Compile file directly, without TESTJAVACOPTS
# Don't use @build or @compile as these implicitly use jtreg -javacoption values
# and it is important that this file be compiled as expected, for later comparison
# against a golden file.
"${TESTJAVA}/bin/javac" ${TESTTOOLVMOPTS} -d ${TESTCLASSES} ${TESTSRC}/Aggregate.java

# Construct path to apt executable
APT="${TESTJAVA}/bin/apt ${TESTTOOLVMOPTS} \
-print "

printf "%s\n" "APT = ${APT}"

PRINT_FILES="${TESTSRC}/MisMatch.java \
${TESTSRC}/GenClass.java \
${TESTSRC}/Misc.java \
${TESTSRC}/Lacuna.java"

for i in ${PRINT_FILES}
do
	# Delete any existing class file
        FILENAME=`basename $i .java`
	rm -f ${FILENAME}.class

        printf "%s\n" "Printing ${i}"
        ${APT} ${i}

        RESULT=$?
        case "$RESULT" in
                0  )
                ;;

                * )
                echo "Problem printing file ${i}."
                exit 1
        esac

        # Verify compilation did not occur
	if [ -f ${FILENAME}.class ]; then
		printf "Improper compilation occured for %s.\n" ${i}
		exit 1
	fi
	
done

# check for mutliple methods and no static initializer

${APT} -XclassesAsDecls -cp ${TESTCLASSES} -print Aggregate > aggregate.txt
diff ${DIFFOPTS} aggregate.txt ${TESTSRC}/goldenAggregate.txt

RESULT=$?
case "$RESULT" in
        0  )
        ;;

        * )
        echo "Expected output not received"
        exit 1
esac

exit 0
