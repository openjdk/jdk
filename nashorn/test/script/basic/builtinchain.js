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
 * Check important prototype chain properties of Function and Object.
 *
 * @test
 * @run
 */

print(typeof(Object) == 'function');
print(typeof(Object.prototype) == 'object');
print(typeof(Function) == 'function');
// Function's prototype is a function!
print(typeof(Function.prototype) == 'function');

print(Function.constructor === Function);
print(Function.prototype === Object.getPrototypeOf(Function));
print(Object.getPrototypeOf(Function.prototype) === Object.prototype);

print(Object.constructor === Function);
print(Object.getPrototypeOf(Object.prototype) === null);
print(Object.getPrototypeOf(Object) === Function.prototype);

// check one function from Function.prototype
var applyFunc = Function.prototype.apply;
print(applyFunc.constructor === Function);
print(Object.getPrototypeOf(applyFunc) === Function.prototype);
print(applyFunc.prototype === undefined);

// check one function from Object.prototype
var toStringFunc = Object.prototype.toString;
print(toStringFunc.constructor === Function);
print(Object.getPrototypeOf(toStringFunc) === Function.prototype);
print(toStringFunc.prototype === undefined);
