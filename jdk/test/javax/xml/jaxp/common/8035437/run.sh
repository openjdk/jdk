#!/bin/sh

##
# Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
##

# @test
# @bug 8035437
# @summary Tests that java.lang.AbstractMethodError is not thrown when
#    serializing improper version of DocumentImpl class.

mkdir -p exec compile

$COMPILEJAVA/bin/javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} \
   -d compile $TESTSRC/Document.java $TESTSRC/Node.java || exit 1

$COMPILEJAVA/bin/javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} \
   -Xbootclasspath/p:compile -d exec $TESTSRC/DocumentImpl.java || exit 1

$COMPILEJAVA/bin/javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} \
   $TESTSRC/AbstractMethodErrorTest.java -d exec || exit 1

$TESTJAVA/bin/java ${TESTVMOPTS} -Xbootclasspath/p:exec -cp exec AbstractMethodErrorTest || exit 1

exit 0
