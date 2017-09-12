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

// WeakMap constructor and prototype

var desc = Object.getOwnPropertyDescriptor(this, "WeakMap");
Assert.assertEquals(desc.writable, true);
Assert.assertEquals(desc.configurable, true);
Assert.assertEquals(desc.enumerable, false);

Assert.assertTrue(Object.getPrototypeOf(new WeakMap()) === WeakMap.prototype);
Assert.assertTrue(Object.getPrototypeOf(WeakMap.prototype) === Object.prototype);
Assert.assertTrue(Object.getPrototypeOf(new WeakMap("")) === WeakMap.prototype);
Assert.assertTrue(Object.getPrototypeOf(new WeakMap([])) === WeakMap.prototype);

assertThrows("WeakMap()", TypeError);
assertThrows("new WeakMap(3)", TypeError);
assertThrows("new WeakMap({})", TypeError);
assertThrows("new WeakMap([['a', {}]])", TypeError);
assertThrows("new WeakMap([[3, {}]])", TypeError);
assertThrows("new WeakMap([[true, {}]])", TypeError);
assertThrows("new WeakMap([[Symbol.iterator, {}]])", TypeError);

assertThrows("WeakMap.prototype.set.apply({}, [{}, {}])", TypeError);
assertThrows("WeakMap.prototype.has.apply(3, [{}])", TypeError);
assertThrows("WeakMap.prototype.delete.apply('', [3])", TypeError);

// WeakMap methods

let values = [[{}, 1], [{}, 2], [{}, 3]];
let m = new WeakMap(values);

for (let i = 0; i < values.length; i++) {
    Assert.assertTrue(m.has(values[i][0]) === true);
    Assert.assertTrue(m.get(values[i][0]) === values[i][1]);
    Assert.assertTrue(m.delete(values[i][0]) === true);
    Assert.assertTrue(m.has(values[i][0]) === false);
}

values.forEach(function(v) {
    Assert.assertTrue(m.set(v[0], v[1]) === m);
});

for (let i = 0; i < values.length; i++) {
    Assert.assertTrue(m.has(values[i][0]) === true);
    Assert.assertTrue(m.get(values[i][0]) === values[i][1]);
    Assert.assertTrue(m.delete(values[i][0]) === true);
    Assert.assertTrue(m.has(values[i][0]) === false);
}

// Primitive keys

assertThrows("m.set('a', {})", TypeError);
assertThrows("m.set(3, {})", TypeError);
assertThrows("m.set(false, {})", TypeError);
assertThrows("m.set(Symbol.iterator, {})", TypeError);

Assert.assertTrue(m.has('a') === false);
Assert.assertTrue(m.delete(3) === false);
Assert.assertTrue(m.get(Symbol.iterator) === undefined);
Assert.assertTrue(m.get(true) === undefined);
