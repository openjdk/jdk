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
 * Verify instanceof operator.
 *
 * @test
 * @run
 */

var b = new Boolean(true);
var s = new String("hello");
var r = /java/;

function MyConstructor(fooval) {
   this.foo = fooval;
}

var c = new MyConstructor("world");

print(b instanceof Boolean);
print(b instanceof String);
print(b instanceof RegExp);
print(b instanceof MyConstructor);
print("------");

print(s instanceof Boolean);
print(s instanceof String);
print(s instanceof RegExp);
print(s instanceof MyConstructor);
print("------");

print(r instanceof Boolean);
print(r instanceof String);
print(r instanceof RegExp);
print(r instanceof MyConstructor);
print("------");

print(c instanceof Boolean);
print(c instanceof String);
print(c instanceof RegExp);
print(c instanceof MyConstructor);
print("------");

var b = new Boolean(true);
print(b instanceof Boolean);
