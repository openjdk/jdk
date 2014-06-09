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
 * NASHORN-251 : Global built-in constructor prototypes are destroyed by user assignments.
 *
 * @test
 * @run
 */

// assign to global "Array"
Array = 3;
// we can create literal array
x = [23, 33];
if (x.join("-") !== '23-33') {
    fail("x.join('-') !== '23-33'");
}

try {
    new Array();
    fail("should have thrown TypeError for 'Array' constructor!");
} catch (e) {
    if (! (e instanceof TypeError)) {
        fail("should have thrown TypeError, got " + e);
    }
}

// assign to global "RegExp"
RegExp = true;
// we can still create RegExp literal
y = /foo/;
if (! y.test("foo")) {
   fail("y.test('foo') is not true");
}

try {
    new RegExp();
    fail("should have thrown TypeError for 'RegExp' constructor!");
} catch (e) {
    if (! (e instanceof TypeError)) {
        fail("should have thrown TypeError, got " + e);
    }
}

TypeError = "foo";
try {
    var foo = 3;
    foo();
} catch(e) {
    if (! (e instanceof Error)) {
        fail("Error subtype expected, got " + e);
    }

    if (e.name !== 'TypeError') {
        fail("e is not of type 'TypeError'");
    }
}

SyntaxError = 234;
try {
    eval("344**++33");
} catch(e) {
    if (! (e instanceof Error)) {
        fail("Error subtype expected, got " + e);
    }

    if (e.name !== 'SyntaxError') {
        fail("e is not of type 'SyntaxError'");
    }
}

ReferenceError = "foo";
try {
    print(unknown);
} catch(e) {
    if (! (e instanceof Error)) {
        fail("Error subtype expected, got " + e);
    }

    if (e.name !== 'ReferenceError') {
        fail("e is not of type 'ReferenceError'");
    }
}
