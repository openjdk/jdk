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
 * NASHORN-288 : Guard setup in ScriptObject.findGetMethod is wrong for inherited properties
 *
 * @test
 * @run
 */

var obj1 = {};
obj1.foo = function() {
}

var obj2 = Object.create(obj1);

// inside function to force same callsite
function func(o) {
  o.foo();
}

func(obj2);

// change proto's property that is called
obj1.foo = 33;

// try again. should get TypeError as 33 is called
// as function!
try {
    func(obj2);
    fail("should have thrown TypeError");
} catch (e) {
    if (! (e instanceof TypeError)) {
        fail("should have thrown TypeError");
    }
}
