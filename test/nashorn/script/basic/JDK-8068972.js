/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8068972: Array.splice should follow the ES6 specification
 *
 * @test
 * @run
 */


function assertEqualArrays(a, b) {
    Assert.assertTrue(Array.isArray(a));
    Assert.assertTrue(Array.isArray(b));
    Assert.assertTrue(a.length === b.length);
    Assert.assertTrue(a.every(function(v, j) {
        return v === b[j];
    }));
}

var array = [1, 2, 3, 4, 5, 6, 7];

var result = array.splice();
assertEqualArrays(array, [1, 2, 3, 4, 5, 6, 7]);
assertEqualArrays(result, []);

result = array.splice(4);
assertEqualArrays(array, [1, 2, 3, 4]);
assertEqualArrays(result, [5, 6, 7]);

result = array.splice(1, 2, -2, -3);
assertEqualArrays(array, [1, -2, -3, 4]);
assertEqualArrays(result, [2, 3]);
