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

OS=`uname -s`
case "$OS" in
  SunOS )
    PS=":"
    ;;
  Linux )
    PS=":"
    ;;
  Darwin )
    PS=":"
    ;;
  AIX )
    PS=":"
    ;;
  Windows*)
    PS=";"
    ;;
  CYGWIN*)
    PS=";"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

mkdir -p exec/java.xml compile/java.xml

$COMPILEJAVA/bin/javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} \
   -d compile/java.xml --patch-module java.xml=$TESTSRC/patch-src1 \
   $TESTSRC/patch-src1/org/w3c/dom/Document.java \
   $TESTSRC/patch-src1/org/w3c/dom/Node.java || exit 1

$COMPILEJAVA/bin/javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} \
   -d exec/java.xml --patch-module java.xml=compile/java.xml${PS}$TESTSRC/patch-src2 \
   $TESTSRC/patch-src2/com/sun/org/apache/xerces/internal/dom/DocumentImpl.java \
   || exit 2

$COMPILEJAVA/bin/javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} \
   $TESTSRC/AbstractMethodErrorTest.java -d exec || exit 3

$TESTJAVA/bin/java ${TESTVMOPTS} --patch-module java.xml=exec -cp exec AbstractMethodErrorTest || exit 4

exit 0

