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
 * Check Object.defineProperty, Object.defineProperties and Object.create.
 * FIXME: yet to checks for property attribute create/modifications.
 *
 * @test
 * @run
 */

// create an object
var obj = {};
// data property
Object.defineProperty(obj, "foo", { value: "hello" });
// accessor property
Object.defineProperty(obj, "bar", { get: function() { return "bar" } });

print("obj.foo = " + obj.foo);
print("obj.bar = " + obj.bar);

// define multiple properties at one go.
Object.defineProperties(obj,
  {
     xyz: { value: 44 },
     abc: { get: function() { print("get abc"); return "abc"; } }
  }
); 

print("obj.xyz = " + obj.xyz);
print("obj.abc = " + obj.abc);

function MyConstructor() {}
var obj2 = Object.create(MyConstructor.prototype);
print("obj2 in MyConstructor instance? " + (obj2 instanceof MyConstructor));

var obj3 = Object.create(Object.prototype, 
  {
     xyz: { value: 44 }
  }
);

print("obj3 is an Object? " + (obj3 instanceof Object));
print(obj3.xyz);
