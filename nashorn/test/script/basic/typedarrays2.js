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
 * Typed array test 2
 *
 * @test
 * @run
 */

var b = new ArrayBuffer(8);

var v1 = new Int32Array(b);
var v2 = new Uint8Array(b, 2);
var v3 = new Int16Array(b, 2, 2);

v3[1000] = 17;
v3.apa = 17;
print(v3[1000]);
var arr = [1,2,3];
arr.gegg = 17;
print(arr.gegg);
var v4 = new Int32Array(arr);
print(v4.gegg);

v2[0] = 0xff;
v2[1] = 0xff;
if (v2[1] != 0xff) {
    print("Error 1: " + v2[1]);
}
if (v3[0] != -1) {
    print("Error 2: " + v3[0]);
}

print("Done");

