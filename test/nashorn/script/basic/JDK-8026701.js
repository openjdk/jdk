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
 * JDK-8026701: Array.prototype.splice is slow on dense arrays
 *
 * @test
 * @run
 */

function testSplice(arr, e1, e2, e3) {
    try {
        print(arr);
        print(arr.splice(3, 0, e1, e2, e3));
        print(arr);
        print(arr.splice(2, 3));
        print(arr);
        print(arr.splice(2, 3, arr[2], arr[3], arr[4]));
        print(arr);
        print(arr.splice(20, 10));
        print(arr);
        print(arr.splice(arr.length, 0, e1, e2, e3));
        print(arr);
        print(arr.splice(0, 2, arr[0], arr[1], arr[2], arr[3]));
        print(arr);
    } catch (error) {
        print(error);
    }
}

function convert(array, type) {
    return (typeof Java === "undefined") ? array : Java.from(Java.to(array, type));
}

// run some splice tests on all dense array implementations
testSplice([1, 2, 3, 4, 5, 6, 7, 8, 9, 10], -1, -2, -3);
testSplice(convert([1, 2, 3, 4, 5, 6, 7, 8, 9, 10], "long[]"), -1, -2, -3);
testSplice(convert([1, 2, 3, 4, 5, 6, 7, 8, 9, 10], "double[]"), -1, -2, -3);
testSplice(["1", "2", "3", "4", "5", "6", "7", "8", "9", "10"], -1, -2, -3);

// test array conversion during splice
testSplice([1, 2, 3, 4, 5, 6, 7, 8, 9, 10], -1, "-2", "-3");
testSplice([1, 2, 3, 4, 5, 6, 7, 8, 9, 10], -1, -2.5, -3.5);
testSplice(convert([1, 2, 3, 4, 5, 6, 7, 8, 9, 10], "long[]"), -1, "-2", "-3");
testSplice(convert([1, 2, 3, 4, 5, 6, 7, 8, 9, 10], "long[]"), -1, -2.5, -3.5);
testSplice(convert([1, 2, 3, 4, 5, 6, 7, 8, 9, 10], "double[]"), -1, "-2", "-3");

// test combination with defined elements
testSplice(Object.defineProperty([1, 2, 3, 4, 5, 6, 7, 8, 9, 10], 5, {value: 13}), -1, -2, -3);
testSplice(Object.defineProperty([1, 2, 3, 4, 5, 6, 7, 8, 9, 10], 5, {value: 13, writable: false}), -1, -2, -3);
testSplice(Object.defineProperty([1, 2, 3, 4, 5, 6, 7, 8, 9, 10], 5, {value: 13, configurable: false}), -1, -2, -3);
testSplice(Object.defineProperty([1, 2, 3, 4, 5, 6, 7, 8, 9, 10], 5, {value: 13, writable: false, configurable: false}), -1, -2, -3);
