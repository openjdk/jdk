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
 * NASHORN-205 :  Cannot convert read-only accessor property into a data property with a new value
 *
 * @test
 * @run
 */

var obj = {};

Object.defineProperty(obj, "foo", {
    get: function() { return 23; },
    set: undefined,
    enumerable: true,
    configurable: true
});

var desc = Object.getOwnPropertyDescriptor(obj, "foo");
if (! desc.hasOwnProperty("get")) {
    fail("'get' missing!!");
}

if (obj.foo !== 23) {
    fail("obj.foo !== 23");
}

try {
    // modify to use "value" property
    Object.defineProperty(obj, "foo", {
        value: 100
    });
} catch (e) {
    fail("failed", e);
}

var newDesc = Object.getOwnPropertyDescriptor(obj, "foo");
if (! newDesc.hasOwnProperty("value")) {
    fail("'value' missing!!");
}

if (obj.foo !== 100) {
    fail("obj.foo !== 100");
}
