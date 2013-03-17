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
 * JDK-8008298: Add tests to cover specialized versions of Math functions.
 * Excerise all specialized Math functions with various literal values.
 *
 * @test
 * @run
 */

if (Math.abs(-88) != 88) {
    fail("Math.abs for int value");
}

if (Math.abs(-2147483648) != 2147483648) {
    fail("Math.abs failed for long value");
}

if (Math.acos(1.0) != 0) {
    fail("Math.acos failed on double value");
}

if (Math.asin(0.0) != 0) {
    fail("Math.asin failed on double value");
}

if (Math.atan(0.0) != 0) {
    fail("Math.atan failed on double value");
}

if (Math.ceil(1) != 1) {
    fail("Math.ceil failed on int value");
}

if (Math.ceil(2147483648) != 2147483648) {
    fail("Math.ceil failed on long value");
}

if (Math.ceil(-0.3) != 0) {
    fail("Math.ceil failed on double value");
}

if (Math.floor(1) != 1) {
    fail("Math.floor failed on int value");
}

if (Math.floor(2147483648) != 2147483648) {
    fail("Math.floor failed on long value");
}

if (Math.floor(0.3) != 0) {
    fail("Math.floor failed on double value");
}

if (Math.log(1.0) != 0) {
    fail("Math.log failed on double value");
}

if (Math.max(2, 28) != 28) {
    fail("Math.max failed for int values");
}

if (Math.max(2147483649, 2147483648) != 2147483649) {
    fail("Math.max failed for long values");
}

if (Math.max(0.0, -2.5) != 0.0) {
    fail("Math.max failed for double values");
}

if (Math.min(2, 28) != 2) {
    fail("Math.min failed for int values");
}

if (Math.min(2147483649, 2147483648) != 2147483648) {
    fail("Math.min failed for long values");
}

if (Math.min(0.0, 2.5) != 0.0) {
    fail("Math.min failed for double values");
}

if (Math.sqrt(4) != 2) {
    fail("Math.sqrt failed for int value");
}

if (Math.tan(0.0) != 0.0) {
    fail("Math.tan failed for double value");
}
