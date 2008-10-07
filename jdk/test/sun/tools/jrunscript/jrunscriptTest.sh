#!/bin/sh

#
# Copyright 2005-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
# @bug 6265810 6705893
# @build CheckEngine
# @run shell jrunscriptTest.sh
# @summary Test that output of 'jrunscript' interactive matches the repl.out file

. ${TESTSRC-.}/common.sh

setup
${JAVA} -cp ${TESTCLASSES} CheckEngine
if [ $? -eq 2 ]; then
    echo "No js engine found and engine not required; test vacuously passes."
    exit 0
fi

rm -f jrunscriptTest.out 2>/dev/null
${JRUNSCRIPT} > jrunscriptTest.out 2>&1 <<EOF
v = 2 + 5;
v *= 5;
v = v + " is the value";
if (v != 0) { println('yes v != 0'); }
java.lang.System.out.println('hello world from script');
new java.lang.Runnable() { run: function() { println('I am runnable'); }}.run();
EOF

diff jrunscriptTest.out ${TESTSRC}/repl.out
if [ $? != 0 ]
then
  echo "Output of jrunscript session differ from expected output. Failed."
  rm -f jrunscriptTest.out 2>/dev/null
  exit 1
fi

rm -f jrunscriptTest.out 2>/dev/null
${JRUNSCRIPT} -l js > jrunscriptTest.out 2>&1 <<EOF
v = 2 + 5;
v *= 5;
v = v + " is the value";
if (v != 0) { println('yes v != 0'); }
java.lang.System.out.println('hello world from script');
new java.lang.Runnable() { run: function() { println('I am runnable'); }}.run();
EOF

diff jrunscriptTest.out ${TESTSRC}/repl.out
if [ $? != 0 ]
then
  echo "Output of jrunscript -l js differ from expected output. Failed."
  rm -f jrunscriptTest.out 2>/dev/null
  exit 1
fi

rm -f jrunscriptTest.out
echo "Passed"
exit 0
