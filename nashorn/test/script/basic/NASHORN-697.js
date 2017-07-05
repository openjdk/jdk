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
 * NASHORN-697 : ScriptFunction should differentiate between "strict" and "builtin"
 *
 * @test
 * @run
 */

// make sure 'this' transformation is not done for built-ins
var toString = Object.prototype.toString;

if (toString() !== "[object Undefined]") {
    fail("toString() !== [object Undefined]");
}

if (toString.call(null) !== "[object Null]") {
    fail("toString.call(null) !== [object Null]");
}


// make sure builtin functions are not strict! For example,
// trying to access arguments and caller should not result in TypeError
try {
    if (toString.arguments) {
        fail("toString.arguments is defined!");
    }
} catch (e) {
    fail("got " + e, e);
}

try {
    if (toString.caller) {
        fail("toString.caller is defined!");
    }
} catch (e) {
    fail("got " + e, e);
}
