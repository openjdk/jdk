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
 * range analysis test. check that computation return values are correct
 * both with and without range analysis
 *
 * @subtest
 */

function f(c) {
    var v = c & 0xffff;
    var w = v & 0xfff;
    var x = v * w;
    return x;
}

function g() {
    var sum = 0;
    for (var x = 0; x < 4711; x++) {
	sum += x;
    }
    return sum;
}

function g2() {
    var sum = 0;
    //make sure we overflow
    var displacement = 0x7ffffffe;
    for (var x = displacement; x < (displacement + 2); x++) {
	sum += x;
    }
    return sum;
}

//mostly provide code coverage for all the range operations    
function h() {
    var sum = 0;
    sum += 4711;
    sum &= 0xffff;
    sum /= 2;
    sum *= 2;
    sum -= 4;
    sum |= 2;
    sum ^= 17;
    sum = sum % 10000;
    sum = -sum;
    return sum
}

print(f(17));
print(g());
print(g2());
print(h());
