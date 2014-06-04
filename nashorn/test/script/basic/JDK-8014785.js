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
 * JDK-8014785: Ability to extend global instance by binding properties of another object
 *
 * @test
 * @run
 */

var obj = { x: 34, y: 100 };
var foo = {}

// bind properties of "obj" to "foo" obj
Object.bindProperties(foo, obj);

// now we can access/write on foo properties
print("foo.x = " + foo.x); // prints obj.x which is 34

// update obj.x via foo.x
foo.x = "hello";
print("obj.x = " + obj.x); // prints "hello" now

obj.x = 42;   // foo.x also becomes 42
print("obj.x = " + obj.x); // prints 42
print("foo.x = " + foo.x); // prints 42

// now bind a mirror object to an object
var obj = loadWithNewGlobal({
  name: "test",
  script: "obj = { x: 33, y: 'hello' }"
});

Object.bindProperties(this, obj);
print("x = " + x); // prints 33
print("y = " + y); // prints "hello"

x = Math.PI;               // changes obj.x to Math.PI
print("obj.x = " +obj.x);  // prints Math.PI

obj.y = 32;
print("y = " + y);  // should print 32
