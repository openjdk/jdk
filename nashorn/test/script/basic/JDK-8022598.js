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
 * JDK-8022598: Object.getPrototypeOf should return null for host objects rather than throwing TypeError
 *
 * @test
 * @run
 */

// the following should not throw TypeError, just return null instead

var proto = Object.getPrototypeOf(new java.lang.Object());
if (proto !== null) {
    fail("Expected 'null' __proto__ for host objects");
}

// on primitive should result in TypeError

function checkTypeError(obj) {
    try {
        Object.getPrototypeOf(obj);
        fail("Expected TypeError for Object.getPrototypeOf on " + obj);
    } catch (e) {
        if (! (e instanceof TypeError)) {
            fail("Expected TypeError, but got " + e);
        }
    }
}

checkTypeError(undefined);
checkTypeError(null);
checkTypeError(3.1415);
checkTypeError("hello");
checkTypeError(false);
checkTypeError(true);
