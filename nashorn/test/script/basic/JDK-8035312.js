/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8035312 push to frozen array must not increase length property
 *
 * @test
 * @run
 * @fork
 * @option -Dnashorn.debug=true
 */

function printArrayDataClass(x) {
    if (typeof Debug !== 'undefined') {
	print(Debug.getArrayDataClass(x));
    }
}

function gpush(x, elem) {
    try {
	print("Pushing " + elem + " to " + x);
	x.push(elem);
    } catch (e) {
	print("caught error" + e);
    }
    print("\tarray is now [" + x + "] length is = " + x.length);
    print();
    printArrayDataClass(x);
}

function gpop(x) {
    try {
	print("Popping from " + x);
	x.pop();
    } catch (e) {
	if (!(e instanceof TypeError)) {
	    print("e of wrong type " + e);
	}
    }
    print("\tarray is now [" + x + "] length is = " + x.length);
    print();
    printArrayDataClass(x);
}

function checkArray(x) {
    print();
    print(">>> Push test");

    var olen = x.length;
    gpush(x, 0);

    print("x.length === " + x.length + " (should be " + olen + ")");
    print("x[3] === " + x[3] + " (should be 0)");
    print("x[4] === " + x[4] + " (should be undefined)");

    print();
    print(">>> Pop test");
    gpop(x);
    gpop(x);
    print("x.length === " + x.length + " (should be " + olen + ")");
    print("x === " + x);

    for (var i = 0 ; i < 5; i++) {
	gpop(x);
    }

    print("x.length === " + x.length + " (should be " + olen + ")");
    print("x === " + x);
}

print("*** Freezing");
var frozen = [1,2,3];
Object.freeze(frozen);
checkArray(frozen);
printArrayDataClass(frozen);

//so far so good

print();
print("*** Other length not writable issues");
var lengthNotWritable = [1,2,3];
Object.defineProperty(lengthNotWritable, "length", { writable: false });
checkArray(lengthNotWritable);
printArrayDataClass(lengthNotWritable);

function set(array, from, to, stride) {
    //add three elements
    for (var i = from; i < to; i+=stride) {
	try {
	    print("Writing " + i);
	    array[i] = i;
	    printArrayDataClass(array);
	} catch (e) {
	    print(e instanceof TypeError);
	}
    }
}

//define empty array with non writable length
var arr = [1];
Object.defineProperty(arr, "length", { writable: false });

var olen2 = arr.length;

set(arr, 0, 3, 1);

if (arr.length != olen2) {
    throw new ("error: " +  arr.length + " != " + olen2);
}

print();
print("array writing 0-3, with 1 stride, array = " + arr);
print("length = " + arr.length + ", but elements are: " + arr[0] + " " + arr[1] + " " + arr[2]);
print();

//do the same but sparse/deleted range
var arr2 = [1];
Object.defineProperty(arr2, "length", { writable: false });

print("initial length = " + arr2.length);
var olen3 = arr2.length;

set(arr2, 0, 30, 3);

if (arr2.length != olen3) {
    throw new ("error: " +  arr2.length + " != " + olen3);
}

print();
var larger = 20;
print("array writing 0-" + larger + ", with 3 stride, array = " + arr2);
print("length = " + arr2.length + ", but elements are: " + arr2[0] + " " + arr2[1] + " " + arr2[2]);

for (var i = 0; i < larger; i++) {
    if (arr2[i] === undefined) {
	continue;
    }
    print(arr2[i] + " has length " + arr2.length);
}

print();
var elem = 0x7fffffff - 10;
printArrayDataClass(arr2);
print("adding a new element high up in the array");
print("length before element was added " + arr2.length);
print("putting sparse at " + elem);
arr2[elem] = "sparse";
print("length after element was added " + arr2.length + " should be the same");
printArrayDataClass(arr2);

print();
print("Printing arr2 - this will fail if length is > 28 and it is " + arr2.length);
print("arr2 = [" + arr2 + "]");
print("new length that should not be writable = " + arr2.length);
print(arr2[elem] === "sparse");
print(arr2[elem]);
for (var i = 0; i < larger; i++) {
    print(arr2[i]);
}
for (var key in arr2) {
    print(key + ":" + arr2[key]);
}

//issues reported by sundar - generic setter doesn't go through push/pop bulkable

function sundarExample2(arr, _writable) {
    print("Checking if push works for bulkable non bulkable arrays - Setting length property not allowed");
    arr[0] = "bar";
    print(arr.length + " should be 1"); // should be 1
    print(arr[0] + " should be bar");
    print("["+ arr + "] should be [bar]");

    //    Object.defineProperty(arr, "length", { configurable: _writable });
    Object.defineProperty(arr, "length", { writable: _writable });
    arr[1] = "baz";

    if (_writable) {
	print(arr.length + " should be 2");
	print(arr[0] + " should be bar");
	print(arr[1] + " should be baz");
	print("["+ arr + "] should be [bar,baz]");
    } else {
	print(arr.length + " should STILL be 1");
	print(arr[0] + " should be bar");
	print(arr[1] + " should be baz");
	print("["+ arr + "] should be [bar]");
    }
}

var newArr1 = [];
sundarExample2(newArr1, false);
print();
try {
    sundarExample2(newArr1, true);
    print("should not get here");
} catch (e) {
    if (!(e instanceof TypeError)) {
	print("Wrong exception");
    }
    print("got TypeError when redefining length, as expected")
}
print();

sundarExample2([], true);
print("Done");
