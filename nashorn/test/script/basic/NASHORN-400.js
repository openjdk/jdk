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

/*
 * NASHORN-400 : java.lang.VerifyError with shadow arguments.
 *
 * @test
 * @run
 */

function test(value, cb) {
    print(cb(value))
}

function x (er) {
    if (!er) {}
    test("nest", function (er) {
        if (er) return er
    });
}
x(5);

function inof(a) {
  if (!a) {}
  return a instanceof Number;
}
inof(42);

function forin(a) {
  if (!a) {}
  for (var i in a);
}
forin(42);

function forinlocal() {
 var I = 42;
 for (var i in I) print(i);
}
forinlocal();

function add(d) {
  if (!d) {}
  print(d + 10);
}
add("3")

