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

// basic DataView constructor checks.

// check ArrayBufferView property values of DataView instance
function check(dv, buf, offset, length) {
    if (dv.buffer !== buf) {
        fail("DataView.buffer is wrong");
    }

    if (dv.byteOffset != offset) {
        fail("DataView.byteOffset = " + dv.byteOffset + ", expected " + offset);
    }

    if (dv.byteLength != length) {
        fail("DataView.byteLength = " + dv.byteLength + ", expected " + length);
    }
}

var buffer = new ArrayBuffer(12);
check(new DataView(buffer), buffer, 0, 12);
check(new DataView(buffer, 2), buffer, 2, 10);
check(new DataView(buffer, 4, 8), buffer, 4, 8);

// make sure expected error is thrown
function checkError(callback, ErrorType) {
    try {
        callback();
        fail("Should have thrown " + ErrorType.name);
    } catch (e) {
        if (! (e instanceof ErrorType)) {
            print("Expected " + ErrorType.name + " got " + e);
            e.printStackTrace()
        }
    }
}

// non ArrayBuffer as first arg
checkError(function() { new DataView(344) }, TypeError);

// illegal offset/length values
checkError(function() { new DataView(buffer, -1) }, RangeError);
checkError(function() { new DataView(buffer, 15) }, RangeError);
checkError(function() { new DataView(buffer, 1, 32) }, RangeError);
