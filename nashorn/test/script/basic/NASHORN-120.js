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
 * NASHORN-120 :  String.prototype.split does not convert the input arguments in the order specified
 *
 * @test
 * @run
 */

var obj1 = {
    toString: function() {
        throw "inside obj1.toString";
    }
};

var obj2 = {
    valueOf: function() {
        throw "inside obj2.valueOf";
    }
};

try {
    "hello".split(obj1, obj2);
    fail('#1: split not throwing exception on input conversion');
} catch (e) {
    if (e !== "inside obj2.valueOf") {
        fail('#2: Exception === "obj2.valueOf" got: '+e);
    }
}

try {
    "hello".split(obj1, 10);
    fail('#3: split not throwing exception on input conversion');
} catch (e) {
    if (e !== "inside obj1.toString") {
        fail('#4: Exception === "obj1.toString" got: '+e);
    }
}
