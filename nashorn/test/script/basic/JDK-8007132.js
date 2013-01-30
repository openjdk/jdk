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
 * JDK-8007232: Java objects returned from constructor functions are lost
 *
 * @test
 * @run
 */

function Func() {
    return new java.util.HashMap();
}

var f = new Func();
if (! (f instanceof java.util.Map)) {
    fail("instance of java.util.Map expected")
}

function check(obj) {
    if (typeof obj != 'object') {
        fail("expected 'object' but got " + (typeof obj));
    }
}

// check primitive returning constructors
check(new (function() { return 22; }));
check(new (function() { return false }));
check(new (function() { return "hello" }));

// check null and undefined returning constructors
check(new (function() { return null; }));
check(new (function() { return undefined; }));
