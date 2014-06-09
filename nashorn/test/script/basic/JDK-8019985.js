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
 * JDK-8019985: Date.parse("2000-01-01T00:00:00.Z") should return NaN
 *
 * @test
 * @run
 */

function testFail(str) {
    if (!isNaN(Date.parse(str))) {
        throw new Error("Parsed invalid date string: " + str);
    }
}

function testOk(str) {
    if (isNaN(Date.parse(str))) {
        throw new Error("Failed to parse valid date string: " + str);
    }
}

testFail("2000-01-01T00:00:00.Z");
testFail("2000-01-01T00:00:Z");
testFail("2000-01-01T00:Z");
testFail("2000-01-01T00Z");
testOk("2000-01-01T00:00:00.000Z");
testOk("2000-01-01T00:00:00.0Z");
testOk("2000-01-01T00:00:00Z");
testOk("2000-01-01T00:00Z");
