#
# Copyright (c) 2004, 2010, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4958170 4891531 4989534
# @summary Test to see if default java locale settings are identical
#          when launch jvm from java and javaw respectively. Test 
#          should be run on Windows with different user locale and 
#          system locale setting in ControlPanel's RegionSetting.
#          Following 2 testing scenarios are recommended
#          (1)systemLocale=Japanese, userLocale=English
#          (2)systemLocale=English, userLocale=Japanese
# @run shell DefaultLocaleTest.sh
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

OS=`uname`

case "$OS" in
        Windows* )
            JAVAC="${TESTJAVA}/bin/javac -d . "
            JAVA="${TESTJAVA}/bin/java -classpath . "
            JAVAW="${TESTJAVA}/bin/javaw -classpath . "

            ${JAVAC} ${TESTSRC}/DefaultLocaleTest.java
            props=`${JAVA} DefaultLocaleTest`
            ${JAVAW} DefaultLocaleTest $props
            if [ $? -ne 0 ]
            then
                echo "Test fails"
                exit 1
            fi
            echo "Test passes"
            exit 0
        ;;
        CYGWIN* )
            JAVAC="${TESTJAVA}/bin/javac -d . "
            JAVA="${TESTJAVA}/bin/java -classpath . "
            JAVAW="${TESTJAVA}/bin/javaw -classpath . "

            ${JAVAC} ${TESTSRC}/DefaultLocaleTest.java
            ${JAVA} DefaultLocaleTest | sed -e s@\\r@@g > x.out
            ${JAVAW} DefaultLocaleTest `cat x.out`
            if [ $? -ne 0 ]
            then
                echo "Test fails"
                exit 1
            fi
            echo "Test passes"
            exit 0
        ;;
        * )
        echo "Non-windows environment; test vacuously succeeds."
        exit 0;
    ;;
esac



