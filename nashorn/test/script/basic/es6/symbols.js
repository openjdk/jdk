/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8141702: Add support for Symbol property keys
 *
 * @test
 * @run
 * @option --language=es6
 */

Assert.assertTrue(typeof Symbol === 'function');
Assert.assertTrue(typeof Symbol() === 'symbol');

Assert.assertTrue(Symbol().toString() === 'Symbol()');
Assert.assertTrue(Symbol('foo').toString() === 'Symbol(foo)');
Assert.assertTrue(Symbol(1).toString() === 'Symbol(1)');
Assert.assertTrue(Symbol(true).toString() === 'Symbol(true)');
Assert.assertTrue(Symbol([1, 2, 3]).toString() === 'Symbol(1,2,3)');
Assert.assertTrue(Symbol(null).toString() === 'Symbol(null)');
Assert.assertTrue(Symbol(undefined).toString() === 'Symbol()');

var s1 = Symbol();
var s2 = Symbol("s2");
Assert.assertFalse(s1 instanceof Symbol); // not an object

var obj = {};
obj['foo'] = 'foo';
obj[s1] = s1;
obj['bar'] = 'bar';
obj[1] = 1;
obj[s2] = s2;

Assert.assertTrue(obj['foo'] === 'foo');
Assert.assertTrue(obj[s1] === s1);
Assert.assertTrue(obj['bar'] === 'bar');
Assert.assertTrue(obj[1] === 1);
Assert.assertTrue(obj[s2] === s2);

var expectedNames = ['1', 'foo', 'bar'];
var expectedSymbols = [s1, s2];
var actualNames = Object.getOwnPropertyNames(obj);
var actualSymbols = Object.getOwnPropertySymbols(obj);
Assert.assertTrue(expectedNames.length == actualNames.length);
Assert.assertTrue(expectedSymbols.length == actualSymbols.length);

for (var key in expectedNames) {
    Assert.assertTrue(expectedNames[key] === actualNames[key]);
}
for (var key in expectedSymbols) {
    Assert.assertTrue(expectedSymbols[key] === actualSymbols[key]);
}

// Delete
Assert.assertTrue(delete obj[s1]);
Assert.assertTrue(Object.getOwnPropertySymbols(obj).length === 1);
Assert.assertTrue(Object.getOwnPropertySymbols(obj)[0] === s2);

// Object.defineProperty
Object.defineProperty(obj, s1, {value : 'hello'});
Assert.assertTrue(obj[s1] === 'hello');
actualSymbols = Object.getOwnPropertySymbols(obj);
Assert.assertTrue(Object.getOwnPropertySymbols(obj).length === 2);
Assert.assertTrue(Object.getOwnPropertySymbols(obj)[1] === s1);

// Symbol called as constructor
try {
    new Symbol();
    Assert.fail("Symbol invoked as constructor");
} catch (e) {
    if (e.name !== "TypeError" || e.message !== "Symbol is not a constructor.") {
        Assert.fail("Unexpected error: " + e);
    }
}

// Implicit conversion to string or number should throw
try {
    ' ' + s1;
    Assert.fail("Symbol converted to string");
} catch (e) {
    if (e.name !== "TypeError" || e.message !== "Can not convert Symbol value to string.") {
        Assert.fail("Unexpected error: " + e);
    }
}

try {
    4 * s1;
    Assert.fail("Symbol converted to number");
} catch (e) {
    if (e.name !== "TypeError" || e.message !== "Can not convert Symbol value to number.") {
        Assert.fail("Unexpected error: " + e);
    }
}

// Symbol.for and Symbol.keyFor

var uncached = Symbol('foo');
var cached = Symbol.for('foo');

Assert.assertTrue(uncached !== cached);
Assert.assertTrue(Symbol.keyFor(uncached) === undefined);
Assert.assertTrue(Symbol.keyFor(cached) === 'foo');
Assert.assertTrue(cached === Symbol.for('foo'));
Assert.assertTrue(cached === Symbol.for('f' + 'oo'));

// Object wrapper

var o = Object(s1);
obj = {};
obj[s1] = "s1";
Assert.assertTrue(o == s1);
Assert.assertTrue(o !== s1);
Assert.assertTrue(typeof o === 'object');
Assert.assertTrue(o instanceof Symbol);
Assert.assertTrue(obj[o] == 's1');
Assert.assertTrue(o in obj);

// various non-strict comparisons that should fail

Assert.assertFalse(0 == Symbol());
Assert.assertFalse(1 == Symbol(1));
Assert.assertFalse(null == Symbol());
Assert.assertFalse(undefined == Symbol);
Assert.assertFalse('Symbol()' == Symbol());
Assert.assertFalse('Symbol(foo)' == Symbol('foo'));

