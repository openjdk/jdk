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
 * Basic Array tests.
 *
 * @test
 * @run
 */

var arr = new Array(3);
print(arr.length);

print("isArray.length = " + Array.isArray.length);
print(Array.isArray(44));
print(Array.isArray([44]));

function even(num) {
    return (num % 2) == 0;
}

print("join.length = " + Array.prototype.join.length);
print(["javascript", "is", "great"].join("<->"));

var arr = [4, 56, 5];
print("every.length = " + Array.prototype.every.length);
print(arr.toString() + " every even? = " + arr.every(even));
arr = [4, 56, 688];
print(arr.toString() + " every even? = " + arr.every(even));

print("some.length = " + Array.prototype.some.length);
arr = [4, 56, 5];
print(arr.toString() + " some even? = " + arr.some(even));
arr = [3, 5, 17];
print(arr.toString() + " some even? = " + arr.some(even));

print("forEach.length = " + Array.prototype.forEach.length);
arr = [ "java", "javascript", "jython", "jruby"];
arr.forEach(function(val, idx, obj) {
    print(obj.toString() + "[" + idx + "] is " + val);
});

print(arr.map(function(val) { return val.toUpperCase(); }));
print("shifted is " + arr.shift() + ", remaining is " + arr.toString() + ", length is " + arr.length);

arr = [ "c++", "java", "javascript", "objective c" ];
print(arr.filter(function(val) { return val.charAt(0) == 'j'; }));

print([3, 66, 2, 44].reduce(function (acc, e) { return acc + e; }));
print([1, 2, 3, 4, 5].reduce(function (acc, e) { return acc * e; }));

print(arr.reduce(
    function(acc, e) { return acc + " " + e; }
));

print(["javascript", "from", "world", "hello"].reduceRight(
    function(acc, x) { return acc + " " + x; }
));

var langs = ["java", "javascript", "jython", "jruby", "c"];
print("indexOf.length = " + Array.prototype.indexOf.length);
print("indexOf('java') = " + langs.indexOf("java"));
print("indexOf('javascript') = " + langs.indexOf("javascript"));
print("indexOf('javascript', 3) = " + langs.indexOf("javascript", 3));
print("indexOf('c++') = " + langs.indexOf("c++"));
print("[].indexOf('any') = " + [].indexOf("any"));

langs = ["java", "javascript", "jython", "jruby", "java", "jython", "c"];
print("lastIndexOf.length = " + Array.prototype.lastIndexOf.length);
print("lastIndexOf('java') = " + langs.lastIndexOf("java"));
print("lastIndexOf('jython') = " + langs.lastIndexOf("jython"));
print("lastIndexOf('c') = " + langs.lastIndexOf("c"));
print("lastIndexOf('c++') = " + langs.lastIndexOf("c++"));
print("[].lastIndexOf('any') = " + [].lastIndexOf("any"));

print("concat.length = " + Array.prototype.concat.length);
print(["foo", "bar"].concat(["x", "y"], 34, "sss", [3, 4, 2]));


// Check various array length arguments to constructor

function expectRangeError(length) {
    try {
        var arr = new Array(length);
        print("range error expected for " + length);
    } catch (e) {
        if (! (e instanceof RangeError)) {
            print("range error expected for " + length);
        }
    }
}

expectRangeError(NaN);
expectRangeError(Infinity);
expectRangeError(-Infinity);
expectRangeError(-10);

var arr = new Array("10");
if (arr.length != 1 && arr[0] != '10') {
    throw new Error("expected length 1 array");
}

arr = new Array(new Number(34));
if (arr.length != 1 && arr[0] != new Number(34)) {
    throw new Error("expected length 1 array");
}

arr = new Array(15);
if (arr.length != 15) {
    throw new Error("expected length 15 array");
}

print("Array.length = " + Array.length);

print([NaN,NaN,NaN]);

// check setting array's length
arr = [3,2,1];
arr.length = 1;
print(arr);
print(arr.length);

// test typeof array
var numberArray = [];
numberArray[0] = 1;
print(typeof numberArray[0]);

print(numberArray.toLocaleString());

// Array functions on non-array objects

print(Array.prototype.join.call(new java.lang.Object()));
print(Array.prototype.concat.call("hello", "world"));
print(Array.prototype.map.call("hello", function() {}));
print(Array.prototype.reduce.call("hello", function() {}));
print(Array.prototype.toString.call(new java.lang.Object()));
print(Array.prototype.toLocaleString.call(new java.lang.Object()));
print(Array.prototype.reduceRight.call(new java.lang.Object(),
      function() {}, 33));

