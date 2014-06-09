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
 * NASHORN-459 : Cannot delete configurable accessor properties of global object
 *
 * @test
 * @run
 */

var global = this;
Object.defineProperty(global, "0", {
    get: function() { return 43; },
    set: function(x) { print('in setter ' + x); },
    configurable: true
});

if ((delete global[0]) !== true) {
    fail("can not delete configurable property '0' of global");
}

Object.defineProperty(global, "foo", {
    get: function() { return 33; },
    set: function(x) { print('setter foo'); },
    configurable: true
});

if ((delete global.foo) !== true) {
    fail("can not delete configurable property 'foo' of global");
}

Object.defineProperty(global, "1", {
    get: function() { return 1 },
    set: undefined,
    configurable: true
});

Object.defineProperty(global, "1", {
    get: function() { return 11 },
    set: undefined
});

if ((delete global[1]) !== true) {
    fail("can not delete configurable property '1' of global");
}


