/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8042364: Make __proto__ ES6 draft compliant
 *
 * @test
 * @run
 */

// check for Object.prototype.__proto__ accessor property
print("Object.prototype has __proto__?",
    Object.prototype.hasOwnProperty("__proto__"))

var desc = Object.getOwnPropertyDescriptor(Object.prototype, "__proto__")
print("descriptor");
print(JSON.stringify(desc))
print("getter", desc.get)
print("setter", desc.set)

// no computed "__proto__" name, only identifier!
var p = {}
var obj = {
    "__proto__" : p
}

if (Object.getPrototypeOf(obj) === p) {
    fail("obj has wrong __proto__, allows computed __proto__!")
}

if (obj.__proto__ !== p) {
    fail("__proto__ not created as normal property!")
}

if (Object.getPrototypeOf(obj) !== Object.prototype) {
    fail("obj has wrong __proto__")
}

var obj2 = {
    __proto__: p
}

if (Object.getPrototypeOf(obj2) !== p) {
    fail("can't set __proto__ in object literal")
}
