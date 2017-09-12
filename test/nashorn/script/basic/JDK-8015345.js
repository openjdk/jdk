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
 * JDK-8015345: Function("}),print('test'),({") should throw SyntaxError
 *
 * @test
 * @run
 */

function checkFunction(code) {
    try {
        Function(code);
        fail("should have thrown SyntaxError for :" + code);
    } catch (e) {
        if (! (e instanceof SyntaxError)) {
            fail("SyntaxError expected, but got " + e);
        }
        print(e);
    }
}

// invalid body
checkFunction("}),print('test'),({");

// invalid param list
checkFunction("x**y", "print('x')");

// invalid param identifier
checkFunction("in", "print('hello')");
//checkFunction("<>", "print('hello')")

// invalid param list and body
checkFunction("x--y", ")");

// check few valid cases as well
var f = Function("x", "return x*x");
print(f(10))

f = Function("x", "y", "return x+y");
print(f(33, 22));

f = Function("x,y", "return x/y");
print(f(24, 2));
