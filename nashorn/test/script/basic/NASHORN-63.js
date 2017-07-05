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
 * NASHORN-63 :  equality and strict equality implementations are broken.
 *
 * @test
 * @run
 */

function error(msg) {
    print("Error: " + msg);
}

if ((new Boolean(true) != new Boolean(true)) !== true) {
    error('Different Boolean objects are equal');
}

if ((new Boolean(false) != new Boolean(false)) !== true) {
    error('Different Boolean objects are equal');
}

if ((new Boolean(true) != new Boolean(false)) !== true) {
    error('Different Boolean objects are equal');
}

if ((new Number(3.14) != new Number(3.14)) !== true) {
    error('Different Number objects are equal');
}

if ((new Number(2.718) != new Number(3.14)) !== true) {
    error('Different Number objects are equal');
}

if ((new String("nashorn") != new String("nashorn")) !== true) {
    error('Different String objects are equal');
}

if ((new String("ecmascript") != new String("nashorn")) !== true) {
    error('Different String objects are equal');
}

if ((new Object() != new Object()) !== true) {
    error('Different Objects are equal');
}

var obj1 = {};
var obj2 = obj1;
if (obj1 != obj2) {
    error(' Same object literals are not equal');
}

if ((new Boolean(0) != new Number(0)) !== true) {
    error('a Boolean and a Number object are equal');
}

if ((new Number(42) != new String("42")) !== true) {
    error('a Number and a String object are equal');
}

if ((new String("1") != new Boolean(true)) !== true) {
    error('a String and a Boolean object are equal');
}

// strict equality checks
if ((new String("abc")) === "abc") {
    error('a String and a primitive string are strict equal');
}

if ((new Number(42)) === 42) {
    error('a Number and a primitive number are strict equal');
}

if ((new Boolean(true)) === true) {
    error('a Boolean and a primitive boolean are strict equal');
}

if ((new Boolean(false)) === false) {
    error('a Boolean and a primitive boolean are strict equal');
}

if ((new Object()) === (new Object())) {
    error('two different Objects are strict strict equal');
}

if ({} === {}) {
    error('two different Objects are strict strict equal');
}
