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
 * Test the functionality of Function.prototype.bind.
 *
 * @test
 * @run
 */

function f(a, b) {
    print("f: this=" + this + ", a=" + a + ", b=" + b);
}
function v(a, b) {
    print("v: this=" + this + ", a=" + a + ", b=" + b + ", c=" + arguments[2]);
}

(f.bind(null))();
(v.bind(null))();

var boundThis = "boundThis";
(f.bind(boundThis))();
(v.bind(boundThis))();

(f.bind(boundThis))("a0");
(v.bind(boundThis))("a1");

(f.bind(boundThis, "a2"))();
(v.bind(boundThis, "a3"))();

(f.bind(boundThis, "a4"))("b4");
(v.bind(boundThis, "a5"))("b5");

(f.bind(boundThis, "a6", "b6"))();
(v.bind(boundThis, "a7", "b7"))();

(f.bind(boundThis, "a8", "b8"))("c8"); // invoking with extra args after all were bound!
(v.bind(boundThis, "a9", "b9"))("c9");

(f.bind(boundThis, "a10", "b10", "c10"))(); // binding more args than it receives!
(v.bind(boundThis, "a11", "b11", "c11"))();

print("\nTest constructors\n");

new (f.bind(boundThis))();
new (v.bind(boundThis))();

new (f.bind(boundThis))("a0");
new (v.bind(boundThis))("a1");

new (f.bind(boundThis, "a2"))();
new (v.bind(boundThis, "a3"))();

new (f.bind(boundThis, "a4"))("b4");
new (v.bind(boundThis, "a5"))("b5");

new (f.bind(boundThis, "a6", "b6"))();
new (v.bind(boundThis, "a7", "b7"))();

new (f.bind(boundThis, "a8", "b8"))("c8");
new (v.bind(boundThis, "a9", "b9"))("c9");

new (f.bind(boundThis, "a10", "b10", "c10"))();
new (v.bind(boundThis, "a11", "b11", "c11"))();

print("\nTest double binding\n");

(f.bind(boundThis).bind("thisIsIgnored"))();
new (f.bind("thisIsIgnored").bind("thisIsIgnoredToo"))();
new (f.bind("thisIsIgnored", "a12").bind("thisIsIgnoredToo"))();

(v.bind(boundThis).bind("thisIsIgnored"))();
new (v.bind("thisIsIgnored").bind("thisIsIgnoredToo"))();
new (v.bind("thisIsIgnored", "a13").bind("thisIsIgnoredToo"))();
