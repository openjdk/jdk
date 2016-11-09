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
 * JDK-8168140: TypedArrays should implement ES6 iterator protocol
 *
 * @test
 * @run
 * @option --language=es6
 */

let TypedArrayTypes = [
    Int8Array,
    Uint8Array,
    Uint8ClampedArray,
    Int16Array,
    Uint16Array,
    Int32Array,
    Uint32Array,
    Float32Array,
    Float64Array
];

let arrays = [];
let sum = 0;

TypedArrayTypes.forEach(function(ArrayType) {
    var a = new ArrayType(10);
    for (let i = 0; i < a.length; i++) {
        a[i] = i;
    }
    arrays.push(a);
});

Assert.assertTrue(arrays.length === 9);

for (let array of arrays) {

    Assert.assertTrue(array.length === 10);
    let count = 0;

    for (let value of array) {
        Assert.assertTrue(value === count++);
        sum += value;
    }
}

Assert.assertTrue(sum === 405);
