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
 * NASHORN-486 : error reporting is inconsistent
 *
 * @test
 * @run
 */

var fileName = __FILE__;

try {
    // save line number and force ReferenceError for 'foo'
    var lineNumber = __LINE__; print(foo);
    fail("#1 should have thrown ReferenceError");
} catch (e) {
    if (! (e instanceof ReferenceError)) {
        fail("#2 ReferenceError expected, got " + e);
    }

    if (e.lineNumber !== lineNumber) {
        fail("#3 line number not correct");
    }

    if (e.fileName !== fileName) {
        fail("#4 file name not correct");
    }
}

try {
    // try any library function on invalid self
    // get line number again and force a TypeError
    lineNumber = __LINE__; RegExp.prototype.exec.call(3);
    fail("#5 should have thrown TypeError");
} catch (e) {
    if (! (e instanceof TypeError)) {
        fail("#6 TypeError expected, got " + e);
    }

    if (e.lineNumber !== lineNumber) {
        fail("#7 line number not correct");
    }

    if (e.fileName !== fileName) {
        fail("#8 file name not correct");
    }
}

// try explicit exception thrown from script
try {
    lineNumber = __LINE__; throw new Error();
} catch (e) {
    if (e.lineNumber !== lineNumber) {
        fail("#9 line number not correct");
    }

    if (e.fileName !== fileName) {
        fail("#10 file name not correct");
    }
}
