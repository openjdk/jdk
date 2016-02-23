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

function assertThrows(src, error) {
    try {
        eval(src);
        Assert.fail("No error, expected " + error);
    } catch (e) {
        if (!(e instanceof error)) {
            Assert.fail("Wrong error, expected " + error + " but got " + e);
        }
    }
}

// Set constructor and prototype

var desc = Object.getOwnPropertyDescriptor(this, "Set");
Assert.assertEquals(desc.writable, true);
Assert.assertEquals(desc.configurable, true);
Assert.assertEquals(desc.enumerable, false);

Assert.assertTrue(Object.getPrototypeOf(new Set()) === Set.prototype);
Assert.assertTrue(Object.getPrototypeOf(Set.prototype) === Object.prototype);
Assert.assertTrue(new Set().size === 0);
Assert.assertTrue(Object.getPrototypeOf(new Set(["a", 3, false])) === Set.prototype);
Assert.assertTrue(new Set(["a", 3, false]).size === 3);
Assert.assertTrue(new Set("abc").size === 3);
Assert.assertTrue(new Set("").size === 0);
Assert.assertTrue(new Set([]).size === 0);

assertThrows("Set()", TypeError);
assertThrows("new Set(3)", TypeError);
assertThrows("new Set({})", TypeError);

assertThrows("Set.prototype.add.apply({}, [''])", TypeError);
assertThrows("Set.prototype.has.apply(3, [''])", TypeError);
assertThrows("Set.prototype.delete.apply('', [3])", TypeError);

// Set methods

var s = new Set(["a", 3, false]);
Assert.assertTrue(s.size, 2);
Assert.assertTrue(s.has("a") === true);
Assert.assertTrue(s.has(3) === true);
Assert.assertTrue(s.has(false) === true);

Assert.assertTrue(s.clear() === undefined);
Assert.assertTrue(s.size === 0);
Assert.assertTrue(s.has("a") === false);
Assert.assertTrue(s.has(3) === false);
Assert.assertTrue(s.has(false) === false);

var a = "a", x = "x"; // for ConsString keys
Assert.assertTrue(s.add("ab", false) === s);
Assert.assertTrue(s.add(x + "y", s) === s);
Assert.assertTrue(s.has(a + "b") === true);
Assert.assertTrue(s.has("xy") === true);

// Special keys

s.clear()
Assert.assertTrue(s.add(NaN) === s);  // NaN should work as key
Assert.assertTrue(s.size === 1);
Assert.assertTrue(isNaN(s.keys().next().value));
Assert.assertTrue(isNaN(s.values().next().value));
Assert.assertTrue(s.has(NaN) === true);
Assert.assertTrue(s.delete(NaN) === true);
Assert.assertTrue(s.size === 0);
Assert.assertTrue(s.keys().next().done);
Assert.assertTrue(s.values().next().done);
Assert.assertTrue(s.has(NaN) === false);

s.clear()
s.add(-0); // -0 key should be converted to +0
Assert.assertTrue(s.size === 1);
Assert.assertTrue(1 / s.keys().next().value === Infinity);
Assert.assertTrue(1 / s.values().next().value === Infinity);
Assert.assertTrue(s.has(-0) === true);
Assert.assertTrue(s.has(0) === true);
Assert.assertTrue(s.delete(-0) === true);
Assert.assertTrue(s.size === 0);
Assert.assertTrue(s.has(-0) === false);
Assert.assertTrue(s.has(0) === false);

// foreach

s = new Set([1, 2, 3]);

s.forEach(function(value, key, set) {
    Assert.assertTrue(this === s);
    Assert.assertTrue(set === s);
}, s);

function assertEqualArrays(a, b) {
    Assert.assertTrue(Array.isArray(a));
    Assert.assertTrue(Array.isArray(b));
    Assert.assertTrue(a.length === b.length);
    Assert.assertTrue(a.every(function(v, j) {
        return v === b[j];
    }));
}

let array = [];
s = new Set([1, 2, 3]);
s.forEach(function(value, key, set) {
    array.push(value);
});
assertEqualArrays(array, [1, 2, 3]);

array = [];
s = new Set([1, 2, 3]);
s.forEach(function(value, key, set) {
    array.push(value);
    if (key == 3) {
        set.clear();
        set.add("four");
    }
});
assertEqualArrays(array, [1, 2, 3, "four"]);

array = [];
s = new Set([1, 2, 3]);
s.forEach(function(value, key, set) {
    array.push(value);
    if (key == 1) {
        set.delete(1);
    }
    if (key == 2) {
        set.delete(3);
    }
});
assertEqualArrays(array, [1, 2]);

array = [];
s = new Set([1, 2, 3]);
s.forEach(function(value, key, set) {
    array.push(value);
    if (key < 4) {
        set.delete(key);
        set.add(key + 3)
    }
});
assertEqualArrays(array, [1, 2, 3, 4, 5, 6]);


