/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8079145: jdk.nashorn.internal.runtime.arrays.IntArrayData.convert assertion
 *
 * @test
 * @fork
 * @option -Dnashorn.debug=true
 * @run
 */

var Byte = java.lang.Byte;
var Short = java.lang.Short;
var Integer = java.lang.Integer;
var Long = java.lang.Long;
var Float = java.lang.Float;
var Double = java.lang.Double;
var Character = java.lang.Character;

function checkWiden(arr, value, name) {
    switch (typeof value) {
    case 'object':
    case 'undefined':
        print(name + ": check widen for " + value);
        break;
    default:
        print(name + ": check widen for " + value + 
            " [" + Debug.getClass(value) + "]");
    }

    arr[0] = value;
}

function checkIntWiden(value) {
   checkWiden([34], value, "int array");
}

function checkLongWiden(value) {
    checkWiden([Integer.MAX_VALUE + 1], value, "long array");
}

function checkNumberWiden(value) {
    checkWiden([Math.PI], value, "number array");
}

function checkObjectWiden(value) {
    checkWiden([null], value, "object array");
}

var values = [{}, null, undefined, false, true, new Byte(34),
   new Integer(344454), new Long(454545), new Long(Integer.MAX_VALUE + 1),
   new Float(34.3), new Double(Math.PI), new Character('s')];

for each (var v in values) {
    checkIntWiden(v);
}

for each (var v in values) {
    checkLongWiden(v);
}

for each (var v in values) {
    checkNumberWiden(v);
}

for each (var v in values) {
    checkObjectWiden(v);
}
