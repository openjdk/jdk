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
 * new Function test.
 *
 * @test
 * @run 
 */

function MyObject() {
    this.x = 10;
    this.y = "string";
    this.z = function() {
        return true;
    }
};

var a = new MyObject();

print(a.x);
print(a.y);
print(a.z);

var b = new MyObject;

print(b.x);
print(b.y);
print(b.z);

var obj = {
  func: function() {
     print("obj.func called");
  }
};

var x = new obj.func;
print(x);

x = new function f1(){ print("in f1"); return 1;};
print(typeof(x));

