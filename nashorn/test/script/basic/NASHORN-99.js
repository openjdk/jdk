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
 * NASHORN-99 :  Passing less number of arguments to a native varargs function results in IllegalArgumentException.
 *
 * @test
 * @run
 */

function func() {
}

try {
    func.call();
} catch (e) {
    fail("func.call() failed", e);
}

var obj = Object.create(Function.prototype);

if (typeof obj.call !== "function") {
    fail('#1: call method not found!');
}

try {
    obj.call();
    fail('#2: No [[Call]] property, a TypeError expected');
} catch (e) {
    if (!(e instanceof TypeError)) {
        fail('#3: TypeError expected but got ' + e);
    }
}
