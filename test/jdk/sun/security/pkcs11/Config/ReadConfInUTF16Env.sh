#
# Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
# @bug 8187023
# @summary Pkcs11 config file should be assumed in ISO-8859-1
# @library /test/lib
# @build ReadConfInUTF16Env
# @run shell ReadConfInUTF16Env.sh

# jtreg does not like -Dfile.encoding=UTF-16 inside a @run main line,
# testlibrary.ProcessTools.createJavaProcessBuilder() also had troubles
# executing a subprocess with -Dfile.encoding=UTF-16 option added,
# therefore a shell test is written.

$TESTJAVA/bin/java $TESTVMOPTS -cp $TESTCLASSES \
  -Dfile.encoding=UTF-16 \
  ReadConfInUTF16Env

