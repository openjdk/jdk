/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8036743: need ArrayBuffer constructor with specified data
 *
 * @test
 * @run
 */

var ByteArray = Java.type("byte[]");
var ByteBuffer = Java.type("java.nio.ByteBuffer");

var ba = new ByteArray(4);
// use constructor that accepts ByteBuffer as first arg.
var abuf = new ArrayBuffer(ByteBuffer.wrap(ba));
print("abuf.byteLength = " + abuf.byteLength);
var view = new DataView(abuf);

function setAndPrint(value, endian) {
    view.setInt32(0, value, endian);
    print("Little endian? " + endian);
    print("view[0] = " + view.getInt32(0, endian));
    print("ba[0] = " + ba[0]);
    print("ba[1] = " + ba[1]);
    print("ba[2] = " + ba[2]);
    print("ba[3] = " + ba[3]);
}

setAndPrint(42, true);
setAndPrint(42, false);
setAndPrint(java.lang.Byte.MAX_VALUE, true);
setAndPrint(java.lang.Byte.MAX_VALUE, false);
setAndPrint(java.lang.Short.MAX_VALUE, true);
setAndPrint(java.lang.Short.MAX_VALUE, false);
setAndPrint(java.lang.Integer.MAX_VALUE, true);
setAndPrint(java.lang.Integer.MAX_VALUE, false);
