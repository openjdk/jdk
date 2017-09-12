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
 * Basic object literal tests. Also check few Object.prototype and Object
 * constructor functions.
 *
 * @test
 * @run
 */
var person = { name: "sundar" };
print(person.name);
person.name = "Sundararajan";
print(person.name);

var obj = { foo: 3, bar: 44 };

print("own properties of 'obj':");
var names = Object.getOwnPropertyNames(obj);
// get only own property names
for (i in names) {
   print(i + " -> " + names[i]);
}

print("has own 'foo'? " + obj.hasOwnProperty('foo'));
print("has own 'xyz'? " + obj.hasOwnProperty('xyz'));

print("'foo' enumerable? " + obj.propertyIsEnumerable('foo'));
print("'bar' enumerable? " + obj.propertyIsEnumerable('bar'));

obj = {
    foo: 44,
    bar: "orcl",
    func: function() { print("myfunc"); },
    get abc() { return "abc"; },
    set xyz(val) { print(val); },
    get hey() { return "hey"; },
    set hey(val) { print(val); }
}

// get property descriptor for each property and check it
for (i in obj) {
    var desc = Object.getOwnPropertyDescriptor(obj, i);
    print(i + " is writable? " + desc.writable);
    print(i + " is configurable? " + desc.configurable);
    print(i + " is enumerable? " + desc.enumerable);
    print(i + "'s value = " + desc.value);
    print(i + "'s get = " + desc.get);
    print(i + "'s set = " + desc.set);
}

print(Object.getOwnPropertyDescriptor(obj, "non-existent"));
