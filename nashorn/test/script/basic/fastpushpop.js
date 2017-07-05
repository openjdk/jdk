/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * fastpushpop.js: make sure guards work for fast push implementation
 * and normal one
 *
 * @test
 * @run
 */

var a = [1,2,3];
a.push(4);
a.push(5);
a.push(6);
print(a);

var a2 = Object.defineProperty(a,"length", { writable: false });
try { 
    a2.push(7);
} catch (e) {
    print("first: " + (e instanceof TypeError));
}

print(a2);

var b = [1,2,3,,,,4711.17,"dingo!"];
b.push(4);
b.push(5);
b.push(6);
print(b);

var b2 = Object.defineProperty(b,"length", { writable: false });
try { 
    b2.push(7);
} catch (e) {
    print("second: " + (e instanceof TypeError));
}

print(b2);

