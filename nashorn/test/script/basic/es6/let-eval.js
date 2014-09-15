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
 * JDK-8051889: Implement block scoping in symbol assignment and scope computation
 *
 * @test
 * @run
 * @option --language=es6 */

"use strict";

function f() {
    var a;
    let b;
    const c = 0;

    print(a, b, c);

    try {
        eval("x = 1; print('x: ' + x);");
        print("assignment to x succeeded");
    } catch (e) {
        print(e);
    }
    try {
        eval("'use strict'; let z = 1; print('z: ' + z);");
        print("assignment to z succeeded");
        eval("print('z: ' + z);");
    } catch (e) {
        print(e);
    }

    try {
        eval("a = 1; print(a);");
        print("assignment to a succeeded");
    } catch (e) {
        print(e);
    }
    print("a: " + a);

    try {
        eval("b = 1; print('b: ' + b);");
        print("assignment to b succeeded");
    } catch (e) {
        print(e);
    }
    print("b: " + b);

    try {
        eval("c = 1; print('c: ' + c);");
        print("assignment to c succeeded");
    } catch (e) {
        print(e);
    }
    print("c: " + c);

    eval("a = 2; let b = 3;");

    try {
        print(a, b, c);
    } catch (e) {
        print(e);
    }

    let x;

    try {
        print(a, b, c, x);
    } catch (e) {
        print(e);
    }

}

f();

print(typeof a, typeof b, typeof c, typeof x, typeof z);
