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
 * @test
 * @bug 8036987, 8037572
 * @summary Implement tests that checks static types in the compiled code
 * @option --optimistic-types=true
 * @run
 */

var inspect = Java.type("jdk.nashorn.test.tools.StaticTypeInspector").inspect
var a = 3, b, c;
var x = { a: 2, b:1, c: 7, d: -1}
var y = { a: Number.MAX_VALUE, b: Number.POSITIVE_INFINITY, c: "Hello", d: undefined}

// Testing arithmetic operators
//-- Local variable
print(inspect(x.a*x.b, "local int multiplication by local int"))
print(inspect(x.a/x.b, "local int division by local int without remainder"))
print(inspect(x.c/x.a, "local int division by local int with remainder"))
print(inspect(x.c%x.a, "local int modulo by local int"))
print(inspect(x.a+x.b, "local int addition local int"))
print(inspect(x.a-x.b, "local int substraction local int"))
print(inspect(y.a*y.a, "max value multiplication by max value"))
print(inspect(y.b*y.b, "infinity multiplication by infinity"))
print(inspect(x.d/y.b, "-1 division by infinity"))
print(inspect(y.b/x.e, "infinity division by zero"))
print(inspect(y.b/y.c, "infinity division by String"))
print(inspect(y.d/y.d, "local undefined division by local undefined"))
print(inspect(y.d*y.d, "local undefined multiplication by local undefined"))
print(inspect(y.d+x.a, "local undefined addition local int"))
print(inspect(y.d--, "local undefined decrement postfix"))
print(inspect(--y.d, "local undefined decrement prefix"))
print(inspect(x.a++, "local int increment postfix"))
print(inspect(++x.a, "local int increment prefix"))
print(inspect(x.a--, "local int decrement postfix"))
print(inspect(--x.a, "local int decrement prefix"))
print(inspect(+x.a, "local int unary plus"))
print(inspect(-x.a, "local int unary minus"))
//-- Global variable
print(inspect(b*c, "undefined multiplication by undefined"))
print(inspect(b/c, "undefined division by undefined"))
print(inspect(a+a, "global int addition global int"))
print(inspect(a-a, "global int substraction global int"))
print(inspect(a*a, "global int multiplication by global int"))
print(inspect(a++, "global int increment postfix"))
print(inspect(++a, "global int increment prefix"))
print(inspect(a--, "global int decrement postfix"))
print(inspect(--a, "global int decrement prefix"))
print(inspect(+a, "global int unary plus"))
print(inspect(-a, "global int unary minus"))
print(inspect(b--, "global undefined decrement postfix"))
print(inspect(--b, "global undefined decrement prefix"))
print(inspect(x, "object"))
print(inspect(x/x, "object division by object"))
print(inspect(x/y, "object division by object"))
