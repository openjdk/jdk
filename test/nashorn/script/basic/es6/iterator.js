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
 * JDK-8147558: Add support for ES6 collections
 *
 * @test
 * @run
 * @option --language=es6
 */


function testIterator(iter, expectedValues) {
    let next;

    for (var i = 0; i < expectedValues.length; i++) {
        next = iter.next();
        Assert.assertTrue(next.done === false);

        if (Array.isArray(expectedValues[i])) {
            Assert.assertTrue(Array.isArray(next.value));
            Assert.assertTrue(next.value.length === expectedValues[i].length);
            Assert.assertTrue(next.value.every(function(v, j) {
                return v === expectedValues[i][j];
            }));

        } else {
            Assert.assertTrue(next.value === expectedValues[i]);
        }
    }

    next = iter.next();
    Assert.assertTrue(next.done === true);
    Assert.assertTrue(next.value === undefined);
}

const str = "abcdefg";
const array = ["a", "b", "c", "d", "e", "f", "g"];
const arrayKeys = [0, 1, 2, 3, 4, 5, 6];
const setEntries = [["a", "a"], ["b", "b"], ["c", "c"], ["d", "d"], ["e", "e"], ["f", "f"], ["g", "g"]];
const mapEntries = [["a", "A"], ["b", "B"], ["c", "C"], ["d", "D"], ["e", "E"], ["f", "F"], ["g", "G"]];
const mapValues = ["A", "B", "C", "D", "E", "F", "G"];
const arrayEntries = [[0, "a"], [1, "b"], [2, "c"], [3, "d"], [4, "e"], [5, "f"], [6, "g"]];

// Set iterator tests
const set = new Set(str);
testIterator(set[Symbol.iterator](), str);
testIterator(set.values(), str);
testIterator(set.keys(), str);
testIterator(set.entries(), setEntries);

// Map iterator tests
const map = new Map(mapEntries);
testIterator(map[Symbol.iterator](), mapEntries);
testIterator(map.values(), mapValues);
testIterator(map.keys(), array);
testIterator(map.entries(), mapEntries);

// String iterator tests
testIterator(str[Symbol.iterator](), str);

// Array iterator tests
testIterator(array[Symbol.iterator](), array);
testIterator(array.values(), array);
testIterator(array.keys(), arrayKeys);
testIterator(array.entries(), arrayEntries);

