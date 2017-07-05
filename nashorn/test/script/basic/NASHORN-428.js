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
 * NASHORN-428 : Redefining a non-configurable property with the "same value" is permitted, but -0.0 and +0.0 are not "same value"
 *
 * @test
 * @run
 */

var obj = {};

Object.defineProperty(obj, "0", {
    value: -0,
    configurable: false
});

try {
    Object.defineProperties(obj, {
        "0": { value: +0 }
    });
    fail("#1 expected TypeError");
} catch (e) {
    if (! (e instanceof TypeError)) {
        fail("#2 expected TypeError, got " + e);
    }
}

// -0 should be fine.. should not throw any TypeError
Object.defineProperties(obj, {
    "0": { value: -0 }
});

