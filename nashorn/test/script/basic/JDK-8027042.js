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
 * JDK-8027042: Evaluation order for binary operators can be improved
 *
 * @test
 * @run
 */

// var with getter side effect
Object.defineProperty(this, "a", { get: function() {print("get a"); return 1; }});

// var with both getter and conversion side effect
Object.defineProperty(this, "b", { get: function() {print("get b"); return {valueOf: function() { print("conv b"); return 10; }}; }});

(function() {
    // var with toPrimitive conversion side effect
    var c = {valueOf: function() { print("conv c"); return 100; }};

    print(b + (c + a));
    print(b + (c + b));
    print(b + (a + b));
    print(b + (b + c));
    print(b + (b + c));
    print(b + (c + (a - b)));
    print(b + (c + (c - b)));
    print(b + (c + (b - c)));
    print(b + (b + (a ? 2 : 3)));
    print(b + (b + (b ? 2 : 3)));
    print(b + (b + (c ? 2 : 3)));
    print(b + ((-c) + (-a)));
    print(b + ((-c) + (-b)));
    print(b + ((-c) + (-c)));
    try { print(b + new a); } catch (e) {}
    try { print(b + new b); } catch (e) {}
    try { print(b + new c); } catch (e) {}
})();
