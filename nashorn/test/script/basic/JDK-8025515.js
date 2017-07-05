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
 * JDK-8025515: Performance issues with Source.getLine()
 *
 * @test
 * @run
 */

// Make sure synthetic names of anonymous functions have correct line numbers

function getFirstScriptFrame(stack) {
    for (frameNum in stack) {
        var frame = stack[frameNum];
        if (frame.className.startsWith("jdk.nashorn.internal.scripts.Script$")) {
            return frame;
        }
    }
}

function testMethodName(f, expected) {
    try {
        f();
        fail("expected error");
    } catch (e) {
        var stack = e.nashornException.getStackTrace();
        var name = getFirstScriptFrame(stack).methodName;
        if (name !== expected) {
            fail("got " + name + ", expected " + expected);
        }
    }
}

testMethodName(function() {
    return a.b.c;
}, "L:55");

testMethodName(function() { throw new Error() }, "L:59");

var f = (function() {
    return function() { a.b.c; };
})();
testMethodName(f, "f$L:62");

testMethodName((function() {
    return function() { return a.b.c; };
})(), "L:66$L:67");
