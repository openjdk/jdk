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
 * NASHORN-433 : Array iteration for methods like Array.prototype.forEach should compute length before checking whether the callback is a function
 *
 * @test
 * @run
 */

var obj = { "0": 33, "1": 2 };
Object.defineProperty(obj, "length", {
    get: function() { throw new ReferenceError(); }
});

try {
    Array.prototype.forEach.call(obj, undefined);
    fail("should have thrown error");
} catch (e) {
    // length should be obtained before checking if callback
    // is really a function or not. So, we should get error
    // from length getter rather than TypeError for callback.
    if (! (e instanceof ReferenceError)) {
        fail("ReferenceError expected, got " + e);
    }
}

