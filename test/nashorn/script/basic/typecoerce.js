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
 * There was a bug in the old Lower that didn't fully propagate type information
 * by way of assignment. 'q' in the example below would have finished a double
 * even though it can get an object value through the assignment 'q = l'
 *
 * Furthermore, this caused type coercion to be done at q = l, and not a q = q * 2,
 * which is a bug. This test ensures it happens in the correct order
 *
 * @test
 * @run
 */

function createObject() {
    var obj = { valueOf: function() { print("toNumber coercion"); return 17; }}
    return obj;
}

function f() {
    var l = 1.2; //number
    var q = 2.3; //number
    for (var i = 0; i < 2; i++) {
    q = l; // q = toNumber(l), no coercion here
    print("assignment done");
    q = q * 2; // q = q * 2, coercion here
    print("multiplication done");
    l = createObject();
    }
}

f();
