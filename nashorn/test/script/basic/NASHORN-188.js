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


/*
 * NASHORN-188 : Nashorn uses locale for Number.prototype.toPrecision()
 *
 * @test
 * @run
 */

function format(value) {
    print(value.toFixed());
    print(value.toFixed(1));
    print(value.toFixed(2));
    print(value.toFixed(3));
    print(value.toFixed(10));

    print(value.toExponential());
    print(value.toExponential(1));
    print(value.toExponential(2));
    print(value.toExponential(3));
    print(value.toExponential(10));

    print(value.toPrecision());
    print(value.toPrecision(1));
    print(value.toPrecision(2));
    print(value.toPrecision(3));
    print(value.toPrecision(10));

    print();
}

format(0);
format(1/10);
format(1/12);
format(1);
format(123456789000);
format(123456.123456);
format(-1/10);
format(-1/12);
format(-1);
format(-123456789000);
format(-123456.123456);
format(NaN);
format(Infinity);
