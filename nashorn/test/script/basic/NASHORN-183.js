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
 * NASHORN-183 :  Array.prototype.reduce does not work for array-like objects
 *
 * @test
 * @run
 */

function callback(acc, newVal) {
    return acc + newVal;
}

function func() {
    return Array.prototype.reduce.call(arguments, callback);
}

var val = func(232, 334, 454);
if (val != (232 + 334 + 454)) {
    fail("expecting " + (232 + 334 + 454));
}

var arr = [3, 4, 5, 6];
var obj = Object.create(arr);
val = Array.prototype.reduce.call(obj, callback);

var val2 = 0;
for (i in arr) {
    val2 += arr[i];
}

if (val != val2) {
    fail("val != val2, val2 is " + val2);
}
