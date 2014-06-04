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
 * NASHORN-623 : JSON parser does not allow negative integer
 *
 * @test
 * @run
 */

var obj = JSON.parse("{ \"test\" : -1 }");
if (obj.test != -1) {
    fail("expected obj.test to be -1, got " + obj.test);
}

obj = JSON.parse("{ \"test\" : [3, -2] }");
if (obj.test[1] != -2) {
    fail("expected obj.test[1] to be -2, got " + obj.test[1]);
}

obj = JSON.parse("{ \"test\": { \"foo\": -3 } }");
if (obj.test.foo != -3) {
    fail("expected obj.test.foo to be -3, got " + obj.test.foo);
}

try {
    JSON.parse("{ \"test\" : -xxx }");
    fail("should have thrown SyntaxError");
} catch (e) {
    if (! (e instanceof SyntaxError)) {
        fail("expected SyntaxError, got " + e);
    }
    print(e);
}
