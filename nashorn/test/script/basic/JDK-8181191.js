/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8181191: getUint32 returning Long
 *
 * @test
 * @run
 */


function uint32(x) {
    var buffer = new ArrayBuffer(16);
    var dataview = new DataView(buffer);
    dataview.setUint32(0, x);
    return dataview.getUint32(0);
}

Assert.assertTrue(typeof uint32(0x7f) === 'number');
Assert.assertTrue(typeof uint32(0x80) === 'number');
Assert.assertTrue(typeof uint32(0xffffffff) === 'number');
Assert.assertTrue(typeof uint32(0x100000000) === 'number');

Assert.assertTrue(uint32(0x7f) === 0x7f);
Assert.assertTrue(uint32(0x80) === 0x80);
Assert.assertTrue(uint32(0xffffffff) === 0xffffffff);
Assert.assertTrue(uint32(0x100000000) === 0x0);

Assert.assertTrue(uint32(0x7f) === uint32(0x7f));
Assert.assertTrue(uint32(0x80) === uint32(0x80));
Assert.assertTrue(uint32(0xffffffff) === uint32(0xffffffff));
Assert.assertTrue(uint32(0x100000000) === uint32(0x100000000));
