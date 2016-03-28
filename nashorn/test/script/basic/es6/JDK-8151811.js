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
 * JDK-8151811: Const declarations do not work in for..in loops
 *
 * @test
 * @run
 * @option --language=es6
 */

let array = ["a", "b", "c"];
let count = 0;

for (const i in array) {
    try {
        eval("i = 5");
        fail("const assignment should have thrown")
    } catch (e) {
        Assert.assertTrue(e instanceof TypeError);
    }
    Assert.assertTrue(i == count++);
}

let funcs = [];

for (const i in array) {
    try {
        eval("i = 5");
        fail("const assignment should have thrown")
    } catch (e) {
        Assert.assertTrue(e instanceof TypeError);
    }
    for (const j in array) {
        for (const k in array) {
            funcs.push(function () {
                return array[i] + array[j] + array[k];
            });
        }
    }
}

Assert.assertEquals(funcs.length, 3 * 3 * 3);
count = 0;

for (let i = 0; i < 3; i++) {
    for (let j = 0; j < 3; j++) {
        for (let k = 0; k < 3; k++) {
            Assert.assertEquals(funcs[count++](), array[i] + array[j] + array[k]);
        }
    }
}
