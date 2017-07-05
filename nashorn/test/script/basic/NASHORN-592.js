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
 * NASHORN-592: test all combos of field types and getters and setters
 *
 * @test
 * @run
 */

//fortype undefined
var a;

print(a & 0xff);
print(a >>> 1);
print(a * 2);
print(a + "hej!");

var b;
b = 17;   //set undefined->int

print(b & 0xff);
print(b >>> 1);
print(b * 2);
print(b + "hej!");

var c;
c = 17.4711 //set undefined->double

print(c & 0xff);
print(c >>> 1);
print(c * 2);
print(c + "hej!");

var d; // set undefined->double
d = "Fame and fortune Salamander Yahoo!";

print(d & 0xff);
print(d >>> 1);
print(d * 2);
print(d + "hej!");

// now we have exhausted all getters and undefined->everything setters.


var e = 23; // int to everything setters,
e = 24;     //int to int
print(e);
e = (22222 >>> 1); //int to long;
print(e);
e = 23.23;  //int to double
print(e);
e = 23;     //double to int - still double
print(e);
print(e & 0xff);
e = "Have some pie!" //double to string
print(e);
e = 4711.17;
print(e); //still an object not a double


var f = (23222 >>> 1); // long to everything setters,
f = 34344 >>> 1;     //long to long
print(f);
f = 23; //long to int - still long
print(f);
f = 23.23;  //long to double
print(f);
f = 23;     //double to int - still double
print(f);
print(f & 0xff);
f = "Have some pie!" //double to string
print(f);
f = 4711.17;
print(f); //still an object not a double

var g = 4811.16;
g = 23; //still double
print(g);
g = (222 >>> 1); //still double
print(g);
g = 4711.16; //double->double
print(g);
g = "I like cake!";
print(g);  //object to various
print(g & 0xff);
print(g * 2);
print(g >>> 2);
print(g);

var h = {x:17, y:17.4711, z:"salamander"};
print(h.x);
print(h.y);
print(h.z);
h.x = 4711.17;
h.y = "axolotl";
h.z = "lizard";
print(h.x);
print(h.y);
print(h.z);
