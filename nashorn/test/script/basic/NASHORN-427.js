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
 * NASHORN-427 : Object.seal and Object.freeze do not work for array objects as expected
 *
 * @test
 * @run
 */

function checkSeal(arr) {
    'use strict';

    try {
        delete arr[0];
        fail("#1 can delete sealed array element");
    } catch (e) {
        if (! (e instanceof TypeError)) {
            fail("#2 TypeError expected, got " + e);
        }
    }

    try {
        arr[arr.length] = 334;
        fail("#3 can extend sealed array");
    } catch (e) {
        if (! (e instanceof TypeError)) {
            fail("#4 TypeError expected, got " + e);
        }
    }
}

function checkFreeze(arr) {
    'use strict';
    checkSeal(arr);

    try {
        arr[arr.length - 1] = 34;
        fail("#5 can assign to frozen array element");
    } catch (e) {
        if (! (e instanceof TypeError)) {
            fail("#6 TypeError expected, got " + e);
        }
    }
}


var arr = [ 24 ];
Object.seal(arr);
checkSeal(arr);

var arr2 = [ 42 ];
Object.freeze(arr2);
checkFreeze(arr2);

