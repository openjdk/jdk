/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8015958: DataView constructor is not defined
 *
 * @test
 * @run
 */

// checking get/set of values of various types
// Also basic endianess check.

var Float = Java.type("java.lang.Float");
var Double = Java.type("java.lang.Double");

var DOUBLE_MIN = Double.MIN_VALUE;
var DOUBLE_MIN_NORMAL = Double.MIN_NORMAL;
var FLOAT_MIN = Float.MIN_VALUE;
var FLOAT_MIN_NORMAL = Float.MIN_NORMAL;

var buffer = new ArrayBuffer(12);
var dv = new DataView(buffer);

dv.setInt8(1, 123);
Assert.assertEquals(dv.getInt8(1), 123);
dv.setInt8(1, 123, true);
Assert.assertEquals(dv.getInt8(1, true), 123);

dv.setUint8(1, 255);
Assert.assertEquals(dv.getUint8(1), 255);
dv.setUint8(1, 255, true);
Assert.assertEquals(dv.getUint8(1, true), 255);

dv.setInt16(1, 1234);
Assert.assertEquals(dv.getInt16(1), 1234);
dv.setInt16(1, 1234, true);
Assert.assertEquals(dv.getInt16(1, true), 1234);

dv.setUint16(1, 65535);
Assert.assertEquals(dv.getUint16(1), 65535);
dv.setUint16(1, 65535, true);
Assert.assertEquals(dv.getUint16(1, true), 65535);

dv.setInt32(1, 1234);
Assert.assertEquals(dv.getInt32(1), 1234);
dv.setInt32(1, 1234, true);
Assert.assertEquals(dv.getInt32(1, true), 1234);

dv.setUint32(1, 4294967295);
Assert.assertEquals(dv.getUint32(1), 4294967295);
dv.setUint32(1, 4294967295, true);
Assert.assertEquals(dv.getUint32(1, true), 4294967295);

dv.setFloat64(1, Math.PI);
Assert.assertEquals(dv.getFloat64(1), Math.PI, DOUBLE_MIN);
dv.setFloat64(1, Math.PI, true);
Assert.assertEquals(dv.getFloat64(1, true), Math.PI, DOUBLE_MIN);

dv.setFloat64(1, DOUBLE_MIN_NORMAL);
Assert.assertEquals(dv.getFloat64(1), DOUBLE_MIN_NORMAL, DOUBLE_MIN);
dv.setFloat64(1, DOUBLE_MIN_NORMAL, true);
Assert.assertEquals(dv.getFloat64(1, true), DOUBLE_MIN_NORMAL, DOUBLE_MIN);

dv.setFloat32(1, 1.414);
Assert["assertEquals(float, float, float)"](dv.getFloat32(1), 1.414, FLOAT_MIN);
dv.setFloat32(1, 1.414, true);
Assert["assertEquals(float, float, float)"](dv.getFloat32(1, true), 1.414, FLOAT_MIN);

dv.setFloat32(1, FLOAT_MIN_NORMAL);
Assert["assertEquals(float, float, float)"](dv.getFloat32(1), FLOAT_MIN_NORMAL, FLOAT_MIN);
dv.setFloat32(1, FLOAT_MIN_NORMAL, true);
Assert["assertEquals(float, float, float)"](dv.getFloat32(1, true), FLOAT_MIN_NORMAL, FLOAT_MIN);
