/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 * 
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 * 
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * 
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * JDK-8028434: Check that the line number of the tests in while and do while loops
 * is correct. It needs to correspond to the line with the test expression.
 *
 * @test
 * @run
 */

try {
    while (test.apa < 0) {
	print("x");
    }
} catch (e) {
    var st = e.getStackTrace();
    if (st.length != 1) {
	print("erroneous stacktrace length " + s.length);
    }
    if (st[0].lineNumber !== 33) {
	print("erroneous stacktrace element, lineNumber=" + st[0].lineNumber + " elem=" + st);
    }
}

try {
    do {
	print("x");
    } while (test.apa < 0);
} catch (e) {
    var st = e.getStackTrace();
    if (st.length != 1) {
	print("erroneous stacktrace length " + s.length);
    }
    if (st[0].lineNumber !== 49) {
	print("erroneous stacktrace element, lineNumber= " + st[0].lineNumber + " elem=" + st);
    }
}
