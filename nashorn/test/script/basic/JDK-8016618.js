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
 * JDK-8016618: script mirror object access should be improved
 *
 * @test
 * @option -scripting
 * @option -strict
 * @run
 */

var global = loadWithNewGlobal({
    name: "code",
    script: <<EOF
var x = 33;

function func(x, y) {
    print('func.x = ' + x);
    print('func.x = ' + y)
}; 

var obj = {
    foo: 'hello',
    bar: 42
};

Object.defineProperty(obj, "bar",
    { enumerable: false, writable: false });

// return global
this;
EOF
});

// load on mirror with local object as argument
global.load({ 
    name: "code",
    script: "print('x = ' + x)"
});

function f() {
    // mirror function called with local arguments
    global.func.apply(obj, arguments);
}

f(23, "hello");

var fObject = global.eval("Object");

// instanceof on mirrors
print("global instanceof fObject? " + (global instanceof fObject));

// Object API on mirrors

var desc = Object.getOwnPropertyDescriptor(global, "x");
print("x is wriable ? " + desc.writable);
print("x value = " + desc.value);

var proto = Object.getPrototypeOf(global);
print("global's __proto__ " + proto);

var obj = global.obj;
var keys = Object.keys(obj);
print("Object.keys on obj");
for (var i in keys) {
    print(keys[i]);
}

print("Object.getOwnProperties on obj");
var keys = Object.getOwnPropertyNames(obj);
for (var i in keys) {
  print(keys[i]);
}

// mirror array access
var array = global.eval("[334, 55, 65]");
Array.prototype.forEach.call(array, function(elem) {
    print("forEach " + elem)
});

print("reduceRight " + Array.prototype.reduceRight.call(array,
    function(previousValue, currentValue, index, array) {
        print("reduceRight cur value " + currentValue);
        return previousValue + currentValue;
}, 0));

print("reduce " + Array.prototype.reduce.call(array,
    function(previousValue, currentValue, index, array) {
        print("reduce cur value " + currentValue);
        return previousValue + currentValue;
}, 0));

print("forEach");
Array.prototype.forEach.call(array, function(o) {
   print(o);
});

print("Array.isArray(array)? " + Array.isArray(array));

// try to write to a non-writable property of mirror
try {
   obj.bar = 33;
} catch (e) {
   print(e);
}

// mirror function called with local callback
print("forEach on mirror");
array.forEach(function(toto) {
    print(toto);
});
