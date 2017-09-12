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
 * NASHORN-269 : Global constructor inlining breaks when a builtin constructor is deleted
 *
 * @test
 * @run
 */

delete Array;

try {
    print(new Array(3));
    fail("#1 should have thrown ReferenceError");
} catch (e) {
    if (! (e instanceof ReferenceError)) {
        fail("#2 expected ReferenceError, got " + e);
    }
}

Object.defineProperty(this, "Array", {
    get: function() { return 63; }
});

try {
    print(new Array(2));
    fail("#3 should have thrown TypeError");
} catch (e) {
    if (! (e instanceof TypeError)) {
        fail("#4 expected TypeError, got " + e);
    }
}

Object.defineProperty(this, "RegExp", {
    get: function() { return 23; }
});


try {
    print(new RegExp("abc"));
    fail("#5 should have thrown TypeError");
} catch (e) {
    if (! (e instanceof TypeError)) {
        fail("#6 expected TypeError, got " + e);
    }
}
