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
 * Tests for conversion of JavaScript arrays to Java arrays and the other
 * way round. Also generally useful as a JavaScript-to-Java type conversion 
 * test.
 *
 * @test
 * @run
 */

var x; // used for undefined
var testCount = 0;

function testF(inputValue, type, testFn) {
  var x = Java.to([inputValue], type + "[]")[0];
  if(!testFn(x)) {
    throw ("unexpected value: " + x)
  }
  ++testCount;
}

function test(inputValue, type, expectedValue) {
  testF(inputValue, type, function(x) { return x === expectedValue })
}

function testNaN(inputValue, type) {
  testF(inputValue, type, isNaN)
}

// Those labeled "Correct?" are not clearly correct conversions. Those 
// labeled "TypeError maybe?" could actually throw a TypeError, or only 
// throw a TypeError when in strict mode. 
// The case of ("false", "boolean") => true is particularly amusing.

test(x, "int", 0) // Correct? TypeError maybe?
test(null, "int", 0) // Correct? TypeError maybe?
test(1234, "int", 1234)
test("1234", "int", 1234)
test("1234.49", "int", 1234)
test("1234.51", "int", 1234) // truncates, not rounds
test(true, "int", 1)
test(false, "int", 0)
test("foo", "int", 0) // Correct? TypeError maybe?

test(x, "boolean", false) // Correct? TypeError maybe?
test(null, "boolean", false) // Correct? TypeError maybe?
test(0, "boolean", false)
test(1234, "boolean", true)
test("foo", "boolean", true)
test("", "boolean", false)
test("false", "boolean", true) // Correct? false maybe?

test(x, "java.lang.String", "undefined") // Correct? TypeError maybe?
test(null, "java.lang.String", null)
test(1234, "java.lang.String", "1234")
test(1234.5, "java.lang.String", "1234.5")
test(true, "java.lang.String", "true")
test(false, "java.lang.String", "false")

test(x, "java.lang.Integer", null) // Correct? TypeError maybe?
test(null, "java.lang.Integer", null)
test(1234, "java.lang.Integer", 1234)
test("1234", "java.lang.Integer", 1234)
test("1234.49", "java.lang.Integer", 1234)
test("1234.51", "java.lang.Integer", 1234) // truncates, not rounds
test(true, "java.lang.Integer", 1)
test(false, "java.lang.Integer", 0)
test("foo", "java.lang.Integer", 0) // Correct? TypeError maybe?

test(x, "java.lang.Boolean", null) // Correct? TypeError maybe?
test(null, "java.lang.Boolean", null)
test(0, "java.lang.Boolean", false)
test(1234, "java.lang.Boolean", true)
test("foo", "java.lang.Boolean", true)
test("", "java.lang.Boolean", false)
test("false", "java.lang.Boolean", true) // Correct? false maybe?

testNaN(x, "double")
test(null, "double", 0)
test(1234, "double", 1234)
test("1234", "double", 1234)
test("1234.5", "double", 1234.5)
test(true, "double", 1)
test(false, "double", 0)
testNaN("foo", "double")

testNaN(x, "java.lang.Double")
test(null, "java.lang.Double", null)
test(1234, "java.lang.Double", 1234)
test("1234", "java.lang.Double", 1234)
test("1234.5", "java.lang.Double", 1234.5)
test(true, "java.lang.Double", 1)
test(false, "java.lang.Double", 0)
testNaN("foo", "java.lang.Double")

test({ valueOf: function() { return 42; } }, "int", 42)
test({ valueOf: function() { return "42"; } }, "int", 42)
// If there's no valueOf, toString is used
test({ toString: function() { return "42"; } }, "int", 42)
// For numbers, valueOf takes precedence over toString
test({ valueOf: function() { return "42"; },  toString: function() { return "43"; } }, "int", 42)

test({ toString: function() { return "foo"; } }, "java.lang.String", "foo")
// Yep, even if we have valueOf, toString from prototype takes precedence
test({ valueOf: function() { return 42; } }, "java.lang.String", "[object Object]")
// Converting to string, toString takes precedence over valueOf
test({ valueOf: function() { return "42"; },  toString: function() { return "43"; } }, "java.lang.String", "43")

function assertCantConvert(sourceType, targetType) {
  try {
    Java.to([new Java.type(sourceType)()], targetType + "[]")
    throw "no TypeError encountered"
  } catch(e) {
      if(!(e instanceof TypeError)) {
        throw e;
      }
      ++testCount;
  }
}

// Arbitrary POJOs can't be converted to Java values
assertCantConvert("java.util.BitSet", "int")
assertCantConvert("java.util.BitSet", "double")
assertCantConvert("java.util.BitSet", "long")
assertCantConvert("java.util.BitSet", "boolean")
assertCantConvert("java.util.BitSet", "java.lang.String")
assertCantConvert("java.util.BitSet", "java.lang.Double")
assertCantConvert("java.util.BitSet", "java.lang.Long")

/***************************************************************************
 * Now testing the other way round - Java arrays & collections to JavaScript
 **************************************************************************/

function assert(x) {
  if(!x) {
    throw "Assertion failed"
  }
  ++testCount;
}

var intArray = new (Java.type("int[]"))(3)
intArray[0] = 1234;
intArray[1] = 42;
intArray[2] = 5;
var jsIntArray = Java.from(intArray)
assert(jsIntArray instanceof Array);
assert(jsIntArray[0] === 1234);
assert(jsIntArray[1] === 42);
assert(jsIntArray[2] === 5);

// The arrays are copies, they don't reflect each other
intArray[2] = 6;
assert(jsIntArray[2] === 5);
jsIntArray[2] = 7;
assert(intArray[2] === 6);

var byteArray = new (Java.type("byte[]"))(2)
byteArray[0] = -128;
byteArray[1] = 127;
var jsByteArray = Java.from(byteArray)
assert(jsByteArray instanceof Array);
assert(jsByteArray[0] === -128);
assert(jsByteArray[1] === 127);

var shortArray = new (Java.type("short[]"))(2)
shortArray[0] = -32768;
shortArray[1] = 32767;
var jsShortArray = Java.from(shortArray)
assert(jsShortArray instanceof Array);
assert(jsShortArray[0] === -32768);
assert(jsShortArray[1] === 32767);

var floatArray = new (Java.type("float[]"))(2)
floatArray[0] = java.lang.Float.MIN_VALUE;
floatArray[1] = java.lang.Float.MAX_VALUE;
var jsFloatArray = Java.from(floatArray)
assert(jsFloatArray instanceof Array);
assert(jsFloatArray[0] == java.lang.Float.MIN_VALUE);
assert(jsFloatArray[1] == java.lang.Float.MAX_VALUE);

var charArray = new (Java.type("char[]"))(3)
charArray[0] = "a";
charArray[1] = "b";
charArray[2] = "1";
var jsCharArray = Java.from(charArray)
assert(jsCharArray instanceof Array);
assert(jsCharArray[0] === 97);
assert(jsCharArray[1] === 98);
assert(jsCharArray[2] === 49);

var booleanArray = new (Java.type("boolean[]"))(2)
booleanArray[0] = true;
booleanArray[1] = false;
var jsBooleanArray = Java.from(booleanArray)
assert(jsBooleanArray instanceof Array);
assert(jsBooleanArray[0] === true);
assert(jsBooleanArray[1] === false);

print(testCount + " tests completed ok")
