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
 * NASHORN-12 :  Number does not have methods toFixed, toPrecision, and toExponential.
 *
 * @test
 * @run
 */

// checks for Number.prototype.toFixed

if (NaN.toFixed() !== "NaN") {
    fail("#1 NaN.toFixed() is not NaN");
}

if (new Number(1e21).toFixed(12) !== String(1e21)) {
    fail("#2 new Number(1e21).toFixed(12) is not String(1e21)");
}

if (new Number(1.2).toFixed(3) !== "1.200") {
    fail("#3 new Number(1.2).toFixed(3) is not '1.200'");
}

if (new Number(1.2542).toFixed(3) !== "1.254") {
    fail("#4 Number(1.2542).toFixed(3) is not '1.254'");
}

try {
    453.334.toFixed(31);
    fail("#5 toFixed(31) should have thrown RangeError");
} catch (e) {
    if (! (e instanceof RangeError)) {
        fail("#6 toFixed(31) should throw RangeError, got " + e);
    }
}

try {
    3.14.toFixed(-1);
    fail("#7 toFixed(-1) should have thrown RangeError");
} catch (e) {
    if (! (e instanceof RangeError)) {
        fail("#8 toFixed(-1) should throw RangeError, got " + e);
    }
}


// checks for Number.prototype.toPrecision

var num = new Number(0.0);

try {
    num.toPrecision(0);
    fail("#9: num.toPrecision(0) should have been thrown RangeError");
} catch (e) {
    if (! (e instanceof RangeError)) {
        fail("#10: RangeError expected, got " + e);
    }
}

try {
    num.toPrecision(22);
    fail("#11: num.toPrecision(22) should have been thrown RangeError");
} catch (e) {
    if (! (e instanceof RangeError)) {
        fail("#12: RangeError expected, got " + e);
    }
}

num = new Number(23.4718);

if (num.toPrecision(1) != "2e+1") {
    fail("#13: toPrecision(1) gives " + num.toPrecision(1));
}

if (num.toPrecision(2) != "23") {
    fail("#14: toPrecision(2) gives " + num.toPrecision(2));
}

if (num.toPrecision(3) != "23.5") {
    fail("#15: toPrecision(3) gives " + num.toPrecision(3));
}

if (num.toPrecision(11) != "23.471800000") {
    fail("#16: toPrecision(11) gives " + num.toPrecision(11));
}

if (Infinity.toPrecision(1) != "Infinity") {
    fail("#17: Infinity.toPrecision(1) gives " + Infinity.toPrecision(1));
}

if (-Infinity.toPrecision(1) != "-Infinity") {
    fail("#18: -Infinity.toPrecision(1) gives " + -Infinity.toPrecision(1));
}

// checks for Number.prototype.toExponential

if (num.toExponential(1) != "2.3e+1") {
    fail("#20: toExponential(1) gives " + num.toExponential(1));
}

if (num.toExponential(2) != "2.35e+1") {
    fail("#21: toExponential(2) gives " + num.toExponential(2));
}

if (num.toExponential(3) != "2.347e+1") {
    fail("#22: toExponential(3) gives " + num.toExponential(3));
}

if (num.toExponential(11) != "2.34718000000e+1") {
    fail("#23: toExponential(11) gives " + num.toExponential(11));
}

if (Infinity.toExponential(1) != "Infinity") {
    fail("#24: Infinity.toExponential(1) gives " + Infinity.toExponential(1));
}

if (-Infinity.toExponential(1) != "-Infinity") {
    fail("#25: -Infinity.toExponential(1) gives " + -Infinity.toExponential(1));
}

if (NaN.toExponential(1) != "NaN") {
    fail("#26: NaN.toExponential(1) gives " + NaN.toExponential(1));
}

