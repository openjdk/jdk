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
 * NASHORN-437 : Array.prototype.map should return an array whose length should be same as input array length
 *
 * @test
 * @run
 */

var callbackCount = 0;

function func(kVal, k, arr) {
    arr.length = 2;
    callbackCount++;
    return 42;
}

var arr = [ 1, 2, 3, 4];
var res = arr.map(func);
if (res.length !== 4) {
   fail("map's result does not have 4 elements");
}

if (callbackCount !== 2) {
    fail("callback should be called only twice");
}

if (res[2] !== undefined || res[3] !== undefined) {
    fail("last two elements are not undefined");
}
