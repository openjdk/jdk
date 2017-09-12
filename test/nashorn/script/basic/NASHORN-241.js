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
 * NASHORN-241 : regression: "new 1" does not throw TypeError
 *
 * @test
 * @run
 */

try {
    new 1;
    fail('new "1" should have thrown TypeError');
} catch (e) {
    if (! (e instanceof TypeError)) {
        fail('expected TypeError, got ' + e, e);
    }
}

try {
    var x = "1";
    new x;
    fail('var x = "1"; new x should have thrown TypeError');
} catch (e) {
    if (! (e instanceof TypeError)) {
        fail('var x = "1"; new x - expected TypeError, got ' + e, e);
    }
}

try {
    var x = "1";
    new x();
    fail('var x = "1"; new x() should have thrown TypeError');
} catch (e) {
    if (! (e instanceof TypeError)) {
        fail('var x = "1"; new x() - expected TypeError, got ' + e);
    }
}

