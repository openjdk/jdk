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

// set/get endianess checks

var buffer = new ArrayBuffer(4);
var dv = new DataView(buffer);

// write (default) big endian, read big/little endian
dv.setUint16(0, 0xABCD);
Assert.assertEquals(dv.getUint16(0), 0xABCD);
Assert.assertEquals(dv.getUint16(0, false), 0xABCD);
Assert.assertEquals(dv.getUint16(0, true), 0xCDAB);

// write little endian, read big/little endian
dv.setUint16(0, 0xABCD, true);
Assert.assertEquals(dv.getUint16(0), 0xCDAB);
Assert.assertEquals(dv.getUint16(0, false), 0xCDAB);
Assert.assertEquals(dv.getUint16(0, true), 0xABCD);

// write explicit big endian, read big/little endian
dv.setUint16(0, 0xABCD, false);
Assert.assertEquals(dv.getUint16(0), 0xABCD);
Assert.assertEquals(dv.getUint16(0, false), 0xABCD);
Assert.assertEquals(dv.getUint16(0, true), 0xCDAB);

// write (default) big endian, read big/little endian
dv.setUint32(0, 0xABCDEF89);
Assert.assertEquals(dv.getUint32(0), 0xABCDEF89);
Assert.assertEquals(dv.getUint32(0, false), 0xABCDEF89);
Assert.assertEquals(dv.getUint32(0, true), 0x89EFCDAB);

// write little endian, read big/little endian
dv.setUint32(0, 0xABCDEF89, true);
Assert.assertEquals(dv.getUint32(0), 0x89EFCDAB);
Assert.assertEquals(dv.getUint32(0, false), 0x89EFCDAB);
Assert.assertEquals(dv.getUint32(0, true), 0xABCDEF89);

// write explicit big endian, read big/little endian
dv.setUint32(0, 0xABCDEF89, false);
Assert.assertEquals(dv.getUint32(0), 0xABCDEF89);
Assert.assertEquals(dv.getUint32(0, false), 0xABCDEF89);
Assert.assertEquals(dv.getUint32(0, true), 0x89EFCDAB);
