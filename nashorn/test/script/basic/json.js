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
 * Verify basic JSON parsing using JSON.parse method.
 *
 * @test
 * @run
 */

var obj = JSON.parse('{ "foo" : 44, "bar": "hello",  "arr": [42, 56, 78], "address" : { "city" : "chennai", "country": "India" } }');
print(obj.foo);
print(obj.bar);
print(obj.arr.length);
for (i in obj.arr) {
   print(obj.arr[i]);
}
print(obj.address.city);
print(obj.address.country);

function reviver(name, value) {
   if (name == "") return value;
   print(name + " = " + value);
   return value;
}

var obj2 = JSON.parse('{ "foo" : 44, "bar" : "hello" }', reviver);
print(obj2.foo);
print(obj2.bar);

print(JSON.stringify(obj));
print(JSON.stringify(obj2));

try { 
    JSON.parse('{ "foo" /*comment */ : 44, "bar" : "hello" }', reviver);
    print("Fail!");
} catch (e) {
    if (!(e instanceof SyntaxError)) {
	print("Comments are illegal in JSON. Should throw SyntaxError, not " + e);
    }
}
print("Success!");
