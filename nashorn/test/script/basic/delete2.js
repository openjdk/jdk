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
 * delete second round test.
 * throws VerifyError!!
 *
 * @test
 * @run 
 */

var a = 10;

var b = {w: 10, x: 20, y: 30, z: 40};

function c() {}

function d() { print("side effect"); }

function e(g) {
    var f = 10;
    
    print(delete f);
    print(f);
    print(delete g);
    print(g);
}

function h(foo) {
  var y = 33;
  print(delete y);
  print(y);
  print(delete foo);
  print(foo);
  print(arguments[0]);
  print(delete arguments[0]);
  print(foo);
  print(arguments[0]);
}

print(delete a);
print(delete b.x);
print(delete b["y"]);
with (b) {
    print(delete z);
    print(delete b);
}
print(delete c);

print(delete this.a);
print(delete 10);
print(delete d());

print(typeof a);
for(i in b) print(i, b[i]);
print(typeof c);

e(10);

h(20);
