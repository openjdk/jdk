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
 * JDK-8151700: Add support for ES6 for-of
 *
 * @test
 * @run
 * @option --language=es6
 */

let result = "";
for (let a of [1, 2, "foo"]) {
    result += a;
}

if (result !== "12foo") {
    throw new Error("unexpcected result: " + result);
}

let sum = 0;
let numbers = [1, 2, 3, 4];
numbers.ten = 10; // not iterated over

for (let n of numbers) {
    sum += n;
}

if (sum !== 10) {
    throw new Error("unexpected sum: " + sum);;
}

if (typeof n !== "undefined") {
    throw new Error("n is visible outside of for-of");
}

let message = "Hello";
result = "";

for(const c of message) {
    result += c;
}

if (result !== "Hello") {
    throw new Error("unexpected result: " + result);
}

if (typeof c !== "undefined") {
    throw new Error("c is visible outside of for-of")
}

// Callbacks with per-iteration scope

result = "";
let funcs = [];

for (let a of [1, 2, "foo"]) {
    funcs.push(function() { result += a; });
}

funcs.forEach(function(f) { f(); });
if (result !== "12foo") {
    throw new Error("unexpcected result: " + result);
}

result = "";
funcs = [];

for (const a of [1, 2, "foo"]) {
    funcs.push(function() { result += a; });
}

funcs.forEach(function(f) { f(); });
if (result !== "12foo") {
    throw new Error("unexpcected result: " + result);
}

// Set
var set = new Set(["foo", "bar", "foo"]);
result = "";

for (var w of set) {
    result += w;
}

if (result !== "foobar") {
    throw new Error("unexpected result: " + result);
}

// Maps
var map = new Map([["a", 1], ["b", 2]]);
result = "";

for (var entry of map) {
    result += entry;
}

if (result !== "a,1b,2") {
    throw new Error("unexpected result: " + result);
}

// per-iteration scope

let array = ["a", "b", "c"];
funcs = [];

for (let i of array) {
    for (let j of array) {
        for (let k of array) {
            funcs.push(function () {
                return i + j + k;
            });
        }
    }
}

Assert.assertEquals(funcs.length, 3 * 3 * 3);
let count = 0;

for (let i = 0; i < 3; i++) {
    for (let j = 0; j < 3; j++) {
        for (let k = 0; k < 3; k++) {
            Assert.assertEquals(funcs[count++](), array[i] + array[j] + array[k]);
        }
    }
}

// per-iteration scope with const declaration

funcs = [];

for (const i of array) {
    for (const j of array) {
        for (const k of array) {
            funcs.push(function () {
                return i + j + k;
            });
        }
    }
}

Assert.assertEquals(funcs.length, 3 * 3 * 3);
count = 0;

for (let i = 0; i < 3; i++) {
    for (let j = 0; j < 3; j++) {
        for (let k = 0; k < 3; k++) {
            Assert.assertEquals(funcs[count++](), array[i] + array[j] + array[k]);
        }
    }
}

// fibonacci iterator

let fibonacci = {};

fibonacci[Symbol.iterator] = function() {
    let previous = 0, current = 1;
    return {
        next: function() {
            let tmp = current;
            current = previous + current;
            previous = tmp;
            return { done: false, value: current };
        }
    }
};

for (f of fibonacci) {
    if (f > 100000) {
        break;
    }
}

Assert.assertTrue(f === 121393);

