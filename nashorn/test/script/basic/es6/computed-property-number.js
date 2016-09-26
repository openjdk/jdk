/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8164467: ES6 computed properties are implemented wrongly
 *
 * @test
 * @run
 * @option --language=es6
 */

function f(x) {
    return x;
}

var object = {
    [1.5]: 'a',
    get [f(1e+40)]() { return 'b' },
    [0.0001]: 'c',
    [-0]: 'd',
    [Infinity]: 'e',
    [-Infinity]: 'f',
    [NaN]: 'g'

};


Assert.assertEquals(object['1.5'], 'a');
Assert.assertEquals(object['1e+40'], 'b');
Assert.assertEquals(object['0.0001'], 'c');
Assert.assertEquals(object[0], 'd');
Assert.assertEquals(object[Infinity], 'e');
Assert.assertEquals(object[-Infinity], 'f');
Assert.assertEquals(object[NaN], 'g');
