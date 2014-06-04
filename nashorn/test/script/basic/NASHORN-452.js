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
 * NASHORN-452 : nashorn is able to redefine a global declared variable
 *
 * @test
 * @run
 */

var env = {};
try {
    Object.defineProperty(this, 'env', {
        get: function() {}
    });
    fail("#1 should have thrown TypeError");
} catch (e) {
    if (! (e instanceof TypeError)) {
        fail("#2 TypeError expected, got " + e);
    }
}

var desc = Object.getOwnPropertyDescriptor(this, 'env');
if (desc.configurable) {
    fail("#3 global var 'env' is configurable");
}

(function (x) {
    if (delete x) {
        fail("#4 can delete function argument");
    }
})(3);

(function (x) {
    // use of 'with'
    with ({ foo: 2 }) {
        if (delete x) {
            fail("#5 can delete function argument");
        }
    }
})(4);

(function (x, y) {
    // use of 'arguments'
    var arg = arguments;
    if (delete x || delete y) {
        fail("#6 can delete function argument");
    }
})(5);

(function (x) {
    // use of 'eval'
    eval("x");

    if (delete x) {
        fail("#7 can delete function argument");
    }
})('hello');

