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
 * JDK-8006529 : Constructor functions that don't need callees must not get
 * linked with a MethodHandle boud to a specific function instance.
 * @test
 * @run
 */

Object.defineProperty(Object.prototype, "extends", {
  value: function (superConstructor) {
    function ProtoBridge() { }
    ProtoBridge.prototype = superConstructor.prototype;
    this.prototype = new ProtoBridge();
    this.superConstructor = superConstructor;
  }
});

function A() {
}
A.prototype.f = function () {
  this.g();
}

function B() {
  B.superConstructor.call(this);
  this.f();
}
B.extends(A);

B.prototype.g = function () {
  print("It worked!")
}

function C() {
  C.superConstructor.call(this);
}
C.extends(B);

var x = [B, C]
for(var i in x) {
  print("Doing " + i)
  new x[i]()
}
