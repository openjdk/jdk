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
 * JDK-8144020: Remove long as an internal numeric type
 *
 * @test
 * @run
 */

var LongProvider = Java.type("jdk.nashorn.test.models.LongProvider");
var Long = Java.type("java.lang.Long");
var LongClass = Long.class;
var Integer = Java.type("java.lang.Integer");
var Double = Java.type("java.lang.Double");

var INT      = "3";
var DOUBLE   = "5.5";
var MAX_LONG = "9223372036854775807";
var MIN_LONG = "-9223372036854775808";
var BIG_LONG = "281474976710655"; // can be represented as double
var NEG_LONG = "-281474976710656"; // can be represented as double
var SMALL_LONG = "13";

// Make sure we can pass longs from and to Java without losing precision
LongProvider.checkLong(LongProvider.getLong(MAX_LONG), MAX_LONG);
LongProvider.checkLong(LongProvider.getLong(MIN_LONG), MIN_LONG);
LongProvider.checkLong(LongProvider.getLong(BIG_LONG), BIG_LONG);
LongProvider.checkLong(LongProvider.getLong(NEG_LONG), NEG_LONG);
LongProvider.checkLong(LongProvider.getLong(SMALL_LONG), SMALL_LONG);

// a polymorphic function that can return various number types
function getNumber(str) {
    switch (str) {
        case INT:    return +INT;
        case DOUBLE: return +DOUBLE;
        default:     return Long.parseLong(str);
    }
}

function compareValue(n, str) {
    switch (str) {
        case INT:    return Integer.compare(n, Integer.parseInt(str) == 0);
        case DOUBLE: return Double.compare(n, Double.parseDouble(str) == 0);
        default:     return Long.compare(n, Long.parseLong(str) == 0);
    }
}

// Call a a function with a sequence of values. The purpose of this is that we can handle
// longs without losing precision in the presence of optimistic deoptimization, cached callsites, etc.
function testSequence(fn, values) {
    for (var i in values) {
        fn(values[i]);
    }
}

// We need to use "fresh" (unlinked and un-deoptimized) functions for each of the test runs.
testSequence(function(str) {
    var n = getNumber(str);
    Assert.assertTrue(compareValue(n, str));
}, [INT, BIG_LONG, MIN_LONG]);

testSequence(function(str) {
    var n = getNumber(str);
    Assert.assertTrue(compareValue(n, str));
}, [INT, MAX_LONG]);

testSequence(function(str) {
    var n = getNumber(str);
    Assert.assertTrue(compareValue(n, str));
}, [INT, DOUBLE, NEG_LONG]);

testSequence(function(str) {
    var n = getNumber(str);
    Assert.assertTrue(compareValue(n, str));
}, [DOUBLE, MAX_LONG]);

testSequence(function(str) {
    var n = getNumber(str);
    Assert.assertTrue(compareValue(n, str));
}, [DOUBLE, SMALL_LONG, MAX_LONG]);

testSequence(function(str) {
    var n = getNumber(str);
    Assert.assertTrue(compareValue(n, str));
}, [INT, DOUBLE, NEG_LONG, MAX_LONG]);

testSequence(function(str) {
    var n = getNumber(str);
    Assert.assertTrue(compareValue(n, str));
}, [DOUBLE, MAX_LONG, DOUBLE, INT]);

// Make sure long arrays make it through Java.from and Java.to without losing precision
var longArrayType = Java.type("long[]");
for (var i = 0; i < 3; i++) {
    LongProvider.checkLongArray(Java.to(Java.from(LongProvider.getLongArray(i)), longArrayType), i);
}

l = Long.parseLong(BIG_LONG);
Assert.assertTrue(l >>> 8 === 0xffffff);
Assert.assertTrue(l << 8 === -0x100);
Assert.assertTrue(l + 1 === 0x1000000000000);
Assert.assertTrue(l - 1 === 0xfffffffffffe);

Assert.assertEquals(LongProvider.getLong(MAX_LONG).getClass(), LongClass);
Assert.assertEquals(LongProvider.getLong(MIN_LONG).getClass(), LongClass);
