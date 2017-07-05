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

// Map constructor and prototype

var desc = Object.getOwnPropertyDescriptor(this, "Map");
Assert.assertEquals(desc.writable, true);
Assert.assertEquals(desc.configurable, true);
Assert.assertEquals(desc.enumerable, false);

Assert.assertTrue(Object.getPrototypeOf(new Map()) === Map.prototype);
Assert.assertTrue(Object.getPrototypeOf(Map.prototype) === Object.prototype);
Assert.assertTrue(new Map().size === 0);
Assert.assertTrue(Object.getPrototypeOf(new Map([["a", 1], ["b", 2]])) === Map.prototype);
Assert.assertTrue(new Map([["a", 1], ["b", 2]]).size === 2);
Assert.assertTrue(Object.getPrototypeOf(new Map("")) === Map.prototype);
Assert.assertTrue(new Map("").size === 0);
Assert.assertTrue(Object.getPrototypeOf(new Map([])) === Map.prototype);
Assert.assertTrue(new Map([]).size === 0);

assertThrows("Map()", TypeError);
assertThrows("new Map(3)", TypeError);
assertThrows("new Map('abc')", TypeError);
assertThrows("new Map({})", TypeError);
assertThrows("new Map([1, 2, 3])", TypeError);

assertThrows("Map.prototype.set.apply({}, ['', ''])", TypeError);
assertThrows("Map.prototype.get.apply([], [''])", TypeError);
assertThrows("Map.prototype.has.apply(3, [''])", TypeError);
assertThrows("Map.prototype.clear.apply('', [])", TypeError);

// Map methods

var m = new Map([["a", 1], ["b", 2]]);
Assert.assertTrue(m.size, 2);
Assert.assertTrue(m.get("a") === 1);
Assert.assertTrue(m.get("b") === 2);
Assert.assertTrue(m.get("c") === undefined);
Assert.assertTrue(m.has("a") === true);
Assert.assertTrue(m.has("b") === true);
Assert.assertTrue(m.has("c") === false);

m.clear();
Assert.assertTrue(m.size === 0);
Assert.assertTrue(m.get("a") === undefined);
Assert.assertTrue(m.get("b") === undefined);
Assert.assertTrue(m.get("c") === undefined);
Assert.assertTrue(m.has("a") === false);
Assert.assertTrue(m.has("b") === false);
Assert.assertTrue(m.has("c") === false);

var a = "a", x = "x"; // for ConsString keys
Assert.assertTrue(m.set("ab", false) === m);
Assert.assertTrue(m.set(x + "y", m) === m);
Assert.assertTrue(m.get(a + "b") === false);
Assert.assertTrue(m.get("xy") === m);
Assert.assertTrue(m.has(a + "b") === true);
Assert.assertTrue(m.has("xy") === true);

// Special keys

m = new Map();
Assert.assertTrue(m.set(NaN, NaN) === m);  // NaN should work as key
Assert.assertTrue(m.size === 1);
Assert.assertTrue(isNaN(m.get(NaN)));
Assert.assertTrue(isNaN(m.keys().next().value));
Assert.assertTrue(isNaN(m.values().next().value));
Assert.assertTrue(m.has(NaN) === true);
Assert.assertTrue(m.delete(NaN));
Assert.assertTrue(m.size === 0);
Assert.assertTrue(m.get(NaN) === undefined);
Assert.assertTrue(m.keys().next().done);
Assert.assertTrue(m.values().next().done);
Assert.assertTrue(m.has(NaN) === false);

m.clear();
m.set(-0, -0); // -0 key should be converted to +0
Assert.assertTrue(m.size === 1);
Assert.assertTrue(m.get(-0) === 0);
Assert.assertTrue(1 / m.keys().next().value === Infinity);
Assert.assertTrue(1 / m.values().next().value === -Infinity);
Assert.assertTrue(m.has(-0) === true);
Assert.assertTrue(m.has(0) === true);
Assert.assertTrue(m.delete(-0));
Assert.assertTrue(m.size === 0);
Assert.assertTrue(m.get(-0) === undefined);
Assert.assertTrue(m.get(0) === undefined);
Assert.assertTrue(m.has(-0) === false);
Assert.assertTrue(m.has(0) === false);

Assert.assertFalse(m.delete("foo"));
Assert.assertFalse(m.delete(0));
Assert.assertFalse(m.delete(NaN));

// foreach

m = new Map([[1, "one"], [2, "two"], [3, "three"]]);
m.forEach(function(value, key, map) {
    Assert.assertTrue(this === m);
    Assert.assertTrue(map === m);
}, m);

function assertEqualArrays(a, b) {
    Assert.assertTrue(Array.isArray(a));
    Assert.assertTrue(Array.isArray(b));
    Assert.assertTrue(a.length === b.length);
    Assert.assertTrue(a.every(function(v, j) {
        return v === b[j];
    }));
}

let array = [];
m = new Map([[1, "one"], [2, "two"], [3, "three"]]);
m.forEach(function(value, key, map) {
    array.push(value);
});
assertEqualArrays(array, ["one", "two", "three"]);

array = [];
m = new Map([[1, "one"], [2, "two"], [3, "three"]]);
m.forEach(function(value, key, map) {
    array.push(value);
    if (key == 3) {
        map.clear();
        map.set(4, "four");
    }
});
assertEqualArrays(array, ["one", "two", "three", "four"]);

array = [];
m = new Map([[1, "one"], [2, "two"], [3, "three"]]);
m.forEach(function(value, key, map) {
    array.push(value);
    if (key == 1) {
        map.delete(1);
    }
    if (key == 2) {
        map.delete(3);
    }
});
assertEqualArrays(array, ["one", "two"]);

array = [];
m = new Map([[1, "one"], [2, "two"], [3, "three"]]);
m.forEach(function(value, key, map) {
    array.push(value);
    if (array.length < 4) {
        map.delete(key);
        map.set(key, key + 3)
    }
});
assertEqualArrays(array, ["one", "two", "three", 4, 5, 6]);




