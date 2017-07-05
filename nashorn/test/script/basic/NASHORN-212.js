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
 * NASHORN-212 :  Object literal property redefinition restrictions are not implemented
 *
 * @test
 * @run
 */

try {
    eval("obj = { x : 3, get x() { return 42; }");
    fail("data property redefined as accessor");
} catch (e) {
    if (! (e instanceof SyntaxError)) {
        fail("SyntaxError expected got " + e);
    }
}

try {
    eval("obj = { get x() { return 42; }, x: 'hello'");
    fail("accessor property redefined as data");
} catch (e) {
    if (! (e instanceof SyntaxError)) {
        fail("SyntaxError expected got " + e);
    }
}

try {
    eval("obj = { get x() { return 42; }, get x() { return 'hello' }");
    fail("accessor property redefined with different getter");
} catch (e) {
    if (! (e instanceof SyntaxError)) {
        fail("SyntaxError expected got " + e);
    }
}

try {
    eval("obj = { set x(val) { }, set x(val) { }");
    fail("accessor property redefined with different setter");
} catch (e) {
    if (! (e instanceof SyntaxError)) {
        fail("SyntaxError expected got " + e);
    }
}

// data redefinition is fine
obj = { x: 44, x: 'hello' };

// can define getter and setter
obj = { get x() { return 3; }, set x(val) {} };

