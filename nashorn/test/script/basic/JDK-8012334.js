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
 * JDK-8012334: ToUint32, ToInt32, and ToUint16 don't conform to spec
 *
 * @test
 * @run
 */


function test(val) {
    print(val | 0);
    print(val >> 0);
    print(val >>> 0);
    print(1 >>> val);
    print(parseInt("10", val));
}

test(0);
test(-0);
test('Infinity');
test('+Infinity');
test('-Infinity');
test(Number.POSITIVE_INFINITY);
test(Number.NEGATIVE_INFINITY);
test(Number.NaN);
test(Number.MIN_VALUE);
test(-Number.MIN_VALUE);
test(1);
test(-1);
test(0.1);
test(-0.1);
test(1.1);
test(-1.1);
test(9223372036854775807);
test(-9223372036854775808);
test('9223372036854775807');
test('-9223372036854775808');
test(2147483647);
test(2147483648);
test(2147483649);
test(-2147483647);
test(-2147483648);
test(-2147483649);
test(4294967295);
test(4294967296);
test(4294967297);
test(-4294967295);
test(-4294967296);
test(-4294967297);
test(1e23);
test(-1e23);
test(1e24);
test(-1e24);
test(1e25);
test(-1e25);
test(1e26);
test(-1e26);

