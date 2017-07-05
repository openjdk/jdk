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

// WeakSet constructor and prototype

var desc = Object.getOwnPropertyDescriptor(this, "WeakSet");
Assert.assertEquals(desc.writable, true);
Assert.assertEquals(desc.configurable, true);
Assert.assertEquals(desc.enumerable, false);

Assert.assertTrue(Object.getPrototypeOf(new WeakSet()) === WeakSet.prototype);
Assert.assertTrue(Object.getPrototypeOf(WeakSet.prototype) === Object.prototype);
Assert.assertTrue(Object.getPrototypeOf(new WeakSet("")) === WeakSet.prototype);
Assert.assertTrue(Object.getPrototypeOf(new WeakSet([])) === WeakSet.prototype);

assertThrows("WeakSet()", TypeError);
assertThrows("new WeakSet(3)", TypeError);
assertThrows("new WeakSet({})", TypeError);
assertThrows("new WeakSet(['a'])", TypeError);
assertThrows("new WeakSet([3])", TypeError);
assertThrows("new WeakSet([true])", TypeError);
assertThrows("new WeakSet([Symbol.iterator])", TypeError);

assertThrows("WeakSet.prototype.add.apply({}, [''])", TypeError);
assertThrows("WeakSet.prototype.has.apply(3, [''])", TypeError);
assertThrows("WeakSet.prototype.delete.apply('', [3])", TypeError);

// WeakSet methods

let values = [{}, {}, {}];
let s = new WeakSet(values);

for (let i = 0; i < values.length; i++) {
    Assert.assertTrue(s.has(values[i]) === true);
    Assert.assertTrue(s.delete(values[i]) === true);
    Assert.assertTrue(s.has(values[i]) === false);
}

values.forEach(function(v) {
    Assert.assertTrue(s.add(v) === s);
});

for (let i = 0; i < values.length; i++) {
    Assert.assertTrue(s.has(values[i]) === true);
    Assert.assertTrue(s.delete(values[i]) === true);
    Assert.assertTrue(s.has(values[i]) === false);
}

// Primitive keys

assertThrows("s.add('a')", TypeError);
assertThrows("s.add(3)", TypeError);
assertThrows("s.add(false)", TypeError);
assertThrows("s.add(Symbol.iterator)", TypeError);

Assert.assertTrue(s.has('a') === false);
Assert.assertTrue(s.delete(3) === false);
Assert.assertTrue(s.has(Symbol.iterator) === false);
Assert.assertTrue(s.delete(true) === false);


