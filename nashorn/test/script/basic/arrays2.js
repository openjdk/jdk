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
 * Basic Array tests.
 *
 * @test
 * @run
 */

print("Array.prototype.length = " + Array.prototype.length);
print("Array.prototype.slice.prototype = " + Array.prototype.slice.prototype);
try {
    throw new Array.prototype.slice();
} catch (e) {
    print(e instanceof TypeError);
}

var x = [];
x[true] = 1;
print(x["true"]);

x = [];
x[NaN] = 1;
print(x["NaN"]);

x = [];
x["0"] = 0;
print(x[0]);

x = [];
x[true] = 1;
print(x["true"]);
